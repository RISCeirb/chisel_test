package herd.draft.multi.control

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.bus._
import herd.common.core._
import herd.common.mem.milk._

import _root_.circt.stage.ChiselStage

/*
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
  def cycle_stall = 24 // Nombre de cycles de stall (REQ_CONTROLLEUR + REQ_MEM + CYCLE_DATA_MEM_TO_CACHE + REP_CACHE)

  //////////////////////////// STALL UNIT ///////////////////////////


  object State_coherency extends ChiselEnum {
    val wait_write ,wait_ack_write = Value
  }

  val r_fsm_write = RegInit(State_coherency.wait_write)

  // Détection d'une écriture en cours
  val write_request = io.req_in.map(req => req.valid && (req.bits.op === OP.W)).reduce(_ || _)
  val ack_write     = io.req_in.map(_.bits.ack_write).reduce(_ || _)

  dontTouch(ack_write)

  // Signal de priorité pour les requets d'ecriture du premier coeur

  val w_valid = Wire(Vec(nCache, Bool()))

  val stall_counter = RegInit((0.U(8.W))) // 8 bits

  when(write_request){
    val temp_valid = Wire(Vec(nCache, Bool()))
    
    for (i <- 0 until nCache) {
      temp_valid(i) := io.req_in(i).valid && (io.req_in(i).bits.op === OP.W)
    }
    
    w_valid(0) := temp_valid(0)
    for (i <- 1 until nCache) {
      w_valid(i) := temp_valid(i) && !(temp_valid.slice(0, i).reduce(_ || _)) // Write req valid only if not previous cache valid
    }
  }.otherwise {
    for (i <- 0 until nCache) {
      w_valid(i) := io.req_in(i).valid
    }
  }

  when(write_request && (r_fsm_write === State_coherency.wait_write)) {
    r_fsm_write := State_coherency.wait_ack_write
  }.elsewhen(ack_write && (r_fsm_write === State_coherency.wait_ack_write)){
    r_fsm_write := State_coherency.wait_write
  }

  // Registres pour stocker les valeurs
  val r_valid = RegInit(VecInit(Seq.fill(nCache)(false.B)))
  val r_bits  = RegInit(VecInit(Seq.fill(nCache)(0.U.asTypeOf(new Ctrl_arbiter(p)))))

  switch(r_fsm_write) {
    is(State_coherency.wait_write) {
      for (i <- 0 until nCache) {
        r_valid(i) := w_valid(i)
        r_bits(i)  := io.req_in(i).bits
      }
    }
    is(State_coherency.wait_ack_write) {
      // Pas d'actualisation 
    }
  }

  // Gestion des requêtes
  for (i <- 0 until nCache) {
    //io.req_out(i).valid := w_valid(i) && (r_fsm_write =/= State_coherency.wait_ack_write) // || write_request
    //io.req_out(i).valid := w_valid(i) && (stall_counter === 0.U) // || write_request
    //io.req_out(i).bits  := io.req_in(i).bits
    io.req_out(i).valid := r_valid(i)
    io.req_out(i).bits  := r_bits(i)
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

*/


  // for (i <- 0 until nCache) {
  //   when(write_request) {
  //     // Lancer le stall si une requête arrive et qu'on n'est pas déjà en train de staller
  //     stall_counter := cycle_stall.U
  //   }
  //   when(stall_counter > 0.U) {
  //     stall_counter := stall_counter - 1.U
  //   }
  // }

  /// when(write_request){
  ///   w_valid(0) :=  io.req_in(0).valid && (write_request === io.req_in(0).bits.op)
  ///   w_valid(1) :=  !w_valid(0) && io.req_in(1).valid && (write_request === io.req_in(1).bits.op)
  ///   w_valid(2) :=  !w_valid(1) && !w_valid(0) && io.req_in(2).valid && (write_request === io.req_in(2).bits.op)
  ///   w_valid(3) :=  !w_valid(2) && !w_valid(1) && !w_valid(0) && io.req_in(3).valid && (write_request === io.req_in(3).bits.op)
  /// }.otherwise {
  ///   w_valid(0) :=  io.req_in(0).valid
  ///   w_valid(1) :=  io.req_in(1).valid
  ///   w_valid(2) :=  io.req_in(2).valid
  ///   w_valid(3) :=  io.req_in(3).valid
  /// }