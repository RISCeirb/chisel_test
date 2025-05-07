/*
 * File: mig.scala                                                             *
 * Created Date: 2022-05-27 10:37:51 am
 * Author: Mathieu Escouteloup
 * -----
 * Last Modified: 2024-07-12 10:02:27 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----
 * License: See LICENSE.md
 * Copyright (c) 2024 HerdWare                                                 *
 * -----
 * Description: 
 */


package herd.common.mem.milk

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.mem.mig._


class MilkMig (p: MilkMigParams) extends Module {
  for (np <- 0 until p.nPort) {
    require(p.nDataByte >= p.pPort(np).nDataByte, "MIG data size must be greater than or equal to MILK port data size.")
  }
  require ((p.nReadFifoDepth >= p.nAckFifoDepth), "Read buffer must have have a size greater than et equal to Ack buffer size.")
  
  val io = IO(new Bundle {
    val clk_milk = Input(Vec(p.nPort, Clock()))
    val rst_milk = Input(Vec(p.nPort, Bool()))
    
    val b_milk = MixedVec(
      for (pp <- p.pPort) yield {
        val b_milk = Flipped(new MilkIO(pp))
        b_milk
      }
    )

    val clk_mig = Input(Clock())
    val rst_mig = Input(Bool())

    val b_mig = new MigIO(p.pMig)
  })
   
  val m_req = for (np <- 0 until p.nPort) yield {
    val m_req = Module(new GenAFifo(p, new MilkReqBus(p.pPort(np)), UInt(0.W), p.nReqFifoDepth, 2))
    m_req
  } 

  val m_write = for (np <- 0 until p.nPort) yield {
    val m_write = Module(new GenAFifo(p, UInt(0.W), UInt((p.nDataByte * 8).W), p.nReqFifoDepth, 2))
    m_write
  } 
  
  val m_order = withClockAndReset(io.clk_mig, io.rst_mig) {Module(new GenFifo(p, UInt(log2Ceil(p.nPort + 1).W), UInt(0.W), 4, (p.nAckFifoDepth * p.nPort), 1, 1, false))}
  val m_ack = for (np <- 0 until p.nPort) yield {
    val m_ack = withClockAndReset(io.clk_mig, io.rst_mig) {Module(new GenFifo(p, new MilkReqBus(p.pPort(np)), UInt(0.W), 4, p.nAckFifoDepth, 1, 1,false))}
    m_ack
  } 

  val m_data = for (np <- 0 until p.nPort) yield {
    val m_data = withClockAndReset(io.clk_mig, io.rst_mig) {Module(new GenFifo(p, UInt(0.W), UInt((p.nDataByte * 8).W), 4, p.nAckFifoDepth, 1, 1,false))}
    m_data
  } 

  val m_read = for (np <- 0 until p.nPort) yield {
    val m_read = Module(new GenAFifo(p, UInt(0.W), UInt((p.nDataByte * 8).W), p.nReadFifoDepth, 2))
    m_read
  } 

  val r_wa = RegInit(VecInit(Seq.fill(p.nPort)(false.B)))

  val w_read_av = Wire(Vec(p.nPort, Vec(p.nAckFifoDepth, Bool())))

  for (np <- 0 until p.nPort) {
    m_req(np).io.clk_in := io.clk_milk(np)
    m_req(np).io.rst_in := io.rst_milk(np)
    m_req(np).io.clk_out := io.clk_mig
    m_req(np).io.rst_out := io.rst_mig

    m_write(np).io.clk_in := io.clk_milk(np)
    m_write(np).io.rst_in := io.rst_milk(np)
    m_write(np).io.clk_out := io.clk_mig
    m_write(np).io.rst_out := io.rst_mig

    m_read(np).io.clk_in := io.clk_mig
    m_read(np).io.rst_in := io.rst_mig
    m_read(np).io.clk_out := io.clk_milk(np)
    m_read(np).io.rst_out := io.rst_milk(np)
  }

  m_order.io.i_flush(0) := false.B
  for (np <- 0 until p.nPort) {
    m_ack(np).io.i_flush(0) := false.B
    m_data(np).io.i_flush(0) := false.B
  }

