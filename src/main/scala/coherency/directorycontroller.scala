package herd.draft.multi.control

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.bus._
import herd.common.core._
import herd.common.mem.milk._

import _root_.circt.stage.ChiselStage

  // Directory-Based Cache Controller
class DirectoryController(p : MilkParams, cacheSize: Int, lineSize: Int, nCache: Int) extends Module {
  val io = IO(new Bundle {
    val reqs        = Vec(nCache, Flipped(new Bus_control(p)))
    // val ready = Vec(nCache, Output(Bool()))
  })

  io := DontCare
  dontTouch(io)

  def addrWidth = p.nAddrBit

  // Paramètres mémoire/cache
  def numSets     = nCache * cacheSize / lineSize           // Nombre de blocs dans le cache
  def indexWidth  = log2Ceil(numSets)                       // Bits nécessaires pour l'index
  def offsetWidth = log2Ceil(lineSize / (addrWidth/8))      // Bits pour l'offset
  def tagWidth    = addrWidth - indexWidth - offsetWidth    // Bits pour le tag
  def nblock      = nCache                                  // Nombre de caches

  // Annuaire pour la cohérence MSI : Vecteur de bits de présence (nblock) 

  //val init_directory = Wire(Vec(numSets, new Directory_class(nblock)))
  //for (s <- 0 until numSets) {
  //  init_directory(s).presence := 0.U.asTypeOf(init_directory(s).presence)
  //  init_directory(s).state    := State_MSI.s_invalid
  //}
  //val r_directory = RegInit(init_directory)

  val init_directory = Wire(Vec(numSets, new Directory_class(nblock,tagWidth)))
  for (s <- 0 until numSets) {
    init_directory(s).presence := 0.U.asTypeOf(init_directory(s).presence)
    init_directory(s).state    := State_MSI.s_invalid
    init_directory(s).tag      := 0.U
  }

  val r_directory = RegInit(init_directory)

  //////////////////////////// STALL UNIT ///////////////////////////

  val stall_unit = Module(new stall_coherency(p,cacheSize, lineSize, nCache))

  // stall_unit.io.req_in  

  for (i <- 0 until nCache) {
    // io.reqs(i).req_control.ready      := stall_unit.io.req_in(i).ready
    stall_unit.io.req_in(i).valid     := io.reqs(i).req_control.valid
    stall_unit.io.req_in(i).bits.hart := io.reqs(i).req_control.hart
    stall_unit.io.req_in(i).bits.addr := io.reqs(i).req_control.ctrl.addr
    stall_unit.io.req_in(i).bits.op   := io.reqs(i).req_control.ctrl.op
  }

  /////////////////////////// ARBITER MSI //////////////////////////

  // Arbitre Round-Robin pour gérer les accès concurrents
  val arbiter = Module(new RRArbiter(Flipped(new Ctrl_arbiter(p)), nCache))

  arbiter.io.out.ready := true.B

  for (i <- 0 until nCache) {
      arbiter.io.in(i) <>   stall_unit.io.req_out(i)
  }


  //val bus_arbiter = Wire(Vec(nCache, new DecoupledIO(new Ctrl_arbiter(p))))
//
  //for (i <- 0 until nCache) {
  //  io.reqs(i).req_control.ready  := bus_arbiter(i).ready
  //  bus_arbiter(i).valid          := io.reqs(i).req_control.valid
  //  bus_arbiter(i).bits.hart      := io.reqs(i).req_control.hart
  //  bus_arbiter(i).bits.addr      := io.reqs(i).req_control.ctrl.addr
  //  bus_arbiter(i).bits.op        := io.reqs(i).req_control.ctrl.op
  //}
//
  //// Arbitre Round-Robin pour gérer les accès concurrents
  //val arbiter = Module(new RRArbiter(Flipped(new Ctrl_arbiter(p)), nCache))
//
  //arbiter.io.out.ready := true.B
//
  //for (i <- 0 until nCache) {
  ////  io.reqs(i).req_control.ready := arbiter.io.in(i).ready
  ////  arbiter.io.in(i).valid       := io.reqs(i).req_control.valid
  ////  arbiter.io.in(i).bits        <> io.reqs(i).req_control
  //    arbiter.io.in(i) <> bus_arbiter(i)
  //}

