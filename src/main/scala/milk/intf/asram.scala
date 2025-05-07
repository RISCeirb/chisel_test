/*
 * File: asram.scala                                                           *
 * Created Date: 2023-02-25 01:16:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2024-09-23 11:01:07 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2024 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.common.mem.milk

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.mem.asram._
import herd.common.util.{Counter}


object MilkASramFSM extends ChiselEnum {
  val s0IDLE, s1START, s2READ, s3WRITE = Value
}

class MilkASramBus(p: MilkASramParams) extends Bundle {
  val cen = Bool()
  val wen = Bool()
  val addr = UInt(p.nAddrBit.W)
  val size = UInt(SIZE.NBIT.W)
  val data = UInt(p.nDataBit.W)
}

class MilkASram (p: MilkASramParams) extends Module {
  import herd.common.mem.milk.MilkASramFSM._
  
  val io = IO(new Bundle {
    val b_milk = Flipped(new MilkIO(p.pPort(0)))

    val b_asram = new ASramIO(p.pASram)
  })  

  val m_req = Module(new MilkReqInBack(p.pPort(0), false))
  val m_wreq = Module(new GenFifo(p, new MilkReqBus(p.pPort(0)), UInt(0.W), 4, p.nASramReqDepth, 1, 1, true))
  val m_write = Module(new MilkDataInBack(p.pPort(0), false))
  val m_op = Module(new GenFifo(p, new MilkReqBus(p.pPort(0)), UInt(p.nDataBit.W), 4, p.nASramOpDepth, 1, 1, true))
  val m_read = Module(new GenFifo(p, UInt(0.W), UInt(p.nDataBit.W), 4, p.nASramReadDepth, 1, 1, true))

  val r_fsm = RegInit(s0IDLE)
  val r_cen = RegInit(1.B)
  val r_wen = Reg(Bool())
  val r_hart = Reg(UInt(log2Ceil(p.nHart).W))
  val r_addr = Reg(UInt(19.W))
  val r_size = Reg(UInt(SIZE.NBIT.W))
  val r_data = Reg(UInt(p.nDataBit.W))

  // ******************************
  //              REQ
  // ******************************
  m_req.io.b_port <> io.b_milk.req
  m_write.io.b_port <> io.b_milk.write

  // ------------------------------
  //             PORT
  // ------------------------------
  if (p.pPort(0).useAmo) {
    when (m_req.io.b_out.ctrl.get.a) {
      m_req.io.b_out.ready := m_op.io.b_in(0).ready & m_wreq.io.b_in(0).ready
    }.elsewhen (m_req.io.b_out.ctrl.get.wo) {
      m_req.io.b_out.ready := m_wreq.io.b_in(0).ready
    }.otherwise {
      m_req.io.b_out.ready := m_op.io.b_in(0).ready & ~m_wreq.io.b_out(0).valid
    }
  } else {
    when (m_req.io.b_out.ctrl.get.wo) {
      m_req.io.b_out.ready := m_wreq.io.b_in(0).ready
    }.otherwise {
      m_req.io.b_out.ready := m_op.io.b_in(0).ready & ~m_wreq.io.b_out(0).valid
    }
  }

  // ------------------------------
  //        READ/WRITE FORK
  // ------------------------------
  m_wreq.io.i_flush := 0.U.asTypeOf(m_wreq.io.i_flush)
  
  m_wreq.io.b_in(0) := DontCare 
  m_wreq.io.b_in(0).valid := false.B 

  when (m_req.io.b_out.valid & m_req.io.b_out.ctrl.get.wa) {
    if (p.pPort(0).useAmo) {
      m_wreq.io.b_in(0).valid := m_req.io.b_out.ctrl.get.wo | m_op.io.b_in(0).ready
    } else {
      m_wreq.io.b_in(0).valid := true.B
    }
    m_wreq.io.b_in(0).hart := m_req.io.b_out.hart
    m_wreq.io.b_in(0).ctrl.get := m_req.io.b_out.ctrl.get
  }

  // ------------------------------
  //              OP
  // ------------------------------
  for (h <- 0 until p.nHart) {
    m_op.io.i_flush(h) := false.B
  }
  m_op.io.b_in(0) := DontCare 
  m_op.io.b_in(0).valid := false.B 

  m_wreq.io.b_out(0).ready := m_write.io.b_out.valid & m_op.io.b_in(0).ready
  m_write.io.b_out.ready := m_wreq.io.b_out(0).valid & m_op.io.b_in(0).ready

  when (m_wreq.io.b_out(0).valid) {
    m_op.io.b_in(0).valid := m_write.io.b_out.valid
    m_op.io.b_in(0).hart := m_wreq.io.b_out(0).hart
    m_op.io.b_in(0).ctrl.get := m_wreq.io.b_out(0).ctrl.get
    m_op.io.b_in(0).data.get := m_write.io.b_out.data.get
  }.elsewhen(m_req.io.b_out.valid & m_req.io.b_out.ctrl.get.ra) {
    m_op.io.b_in(0).data.get := DontCare
    if (p.pPort(0).useAmo) {
      m_op.io.b_in(0).valid := m_req.io.b_out.ctrl.get.ro | m_wreq.io.b_in(0).ready
      m_op.io.b_in(0).hart := m_req.io.b_out.hart
      m_op.io.b_in(0).ctrl.get := m_req.io.b_out.ctrl.get
      when (m_req.io.b_out.ctrl.get.a) {
        m_op.io.b_in(0).ctrl.get.op := OP.R
      }
    } else {
      m_op.io.b_in(0).valid := true.B
      m_op.io.b_in(0).hart := m_req.io.b_out.hart
      m_op.io.b_in(0).ctrl.get := m_req.io.b_out.ctrl.get
    }
  }

  // ******************************
  //            ASRAM OP
  // ******************************
  val m_dcnt = Module(new Counter(log2Ceil(p.nDataByte + 1)))

  val w_new = Wire(Bool())
  val w_end = Wire(Bool())
  val w_rdata = Wire(UInt(p.nDataBit.W))

  // ------------------------------
  //            COUNTERS
  // ------------------------------
  m_dcnt.io.i_limit := SIZE.toByte(r_size)
  m_dcnt.io.i_init := true.B
  m_dcnt.io.i_en := false.B

  // ------------------------------
  //             FSM
  // ------------------------------
  m_op.io.b_out(0).ready := w_new

  w_new := false.B
  w_end := false.B
  w_rdata := r_data | (io.b_asram.data.in << (m_dcnt.io.o_val << 3.U))

  switch(r_fsm) {
    is (s0IDLE) {
      w_new := (m_op.io.b_out(0).ctrl.get.wa | m_read.io.b_in(0).ready)
      when (m_op.io.b_out(0).valid & w_new) {
        r_fsm := s1START
        r_hart := m_op.io.b_out(0).hart
        r_wen := ~m_op.io.b_out(0).ctrl.get.wa
        r_addr := m_op.io.b_out(0).ctrl.get.addr
        r_size := m_op.io.b_out(0).ctrl.get.size
        when (m_op.io.b_out(0).ctrl.get.wa) {
          r_data := m_op.io.b_out(0).data.get
        }.otherwise {
          r_data := 0.U
        }              
      }
    }

    is (s1START) {
      r_cen := 0.B
      when (~r_wen) {
        r_fsm := s3WRITE
      }.otherwise {
        r_fsm := s2READ
      }
    }
    
    is (s2READ) {
      when (m_dcnt.io.o_flag) {
        m_dcnt.io.i_init := true.B
        m_dcnt.io.i_en := false.B
        w_new := (m_op.io.b_out(0).ctrl.get.wa | m_read.io.b_in(0).ready)
        w_end := true.B

        when (m_op.io.b_out(0).valid & w_new) {
          r_hart := m_op.io.b_out(0).hart
          r_wen := ~m_op.io.b_out(0).ctrl.get.wa
          r_addr := m_op.io.b_out(0).ctrl.get.addr
          r_size := m_op.io.b_out(0).ctrl.get.size
          when (m_op.io.b_out(0).ctrl.get.wa) {
            r_fsm := s3WRITE
            r_wen := false.B
            r_data := m_op.io.b_out(0).data.get
          }.otherwise {
            r_fsm := s2READ
            r_wen := true.B
            r_data := 0.U
          }   
        }.otherwise {
          r_fsm := s0IDLE
          r_cen := 1.B
        }
      }.otherwise {
        m_dcnt.io.i_init := false.B
        m_dcnt.io.i_en := true.B

        r_addr := r_addr + 1.U
        r_data := w_rdata
      }      
    }
    
    is (s3WRITE) {
      when (m_dcnt.io.o_flag) {
        m_dcnt.io.i_init := true.B
        m_dcnt.io.i_en := false.B
        w_new := (m_op.io.b_out(0).ctrl.get.wa | m_read.io.b_in(0).ready)
        w_end := true.B

        when (m_op.io.b_out(0).valid & w_new) {
          r_hart := m_op.io.b_out(0).hart
          r_wen := ~m_op.io.b_out(0).ctrl.get.wa
          r_addr := m_op.io.b_out(0).ctrl.get.addr
          r_size := m_op.io.b_out(0).ctrl.get.size
          when (m_op.io.b_out(0).ctrl.get.wa) {
            r_fsm := s3WRITE
            r_wen := false.B
            r_data := m_op.io.b_out(0).data.get
          }.otherwise {
            r_fsm := s2READ
            r_wen := true.B
            r_data := 0.U
          }   
        }.otherwise {
          r_fsm := s0IDLE
          r_cen := 1.B
        }
      }.otherwise {
        m_dcnt.io.i_init := false.B
        m_dcnt.io.i_en := true.B

        r_addr := r_addr + 1.U
        r_data := (r_data >> 8.U)
      }
    }
  }

  // ------------------------------
  //           INTERFACE
  // ------------------------------
  io.b_asram.cen := r_cen
  io.b_asram.wen := r_wen
  io.b_asram.oen := ~r_wen
  io.b_asram.addr := r_addr
  io.b_asram.data.eno := Cat(Fill(8, ~r_wen))
  io.b_asram.data.out := r_data(7, 0)

  // ******************************
  //             READ
  // ******************************
  // ------------------------------
  //             INPUT
  // ------------------------------
  for (h <- 0 until p.nHart) {
    m_read.io.i_flush(h) := false.B
  }

  m_read.io.b_in(0).valid := w_end & (r_fsm === s2READ)
  m_read.io.b_in(0).hart := r_hart
  m_read.io.b_in(0).data.get := w_rdata

  // ------------------------------
  //            PORT
  // ------------------------------
  m_read.io.b_out(0).ready := io.b_milk.read.ready
  io.b_milk.read.valid := m_read.io.b_out(0).valid
  io.b_milk.read.hart := m_read.io.b_out(0).hart
  io.b_milk.read.data := m_read.io.b_out(0).data.get

  // ******************************
  //            REPORT
  // ******************************
  def report (): Unit = {
    println("------------------------------")
    println("Interface: ASRAM")
    println("Data size: " + p.nDataBit)
    println("Address base: 0x" + p.nAddrBase.substring(1))
    println("Memory size: 0x" + p.nByte.substring(1))
    println("------------------------------")
  }

  // ******************************
  //          SIMULATION
  // ******************************  
  if (p.isSim) {
    
  }
}

object MilkASram extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkASram(MilkASramConfigBase),
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