  // ******************************
  //           REQ & WRITE
  // ******************************
  // ------------------------------
  //              REQ
  // ------------------------------
  for (np <- 0 until p.nPort) {    
    io.b_milk(np).req.ready := m_req(np).io.b_in.ready        
    m_req(np).io.b_in.valid := io.b_milk(np).req.valid     
    m_req(np).io.b_in.hart := io.b_milk(np).req.hart
    m_req(np).io.b_in.ctrl.get := DontCare
    m_req(np).io.b_in.ctrl.get.op := io.b_milk(np).req.ctrl.op
    m_req(np).io.b_in.ctrl.get.size := io.b_milk(np).req.ctrl.size
    m_req(np).io.b_in.ctrl.get.addr := io.b_milk(np).req.ctrl.addr
  
    m_req(np).io.clk_out := io.clk_mig
    m_req(np).io.rst_out := io.rst_mig
  }

  // ------------------------------
  //              WRITE
  // ------------------------------
  for (np <- 0 until p.nPort) {    
    io.b_milk(np).write.ready := m_write(np).io.b_in.ready    
    m_write(np).io.b_in.valid := io.b_milk(np).write.valid 
    m_write(np).io.b_in.hart := io.b_milk(np).write.hart
    m_write(np).io.b_in.data.get := io.b_milk(np).write.data    

    m_write(np).io.clk_out := io.clk_mig
    m_write(np).io.rst_out := io.rst_mig
  }

  // ------------------------------
  //              MIG
  // ------------------------------
  val w_mig_req_read = Wire(Vec(p.nPort, Bool()))
  val w_mig_req_write = Wire(Vec(p.nPort, Bool()))
  val w_mig_req_free = Wire(Vec(p.nPort + 1, Bool()))
  val w_mig_req_av = Wire(Vec(p.nPort, Bool()))

  w_mig_req_free(0) := ~r_wa.asUInt.orR
  io.b_mig.req := DontCare
  io.b_mig.req.valid := false.B
  io.b_mig.write := DontCare
  io.b_mig.write.valid := false.B
  for (np <- 0 until p.nPort) {
    m_ack(np).io.b_in(0) := DontCare
    m_ack(np).io.b_in(0).valid := false.B
  }
  m_order.io.b_in(0) := DontCare
  m_order.io.b_in(0).valid := false.B

  for (np <- 0 until p.nPort) {
    w_mig_req_read(np) := m_req(np).io.b_out.ctrl.get.ro | (m_req(np).io.b_out.ctrl.get.ra & ~r_wa(np))
    w_mig_req_write(np) := m_req(np).io.b_out.ctrl.get.wo | (m_req(np).io.b_out.ctrl.get.wa & r_wa(np))    
    w_mig_req_free(np + 1) := w_mig_req_free(np) & ~(m_req(np).io.b_out.valid & io.b_mig.req.ready & ((w_mig_req_write(np) & io.b_mig.write.ready) | (w_mig_req_read(np) & w_read_av(np).asUInt.orR)))
    w_mig_req_av(np) := w_mig_req_free(np) | r_wa(np)

    m_req(np).io.b_out.ready := m_req(np).io.b_out.valid & io.b_mig.req.ready & w_mig_req_av(np) & ((w_mig_req_write(np) & io.b_mig.write.ready & m_write(np).io.b_out.valid) | (w_mig_req_read(np) & w_read_av(np).asUInt.orR))
    m_write(np).io.b_out.ready := m_req(np).io.b_out.valid & w_mig_req_write(np) & io.b_mig.req.ready & io.b_mig.write.ready & w_mig_req_av(np)

    when (w_mig_req_av(np)) {
      r_wa(np) := ~r_wa(np) & m_req(np).io.b_out.ctrl.get.a & io.b_mig.write.ready & w_read_av(np).asUInt.orR

      io.b_mig.req.valid := m_req(np).io.b_out.valid & ((w_mig_req_write(np) & m_write(np).io.b_out.valid & io.b_mig.write.ready) | (w_mig_req_read(np) & w_read_av(np).asUInt.orR))
      io.b_mig.req.cmd := Mux(w_mig_req_write(np), CMD.W, CMD.R)
      io.b_mig.req.addr := Cat(m_req(np).io.b_out.ctrl.get.addr(p.nAddrBit - 1, log2Ceil(p.nDataByte)), 0.U(log2Ceil(p.nDataByte).W))         

      io.b_mig.write.valid := m_req(np).io.b_out.valid & w_mig_req_write(np) & m_write(np).io.b_out.valid & io.b_mig.req.ready  
      io.b_mig.write.end := true.B
      if (p.pPort(np).nDataByte < p.nDataByte) {
        io.b_mig.write.mask := ~(Cat(Fill(p.nDataByte - p.pPort(np).nDataByte, 1.B), SIZE.toMask(p.nDataByte, m_req(np).io.b_out.ctrl.get.size)) << m_req(np).io.b_out.ctrl.get.addr(log2Ceil(p.nDataByte) - 1, 0))
      } else {
        io.b_mig.write.mask := ~(SIZE.toMask(p.nDataByte, m_req(np).io.b_out.ctrl.get.size) << m_req(np).io.b_out.ctrl.get.addr(log2Ceil(p.nDataByte) - 1, 0))
      }
      io.b_mig.write.data := (m_write(np).io.b_out.data.get << (m_req(np).io.b_out.ctrl.get.addr(log2Ceil(p.nDataByte) - 1, 0) << 3.U))

      m_order.io.b_in(0).valid := m_req(np).io.b_out.valid & w_mig_req_read(np) & io.b_mig.req.ready
      m_order.io.b_in(0).ctrl.get := np.U
      m_ack(np).io.b_in(0).valid := m_req(np).io.b_out.valid & w_mig_req_read(np) & io.b_mig.req.ready & w_read_av(np).asUInt.orR
      m_ack(np).io.b_in(0).ctrl.get := m_req(np).io.b_out.ctrl.get
    }    
  }

