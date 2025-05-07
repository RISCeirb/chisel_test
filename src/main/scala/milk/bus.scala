/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 12:54:02 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2025-02-20 11:04:26 am                                       *
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


// ******************************
//             REQ
// ******************************
class MilkReqBus(p: MilkReqParams) extends Bundle {  
  val op = UInt(p.nOpBit.W)
  val amo = if (p.useAmo) Some(UInt(AMO.NBIT.W)) else None
  val size = UInt(SIZE.NBIT.W)
  val addr = UInt(p.nAddrBit.W)
  val cache = Bool()

  def ro: Bool = {
    if (p.useAmo) {
      return (op === OP.R) | (op === OP.LR)
    } else {
      return (op === 0.B)
    }
  }
  def wo: Bool = {
    if (p.useAmo) {
      return (op === OP.W)
    } else {
      return (op === 1.B)
    }
  }
  def a: Bool = {
    if (p.useAmo) {
      return (op === OP.AMO) | (op === OP.SC)
    } else {
      return 0.B
    }
  }
  def ra: Bool = this.ro | this.a
  def wa: Bool = this.wo | this.a
}

class MilkReqIO(p: MilkReqParams) extends Bundle {
  val ready = Input(Bool())
  val valid = Output(Bool())
  val hart = UInt(log2Ceil(p.nHart).W)
  val ctrl = Output(new MilkReqBus(p))
}

// ******************************
//             ACK
// ******************************
class MilkDataIO(p: MilkDataParams) extends Bundle {
  val ready = Input(Bool())
  val valid = Output(Bool())
  val hart = UInt(log2Ceil(p.nHart).W)
  val data = Output(UInt((p.nDataByte * 8).W))
}

class MilkAckIO(p: MilkDataParams) extends Bundle {
  val write = new MilkDataIO(p)
  val read = Flipped(new MilkDataIO(p))
}

// ******************************
//             FULL
// ******************************
class MilkIO(p: MilkParams) extends Bundle {
  val req = new MilkReqIO(p)
  val write = new MilkDataIO(p)
  val read = Flipped(new MilkDataIO(p))
}

// ******************************
//            MODULES
// ******************************
class MilkNodeBus(nInst: Int) extends Bundle {
  val op = UInt(NODE.NBIT.W)
  val size = UInt(SIZE.NBIT.W)
  val zero = Bool()
  val node = UInt(nInst.W)

  def r: Bool = (op === NODE.R) | (op === NODE.AMO)
  def w: Bool = (op === NODE.W) | (op === NODE.AMO) 
  def a: Bool = (op === NODE.AMO) 
}