  // Requête sélectionnée

  val selectedReq   = arbiter.io.out.bits
  val selectedValid = arbiter.io.out.valid
  val selectedAddr  = selectedReq.addr
  val selectedHart  = selectedReq.hart
  val selectedOp    = selectedReq.op

  // Extraction de l'index à partir de l'adresse mémoire
  val w_index    = selectedAddr(indexWidth + offsetWidth - 1 + 2, offsetWidth + 2)
  val w_tag      = selectedAddr(addrWidth - 1, indexWidth + offsetWidth +2)
  val r_presence = r_directory(w_index).presence

  /////////////////////////// CONTROL MSI //////////////////////////

  when(selectedValid) {
    when(w_tag === r_directory(w_index).tag){
      switch(r_directory(w_index).state) {
        is(State_MSI.s_invalid) {
          when(selectedOp === OpType.WRITE) {
            for (i <- 0 until nCache) {
              when(i.U =/= arbiter.io.chosen) { r_presence(i) := false.B }
            }
            r_presence(arbiter.io.chosen) := true.B
            r_directory(w_index).state := State_MSI.s_modified
          }.elsewhen(selectedOp === OpType.READ){
            r_presence(arbiter.io.chosen) := true.B
            r_directory(w_index).state := State_MSI.s_shared
          }
        }
        is(State_MSI.s_shared) {
          when(selectedOp === OpType.WRITE) {
            for (i <- 0 until nCache) {
              when(i.U =/= arbiter.io.chosen) { r_presence(i) := false.B }
            }
            r_presence(arbiter.io.chosen) := true.B
            r_directory(w_index).state := State_MSI.s_modified
          }.elsewhen(selectedOp === OpType.READ){
            r_presence(arbiter.io.chosen) := true.B
          }
        }
        is(State_MSI.s_modified) {
            when(selectedOp === OpType.WRITE) {
            for (i <- 0 until nCache) {
              when(i.U =/= arbiter.io.chosen) { r_presence(i) := false.B }
            }
            r_presence(arbiter.io.chosen) := true.B
            r_directory(w_index).state := State_MSI.s_modified
          }.elsewhen(selectedOp === OpType.READ){
            r_presence(arbiter.io.chosen) := true.B
            r_directory(w_index).state := State_MSI.s_shared
          }
        }
        is(State_MSI.s_exclu)    {
          // Not used 
        }
      }
    }.otherwise {
      r_directory(w_index).tag := w_tag
      when(selectedOp === OpType.WRITE) {
        for (i <- 0 until nCache) {
          when(i.U =/= arbiter.io.chosen) { r_presence(i) := false.B }
        }
        r_presence(arbiter.io.chosen) := true.B
        r_directory(w_index).state := State_MSI.s_modified
      }.elsewhen(selectedOp === OpType.READ){
        //for (i <- 0 until nCache) {
        //  when(i.U =/= arbiter.io.chosen) { r_presence(i) := false.B }
        //}
        r_presence(arbiter.io.chosen) := true.B
        r_directory(w_index).state := State_MSI.s_shared
      }
    }
  }

  //////////////////////// CACHE REP ///////////////////////////////

  // val r_ready = RegInit(VecInit(Seq.fill(nCache)(false.B)))

  for (i <- 0 until nCache) {
    // r_ready(i) := arbiter.io.out.ready && (arbiter.io.chosen === i.U)
    io.reqs(i).rep_state := Mux(r_directory(w_index).presence(i), r_directory(w_index).state, State_MSI.s_invalid)
    io.reqs(i).req_control.ready := arbiter.io.out.ready && (arbiter.io.chosen === i.U)
    // bus_arbiter(i).ready
  }

}


// Génération du Verilog
object DirectoryController extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new DirectoryController(MilkConfig0, 512, 16, 4),
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
