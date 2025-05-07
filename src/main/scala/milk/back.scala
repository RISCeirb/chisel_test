/*
 * File: back.scala                                                            *
 * Created Date: 2023-02-25 12:54:02 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2025-03-17 12:22:55 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2025 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.common.mem.milk

import chisel3._
import chisel3.util._

import herd.common.gen._


class MilkReqInBack(p: MilkParams, useReg: Boolean) extends Module {    
  // ******************************
  //             I/Os
  // ******************************
  val io = IO(new Bundle {
    val b_port = Flipped(new MilkReqIO(p))

    val o_back = Output(new GenVBus(p, new MilkReqBus(p), UInt(0.W)))
    val o_reg = if (useReg) Some(Output(new GenVBus(p, new MilkReqBus(p), UInt(0.W)))) else None

    val b_out = new GenRVIO(p, new MilkReqBus(p), UInt(0.W))
  })
  
  val m_reg = Module(new GenBack(p, new MilkReqBus(p), UInt(0.W), true, false, useReg))

  m_reg.io.i_flush := 0.U.asTypeOf(m_reg.io.i_flush)

  io.b_port.ready := m_reg.io.b_in.ready
  m_reg.io.b_in.valid := io.b_port.valid
  m_reg.io.b_in.hart := io.b_port.hart
  m_reg.io.b_in.ctrl.get := io.b_port.ctrl

  io.o_back := m_reg.io.o_back
  if (useReg) io.o_reg.get := m_reg.io.o_reg.get

  io.b_out <> m_reg.io.b_out
}

class MilkReqOutBack(p: MilkParams, useReg: Boolean) extends Module {    
  // ******************************
  //             I/Os
  // ******************************
  val io = IO(new Bundle {
    val b_in = Flipped(new GenRVIO(p, new MilkReqBus(p), UInt(0.W)))

    val o_back = Output(new GenVBus(p, new MilkReqBus(p), UInt(0.W)))
    val o_reg = if (useReg) Some(Output(new GenVBus(p, new MilkReqBus(p), UInt(0.W)))) else None

    val b_port = new MilkReqIO(p)
  })
  
  val m_reg = Module(new GenBack(p, new MilkReqBus(p), UInt(0.W), true, false, useReg))

  m_reg.io.i_flush := 0.U.asTypeOf(m_reg.io.i_flush)

  m_reg.io.b_in <> io.b_in

  io.o_back := m_reg.io.o_back
  if (useReg) io.o_reg.get := m_reg.io.o_reg.get

  m_reg.io.b_out.ready := io.b_port.ready
  io.b_port.valid := m_reg.io.b_out.valid
  io.b_port.hart := m_reg.io.b_out.hart
  io.b_port.ctrl := m_reg.io.b_out.ctrl.get
}

class MilkDataInBack(p: MilkParams, useReg: Boolean) extends Module {    
  // ******************************
  //             I/Os
  // ******************************
  val io = IO(new Bundle {
    val b_port = Flipped(new MilkDataIO(p))

    val o_back = Output(new GenVBus(p, UInt(0.W), UInt((p.nDataByte * 8).W)))
    val o_reg = if (useReg) Some(Output(new GenVBus(p, UInt(0.W), UInt((p.nDataByte * 8).W)))) else None

    val b_out = new GenRVIO(p, UInt(0.W), UInt((p.nDataByte * 8).W))
  })
  
  val m_reg = Module(new GenBack(p, UInt(0.W), UInt((p.nDataByte * 8).W), true, false, useReg))

  m_reg.io.i_flush := 0.U.asTypeOf(m_reg.io.i_flush)

  io.b_port.ready := m_reg.io.b_in.ready
  m_reg.io.b_in.valid := io.b_port.valid
  m_reg.io.b_in.hart := io.b_port.hart
  m_reg.io.b_in.data.get := io.b_port.data

  io.o_back := m_reg.io.o_back
  if (useReg) io.o_reg.get := m_reg.io.o_reg.get

  io.b_out <> m_reg.io.b_out
}

class MilkDataOutBack(p: MilkParams, useReg: Boolean) extends Module {    
  // ******************************
  //             I/Os
  // ******************************
  val io = IO(new Bundle {
    val b_in = Flipped(new GenRVIO(p, UInt(0.W), UInt((p.nDataByte * 8).W)))

    val o_back = Output(new GenVBus(p, UInt(0.W), UInt((p.nDataByte * 8).W)))
    val o_reg = if (useReg) Some(Output(new GenVBus(p, UInt(0.W), UInt((p.nDataByte * 8).W)))) else None

    val b_port = new MilkDataIO(p)
  })
  
  val m_reg = Module(new GenBack(p, UInt(0.W), UInt((p.nDataByte * 8).W), true, false, useReg))

  m_reg.io.i_flush := 0.U.asTypeOf(m_reg.io.i_flush)

  m_reg.io.b_in <> io.b_in

  io.o_back := m_reg.io.o_back
  if (useReg) io.o_reg.get := m_reg.io.o_reg.get

  m_reg.io.b_out.ready := io.b_port.ready
  io.b_port.valid := m_reg.io.b_out.valid
  io.b_port.hart := m_reg.io.b_out.hart
  io.b_port.data := m_reg.io.b_out.data.get
}

object MilkReqInBack extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkReqInBack(MilkConfig0, false),
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

object MilkReqOutBack extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkReqInBack(MilkConfig0, false),
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

object MilkDataInBack extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkDataInBack(MilkConfig0, false),
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

object MilkDataOutBack extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkDataOutBack(MilkConfig0, false),
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