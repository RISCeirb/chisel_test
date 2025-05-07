package herd.draft.multi.control

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.bus._
import herd.common.core._
import herd.common.mem.milk._

import _root_.circt.stage.ChiselStage
import herd.core.betizu.CacheConfig
import herd.core.betizu.mycacheconfig


class stall_coherency(p: MilkParams, z : CacheConfig, nCache: Int) extends Module {
  val io = IO(new Bundle {
    val req_in    = Flipped(Decoupled(new Ctrl_arbiter(p)))
    val req_out   = Decoupled(new Ctrl_arbiter(p))
    val ack_write = Input(Bool()) 
    //val ack_inv   = Input(Bool()) 
    val ack_inv   = Input(Vec(nCache, Bool()))
  })

  val cacheSize = z.cacheSize
  val lineSize  = z.lineSize

  io := DontCare

  val write_request = io.req_in.valid && io.req_in.bits.op === OP.W 

  object State_coherency extends ChiselEnum {
    val wait_write ,wait_ack_write = Value
  }

  val r_fsm_write = RegInit(State_coherency.wait_write)
  val r_valid = RegInit(false.B)
  val r_bits  = RegInit(0.U.asTypeOf(new Ctrl_arbiter(p)))

  val end_stall_write = ((r_fsm_write === State_coherency.wait_write) || (r_fsm_write === State_coherency.wait_ack_write && io.ack_write))
  // val r_fsm_inv = RegInit(VecInit(Seq.fill(nCache)(State_coherency.wait_write)))
  // val all_ack_inv_received = r_fsm_inv.map(_ === State_coherency.wait_write).reduce(_ && _)
  val end_stall   = end_stall_write // all_ack_inv_received && end_stall_write 

  ////////////////////////// ACK WRITE //////////////////////////////

  when(write_request && (r_fsm_write === State_coherency.wait_write) && ~ io.ack_write /*&& end_stall*/) {
    r_fsm_write := State_coherency.wait_ack_write
  }.elsewhen(io.ack_write && (r_fsm_write === State_coherency.wait_ack_write)){
    r_fsm_write := State_coherency.wait_write
  }

  //////////////////////////  ACK INV   /////////////////////////////

  //for (i <- 0 until nCache) {
  //  when(write_request && (r_fsm_inv(i) === State_coherency.wait_write) && io.req_in.ready) {
  //    r_fsm_inv(i) := State_coherency.wait_ack_write
  //  }.elsewhen((r_fsm_inv(i) === State_coherency.wait_ack_write) && io.ack_inv(i) && end_stall_write){
  //    r_fsm_inv(i) := State_coherency.wait_write
  //  }
  //}

  ////////////////////////// STALL REQ /////////////////////////////

  switch(r_fsm_write) {
    is(State_coherency.wait_write) {
      r_valid := true.B
      r_bits  := io.req_in.bits
    }
    is(State_coherency.wait_ack_write) {
      // Pas d'actualisation 
      when(end_stall){
        r_valid := true.B
        r_bits  := io.req_in.bits
      }.otherwise{
        r_valid := false.B
      }
    }
  }

  // Gestion des requêtes
  io.req_out.valid := r_valid 
  io.req_out.bits  := r_bits
  io.req_in.ready  := end_stall 
  
}


// Directory-Based Cache Controller
class DirectoryController(p : MilkParams, z : CacheConfig, nCache: Int) extends Module {
  val io = IO(new Bundle {
    val reqs        = Vec(nCache, Flipped(new Bus_control(p)))
  })

  val cacheSize = z.cacheSize
  val lineSize  = z.lineSize

  io := DontCare
  dontTouch(io)

  def addrWidth = p.nAddrBit

  // Paramètres mémoire/cache
  def numSetsDir  = nCache * cacheSize / lineSize
  def numSets     = cacheSize / lineSize                    // Nombre de blocs dans le cache
  def indexWidth  = log2Ceil(numSets)                       // Bits nécessaires pour l'index
  def offsetWidth = log2Ceil(lineSize / (addrWidth/8))      // Bits pour l'offset
  def tagWidth    = addrWidth - indexWidth - offsetWidth    // Bits pour le tag
  def nblock      = nCache                                  // Nombre de caches

  ////////////////////////////// MODULE  ////////////////////////////

  // Annuaire pour la cohérence MSI : Vecteur de bits de présence (nblock) 

  val init_directory = Wire(Vec(numSets, new Directory_class(nblock,tagWidth)))
  for (s <- 0 until numSets) {
    init_directory(s).presence := 0.U.asTypeOf(init_directory(s).presence)
    init_directory(s).state    := State_MSI.s_invalid
    init_directory(s).tag      := 0.U
  }

  val r_directory = RegInit(init_directory)

  // Arbitre Round-Robin pour gérer les accès concurrents
  val arbiter = Module(new RRArbiter(Flipped(new Ctrl_arbiter(p)), nCache))

  // Stall unit write
  val stall_unit = Module(new stall_coherency(p, z, nCache))
  stall_unit.io := DontCare

