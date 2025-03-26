package herd.draft.multi.control

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.bus._
import herd.common.core._
import herd.common.mem.milk._

import _root_.circt.stage.ChiselStage



 
 //class Counter(n: Int) extends Module {
 //  val io = IO(new Bundle {
 //    val enable = Input(Bool())   // Signal d'activation
 //    val count  = Output(UInt(log2Ceil(n).W)) // Valeur actuelle du compteur
 //  })
 //
 //  val reg = RegInit(0.U(log2Ceil(n).W)) // Registre pour stocker la valeur du compteur
 //
 //  when(io.enable) {
 //    reg := Mux(reg === (n - 1).U, 0.U, reg + 1.U) // Incrémentation avec retour à 0
 //  }
 //
 //  io.count := reg // Sortie du compteur
 //}

  // counter.io.enable := false.B

  // when(write_request && (counter.io.count === 0.U || stall_active)) {
  //   stall_active := true.B
  //   counter.io.enable := true.B
  // }.elsewhen(counter.io.count === 0.U && stall_active) {
  //   stall_active := false.B
  //   counter.io.enable := false.B
  // }

  // object State_stall extends ChiselEnum {
  //   val s_r, s_shared = Value
  // }


  // val stall_unit = Wire(Vec(nCache, Decoupled(new Ctrl_arbiter(p))))

  // val counter = Module(new Counter(cycle_stall))
  // val stall_active = RegInit(false.B) // Indique si le stall est actif


// Permet de stall toutes les requetes de lecture quand une écriture est réalisé:
// Future, utiliser les addrs pour ne pas stall les read à des addresses différents des endroits ou sonbt les write
// 

// Stall Unit Cache Controller
class stall_coherency(p: MilkParams, cacheSize: Int, lineSize: Int, nCache: Int) extends Module {
  val io = IO(new Bundle {
    val req_in    = Vec(nCache, Flipped(Decoupled(new Ctrl_arbiter(p))))
    val req_out   = Vec(nCache, Decoupled(new Ctrl_arbiter(p)))
  })

  io := DontCare


  def addrWidth = p.nAddrBit
  def numSets = nCache * cacheSize / lineSize // Nombre de blocs dans le cache
  def indexWidth = log2Ceil(numSets) // Bits nécessaires pour l'index
  def offset = lineSize / (addrWidth / 8) // Bits pour l'offset
  def offsetWidth = log2Ceil(lineSize / (addrWidth / 8)) // Bits pour l'offset
  def tagWidth = addrWidth - indexWidth - offsetWidth // Bits pour le tag
  def nblock = nCache // Nombre de caches

  def cycle_stall = 3 + offset // Nombre de cycles de stall (REQ_CONTROLLEUR + REQ_MEM + CYCLE_DATA_MEM_TO_CACHE + REP_CACHE)

  //////////////////////////// STALL UNIT ///////////////////////////

  // Détection d'une lecture en cours
  val read_request = io.req_in.map(req => req.valid && (req.bits.op === 0.U)).reduce(_ || _)

  // Détection d'une écriture en cours
  val write_request = io.req_in.map(req => req.valid && (req.bits.op === 1.U)).reduce(_ || _)

  // Signal de priorité pour les requets d'ecriture du premier coeur

  val w_valid = Wire(Vec(nCache, Bool()))

   when(write_request){
     w_valid(0) :=  io.req_in(0).valid && (write_request === io.req_in(0).bits.op)
     w_valid(1) :=  !w_valid(0) && io.req_in(1).valid && (write_request === io.req_in(1).bits.op)
     w_valid(2) :=  !w_valid(1) && !w_valid(0) && io.req_in(2).valid && (write_request === io.req_in(2).bits.op)
     w_valid(3) :=  !w_valid(2) && !w_valid(1) && !w_valid(0) && io.req_in(3).valid && (write_request === io.req_in(3).bits.op)
   }.otherwise {
     w_valid(0) :=  io.req_in(0).valid
     w_valid(1) :=  io.req_in(1).valid
     w_valid(2) :=  io.req_in(2).valid
     w_valid(3) :=  io.req_in(3).valid
   }

  // Write priority from cache 0 to cache N

 // when(write_request) {
 //   val temp_valid = Wire(Vec(nCache, Bool()))
 //   
 //   for (i <- 0 until nCache) {
 //     temp_valid(i) := io.req_in(i).valid && (write_request === io.req_in(i).bits.op)
 //   }
 //   
 //   w_valid(0) := temp_valid(0)
 //   for (i <- 1 until nCache) {
 //     w_valid(i) := temp_valid(i) && !(temp_valid.slice(0, i).reduce(_ || _)) // Write req valid only if not prevuois cache valid
 //   }
 // }.otherwise {
 //   for (i <- 0 until nCache) {
 //     w_valid(i) := io.req_in(i).valid
 //   }
 // }