  // ******************************
  //           ACK & READ
  // ******************************
  // ------------------------------
  //             FREE
  // ------------------------------
  for (np <- 0 until p.nPort) {
    for (ad <- 0 until p.nAckFifoDepth) {
      w_read_av(np)(ad) := (m_ack(np).io.o_pt <= ad.U) & (m_data(np).io.o_pt <= (p.nAckFifoDepth - 1 - ad).U)
    }
  }

  // ------------------------------
  //              MIG
  // ------------------------------
  m_order.io.b_out(0).ready := false.B
  for (np <- 0 until p.nPort) {
    m_ack(np).io.b_out(0).ready := false.B
    m_data(np).io.b_in(0) := DontCare
    m_data(np).io.b_in(0).valid := false.B
  }

  for (np <- 0 until p.nPort) {
    when (np.U === m_order.io.b_out(0).ctrl.get) {
      m_order.io.b_out(0).ready := m_ack(np).io.b_out(0).valid & io.b_mig.read.valid & m_data(np).io.b_in(0).ready
      m_ack(np).io.b_out(0).ready := io.b_mig.read.valid & m_data(np).io.b_in(0).ready
      m_data(np).io.b_in(0).valid := m_ack(np).io.b_out(0).valid & io.b_mig.read.valid
      m_data(np).io.b_in(0).data.get := (io.b_mig.read.data >> (m_ack(np).io.b_out(0).ctrl.get.addr(log2Ceil(p.nDataByte) - 1, 0) << 3.U))
    }
  }

  // ------------------------------
  //              READ
  // ------------------------------
  for (np <- 0 until p.nPort) {
    m_read(np).io.b_in <> m_data(np).io.b_out(0)

    m_read(np).io.b_out.ready := io.b_milk(np).read.ready
    io.b_milk(np).read.valid := m_read(np).io.b_out.valid
    io.b_milk(np).read.hart := 0.U
    io.b_milk(np).read.data := m_read(np).io.b_out.data.get   
  }

  // ******************************
  //            REPORT
  // ******************************
  def report (): Unit = {
    println("------------------------------")
    println("Interface: MIG")
    println("Data size: " + p.nDataBit)
    println("Address base: 0x" + p.nAddrBase.substring(1))
    println("Memory size: 0x" + p.nByte.substring(1))
    println("------------------------------")
  }

  // ******************************
  //          SIMULATION
  // ******************************  
  if (p.isSim) {
    dontTouch(w_mig_req_read)
    dontTouch(w_mig_req_write)
    dontTouch(w_mig_req_free)
    dontTouch(w_mig_req_av)
  }
}
object MilkMig extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkMig(MilkMigConfigBase),
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