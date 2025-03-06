package herd.core.betizu

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

import herd.common.mem.milk._

  // Enum pour les types d'opérations
  object OpType {
    val READ  = 0.U
    val WRITE = 1.U
  }

  // États de la FSM MSI avec représentation sur 2 bits
  object State_MSI extends ChiselEnum {
    val s_invalid  = UInt(2.W)  // Invalid, 00
    val s_shared   = UInt(2.W)  // Shared, 01
    val s_modified = UInt(2.W)  // Modified, 10

    // Assignation explicite des valeurs sur 2 bits
    s_invalid   := "b00".U
    s_shared    := "b01".U
    s_modified  := "b10".U
  }

  // Directory-Based Cache Controller
  class DirectoryController(p: MilkParams, cacheSize: Int, lineSize: Int) extends Module {
    val io = IO(new Bundle {
      val reqs       = Flipped(Vec(p.nHart, new MilkReqIO(p)))                // Requêtes caches
      // val resp       = Vec(p.nHart, new MilkReqIO(p))                      // Réponses vers caches
      val invalidate = Vec(p.nHart * cacheSize / lineSize, Bool())            // Signaux d'invalidation
    })

  val addrWidth = p.nAddrBit
  val dataWidth = p.nDataBit

  // Paramètres mémoire/cache
  val numSets     = cacheSize / lineSize                    // Nombre de blocs dans le cache
  val indexWidth  = log2Ceil(numSets)                       // Bits nécessaires pour l'index
  val offsetWidth = log2Ceil(lineSize / (dataWidth/8))      // Bits pour l'offset
  val nblock      = p.nHart * 2                             // Nombre de caches

  // Annuaire pour la cohérence MSI :
  // - Vecteur de bits de présence (nblock) 

  // Annuaire pour la cohérence MSI :
  class Directory_class extends Bundle {
    val presence = Vec(nblock, Bool()) // Vecteur de bits de présence
    val state    = UInt(2.W)           // 2 bits pour l'état MSI
  }

  // Initialisation de directory avec les valeurs appropriées
  val directory = RegInit(VecInit(Seq.fill(numSets)({
    val dir = Wire(new Directory_class)
    dir.presence := VecInit(Seq.fill(nblock)(false.B)) // Initialisation à false pour la présence
    dir.state := State_MSI.s_invalid // Initialisation de l'état à "Invalid"
    dir
  })))

  // Arbitre Round-Robin pour gérer les accès concurrents
  val arbiter = Module(new RRArbiter(new MilkReqIO(p), p.nHart))

  for (i <- 0 until p.nHart) {
    arbiter.io.in(i) <> io.reqs(i).ctrl
  }

  // Requête sélectionnée
  val selectedReq   = arbiter.io.out.bits
  val selectedValid = arbiter.io.out.valid
  val selectedAddr  = selectedReq.ctrl.addr
  val selectedHart  = selectedReq.hart

  // Extraction de l'index à partir de l'adresse mémoire
  val w_index    = selectedAddr(indexWidth + offsetWidth - 1 + 2, offsetWidth + 2)
  val r_state    = directory(w_index).state
  val r_presence = directory(w_index).presence

  // Logique de cohérence MSI
  when(selectedValid) {
    switch(r_state) {
      is(State_MSI.s_invalid) {
        when(selectedReq.ctrl.op === OpType.READ) {
          r_presence(selectedHart) := true.B
          r_state := State_MSI.s_shared
        }.elsewhen(selectedReq.ctrl.op === OpType.WRITE) {
          r_presence(selectedHart) := true.B
          r_state := State_MSI.s_modified
        }
      }
      is(State_MSI.s_shared) {
        when(selectedReq.ctrl.op === OpType.WRITE) {
          for (i <- 0 until p.nHart) {
            when(i.U =/= selectedHart) { r_presence(i) := false.B }
          }
          r_presence(selectedHart) := true.B
          r_state := State_MSI.s_modified
        }
      }
      is(State_MSI.s_modified) {
        // Déjà modifié, pas d'action à faire
      }
    }
  }

  // Génération du signal d'invalidation
  for (i <- 0 until numSets) {
    for (j <- 0 until nblock) {
      io.invalidate(i * nblock + j) := (directory(i).state === State_MSI.s_invalid) || !directory(i).presence(j)
    }
  }

  // Réponses aux caches 
  for (i <- 0 until p.nHart) {
    io.reqs(i).ready := arbiter.io.out.ready && (arbiter.io.chosen === i.U)
  }
 
  // Le controleur invalid les caches et met à jour les états puis il dit au caches qu'il est prêt à traiter la demande du cpu. 

}

// Génération du Verilog
object DirectoryController extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new DirectoryController(MilkConfig0, 512, 64),
    firtoolOpts = Array.concat(
      Array(
        "--disable-all-randomization",
        "--strip-debug-info",
        "--split-verilog"
      ),
      args
    )
  )
}