  // Write priority from cache 0 to cache N if addr conflict
  //when(write_request) {
  //val temp_valid = Wire(Vec(nCache, Bool()))
  //val temp_addr  = Wire(Vec(nCache, UInt(p.nAddrBit.W)))
  //// Capture valid write requests and their addresses
  //  for (i <- 0 until nCache) {
  //    temp_valid(i) := io.req_in(i).valid && (io.req_in(i).bits.op === 1.U)
  //    temp_addr(i)  := io.req_in(i).bits.ctrl.addr
  //  }
//
  //  w_valid(0) := temp_valid(0)
//
  //  for (i <- 1 until nCache) {
  //    // Check if any previous valid write request has the same address
  //    val addr_conflict = temp_valid.slice(0, i).zip(temp_addr.slice(0, i))
  //      .map { case (valid, addr) => valid && (addr === temp_addr(i)) }
  //      .reduce(_ || _)
//
  //    w_valid(i) := temp_valid(i) && !addr_conflict
  //  }
  //}.otherwise {
  //  // Normal read or non-conflicting writes
  //  for (i <- 0 until nCache) {
  //    w_valid(i) := io.req_in(i).valid
  //  }
  //}



  // for (i <- 0 until nCache) {
  //   w_valid(i) := io.req_in(i).valid && (write_request === io.req_in(i).bits.op)
  //   for (j <- 0 until i) {
  //     w_valid(i) := w_valid(i) && !w_valid(j)
  //   }
  // }


  // for (i <- 1 until nCache) {
  //   w_valid(i) :=  !w_valid(i-1) && io.req_in(i).valid && (write_request === io.req_in(i).bits.op)
  // }

////////////////////////////////////////////////////////////////////////

  //when(write_request) {
  //  val temp_valid = Wire(Vec(nCache, Bool()))
  //  val temp_addr  = Wire(Vec(nCache, UInt(p.nAddrBit.W)))
//
  //  // Capture valid write requests and their addresses
  //  for (i <- 0 until nCache) {
  //    temp_valid(i) := io.req_in(i).valid && (io.req_in(i).bits.op === 1.U)
  //    temp_addr(i)  := io.req_in(i).bits.addr
  //  }
//
  //  w_valid(0) := temp_valid(0)
//
  //  for (i <- 1 until nCache) {
  //    // Check if any previous write has the same address
  //    val addr_conflict = temp_valid.slice(0, i).zip(temp_addr.slice(0, i))
  //      .map { case (valid, addr) => valid && (addr === temp_addr(i)) }
  //      .reduce(_ || _)
//
  //    w_valid(i) := temp_valid(i) && !addr_conflict
  //  }
  //}.otherwise {
  //  for (i <- 0 until nCache) {
  //    w_valid(i) := io.req_in(i).valid
  //  }
  //}
//
  //// Enable reads that do NOT conflict with an active write
  //val write_addrs = Wire(Vec(nCache, UInt(p.nAddrBit.W)))
  //val has_write   = Wire(Vec(nCache, Bool()))
//
  //for (i <- 0 until nCache) {
  //  has_write(i)   := io.req_in(i).valid && (io.req_in(i).bits.op === 1.U)
  //  write_addrs(i) := io.req_in(i).bits.addr
  //}
//
  //for (i <- 0 until nCache) {
  //  val read_valid = io.req_in(i).valid && (io.req_in(i).bits.op === 0.U)
//
  //  // Check if this read conflicts with any write
  //  val read_conflict = has_write.zip(write_addrs)
  //    .map { case (w_valid, w_addr) => w_valid && (w_addr === io.req_in(i).bits.addr) }
  //    .reduce(_ || _)
//
  //  io.req_out(i).valid := read_valid && !read_conflict
  //  io.req_out(i).bits  := io.req_in(i).bits
  //  io.req_in(i).ready  := true.B
  //}


////////////////////////////////////////////////////////////////////////

  // Gestion des requêtes
  for (i <- 0 until nCache) {
    io.req_out(i).valid := w_valid(i) //io.req_in(i).valid && (write_request === io.req_in(i).bits.op) 
    io.req_out(i).bits  := io.req_in(i).bits
    io.req_in(i).ready  := true.B 
  }
}

// Génération du Verilog
object stall_coherency extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new stall_coherency(MilkConfig0, 512, 16, 4),
    firtoolOpts = Array.concat(
      Array(
        "--disable-all-randomization",
        "--strip-debug-info",
        "--split-verilog",
        "-o", "verilogtest" 
      ),
      args
    )
  )
}
