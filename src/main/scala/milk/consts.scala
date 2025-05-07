/*
 * File: consts.scala                                                          *
 * Created Date: 2023-02-25 12:54:02 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2025-02-11 03:09:26 pm                                       *
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


object OP {
  def NBIT  = 3
  def X     = 0.U(NBIT.W)

  def R     = 0.U(NBIT.W)
  def W     = 1.U(NBIT.W)
  def LR    = 2.U(NBIT.W)
  def SC    = 3.U(NBIT.W)
  def AMO   = 4.U(NBIT.W)
}

object AMO {
  def NBIT  = 4
  def X     = 0.U(NBIT.W)

  def SWAP  = 0.U(NBIT.W)
  def ADD   = 1.U(NBIT.W)
  def AND   = 2.U(NBIT.W)
  def OR    = 3.U(NBIT.W)
  def XOR   = 4.U(NBIT.W)
  def MAXU  = 5.U(NBIT.W)
  def MAX   = 6.U(NBIT.W)
  def MINU  = 7.U(NBIT.W)
  def MIN   = 8.U(NBIT.W)
}

object SIZE {
  def NBIT  = 3

  def B0    = 0
  def B1    = 1
  def B2    = 2
  def B4    = 3
  def B8    = 4
  def B16   = 5

  def toSize (nByte: Int) = {
    if (nByte >= 16) B16
    else if (nByte >= 8) B8
    else if (nByte >= 4) B4
    else if (nByte >= 2) B2
    else if (nByte >= 1) B1
    else B0
  }

  def toByte(size: UInt): UInt = {
    val w_nbyte = Wire(UInt(5.W))

    w_nbyte := 0.U
    switch (size) {
      is (B1.U)   {w_nbyte := 1.U}
      is (B2.U)   {w_nbyte := 2.U}
      is (B4.U)   {w_nbyte := 4.U}
      is (B8.U)   {w_nbyte := 8.U}
      is (B16.U)  {w_nbyte := 16.U}
    }

    return w_nbyte
  }

  def toMask(nDataByte: Int, size: UInt): UInt = {
    val w_mask = Wire(Vec(nDataByte, Bool())) 

    for (db <- 0 until nDataByte) {
      w_mask(db) := (db.U < toByte(size))
    }

    return w_mask.asUInt
  }
}

object MILK {
  def node (p: Array[MilkParams]) : MilkParams = {
    return new MilkConfig (
      isSim = {
        var tmp: Boolean = p(0).isSim
        for (s <- 1 until p.size) {
          if (p(s).isSim == true) {
            tmp = true
          }
        }
        tmp 
      },
      
      readOnly = {
        var tmp: Boolean = p(0).readOnly
        for (s <- 1 until p.size) {
          if (p(s).readOnly == false) {
            tmp = false
          }
        }
        tmp 
      },
      nHart = {
        var tmp: Int = p(0).nHart
        for (s <- 1 until p.size) {
          if (p(s).nHart > tmp) {
            tmp = p(s).nHart
          }
        }
        tmp
      },
      nAddrBit = {
        var tmp: Int = p(0).nAddrBit
        for (s <- 1 until p.size) {
          if (p(s).nAddrBit > tmp) {
            tmp = p(s).nAddrBit
            println("Warning: all masters have not the same number of address bits.")
          }
        }
        tmp
      },
      useAmo = {
        var tmp: Boolean = p(0).useAmo
        for (s <- 1 until p.size) {
          if (p(s).useAmo) {
            tmp = true
          }
        }
        tmp
      },
      nDataByte = {
        var tmp: Int = p(0).nDataByte
        for (s <- 1 until p.size) {
          if (p(s).nDataByte > tmp) {
            tmp = p(s).nDataByte
          }
        }
        tmp
      }
    )
  }
}

object NODE {
  def NBIT  = 2
  def X     = 0.U(NBIT.W)

  def R     = 0.U(NBIT.W)
  def W     = 1.U(NBIT.W)
  def AMO   = 2.U(NBIT.W)

  def fromMilk(p: MilkReqParams, op: UInt): UInt = {
    val w_node = Wire(UInt(NBIT.W))

    w_node := DontCare

    if (p.useAmo) {
      switch (op) {
        is (OP.R, OP.LR)    {w_node := R}
        is (OP.W)           {w_node := W}
        is (OP.AMO, OP.SC)  {w_node := AMO}
      }
    } else {
      when (op === 1.U) {
        w_node := W
      }.otherwise {
        w_node := R
      }
    }

    return w_node
  }
}