  /////////////////////////// ARBITER REQ //////////////////////////

  // arbiter.io.out.ready := true.B

  for (i <- 0 until nCache) {
    arbiter.io.in(i).valid := io.reqs(i).req_control.valid
    arbiter.io.in(i).bits  <>  io.reqs(i).req_control.ctrl
  }

  val r_last_chosen = RegInit(0.U(log2Ceil(nCache).W))
  val chosen     = r_last_chosen

  //////////////////////////// STALL UNIT ///////////////////////////

  val control_ready = stall_unit.io.req_in.ready

  // ROUND-ROBIN <> STALL-UNIT
  stall_unit.io.req_in.valid := arbiter.io.out.valid
  stall_unit.io.req_in.bits  := arbiter.io.out.bits
  arbiter.io.out.ready       := control_ready //stall_unit.io.req_in.ready
  stall_unit.io.ack_write    := io.reqs(chosen).ack_write 
  for (i <- 0 until nCache) {
    stall_unit.io.ack_inv(i) := io.reqs(i).ack_invalid
  }

  // Requête sélectionnée

  val selectedReq   = stall_unit.io.req_out.bits
  val selectedValid = stall_unit.io.req_out.valid
  val selectedAddr  = selectedReq.addr
  val selectedHart  = selectedReq.hart
  val selectedOp    = selectedReq.op

  // Extraction de l'index à partir de l'adresse mémoire
  val w_index    = selectedAddr(indexWidth + offsetWidth - 1 + 2, offsetWidth + 2)
  val w_tag      = selectedAddr(addrWidth - 1, indexWidth + offsetWidth +2)
  val r_presence = r_directory(w_index).presence

  // LAST CHOSEN 
  when(control_ready/*selectedValid*/) {
    r_last_chosen := arbiter.io.chosen
  }

  dontTouch(w_index)
  dontTouch(w_tag)

  // val w_rep_state = WireInit(VecInit(Seq.fill(nCache)(State_MSI.s_invalid)))

  /////////////////////////// CONTROL MSI //////////////////////////

  when(selectedValid) {
    when(w_tag === r_directory(w_index).tag){
      switch(r_directory(w_index).state) {
        is(State_MSI.s_invalid) {
          when(selectedOp === OpType.WRITE) {
            for (i <- 0 until nCache) {
              when(i.U =/= chosen) { 
                r_presence(i)          := false.B 
              }
            }
            r_presence(chosen)         := true.B
            r_directory(w_index).state := State_MSI.s_modified
          }.elsewhen(selectedOp === OpType.READ){
            r_presence(chosen)         := true.B
            r_directory(w_index).state := State_MSI.s_shared
          }
        }
        is(State_MSI.s_shared) {
          when(selectedOp === OpType.WRITE) {
            for (i <- 0 until nCache) {
              when(i.U =/= chosen) { 
                r_presence(i)          := false.B
              }
            }
            r_presence(chosen)         := true.B
            r_directory(w_index).state := State_MSI.s_modified
          }.elsewhen(selectedOp === OpType.READ){
            r_presence(chosen)         := true.B
          }
        }
        is(State_MSI.s_modified) {
            when(selectedOp === OpType.WRITE) {
            for (i <- 0 until nCache) {
              when(i.U =/= chosen) { 
                r_presence(i)          := false.B 
                }
            }
            r_presence(chosen) := true.B
            r_directory(w_index).state := State_MSI.s_modified
          }.elsewhen(selectedOp === OpType.READ){
            r_presence(chosen) := true.B
            r_directory(w_index).state := State_MSI.s_shared
          }
        }
        is(State_MSI.s_exclu)    {
          // TO DO
        }
      }
    }.otherwise {
      r_directory(w_index).tag := w_tag
      when(selectedOp === OpType.WRITE) {
        for (i <- 0 until nCache) {
          when(i.U =/= chosen) { r_presence(i) := false.B }
        }
        r_presence(chosen) := true.B
        r_directory(w_index).state := State_MSI.s_modified
      }.elsewhen(selectedOp === OpType.READ){
        // for (i <- 0 until nCache) {
        //   when(i.U =/= chosen) { r_presence(i) := false.B }
        // }
        r_presence(chosen) := true.B
        r_directory(w_index).state := State_MSI.s_shared
      }
    }
  }

  //////////////////////// CACHE REP ///////////////////////////////

  dontTouch(selectedValid)
  dontTouch(selectedReq)

  for (i <- 0 until nCache) {
    io.reqs(i).rep_state         := Mux(r_directory(w_index).presence(i), r_directory(w_index).state, State_MSI.s_invalid) 
    io.reqs(i).req_control.ready := selectedValid || (r_last_chosen === i.U) //arbiter.io.out.valid && r_directory(w_index).presence(i)
    io.reqs(i).addr              := selectedAddr
  }

}


// Génération du Verilog
object DirectoryController extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new DirectoryController(MilkConfig0, mycacheconfig, 4),
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
