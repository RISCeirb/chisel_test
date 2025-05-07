/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 12:54:02 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2025-04-26 04:10:43 pm                                       *
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
import scala.math._

import herd.common.gen._
import herd.common.mem.axi4.{Axi4Params, Axi4Config}


// ******************************
//              BASE
// ******************************
trait MilkBaseParams extends GenParams {
  def isSim: Boolean

  def nHart: Int
}

case class MilkBaseConfig (
  isSim: Boolean,

  nHart: Int
) extends MilkBaseParams

// ******************************
//              REQ
// ******************************
trait MilkReqParams extends MilkBaseParams {
  def isSim: Boolean  

  def readOnly: Boolean
  def nHart: Int
  def nAddrBit: Int
  def useAmo: Boolean
  def nOpBit: Int = {
    if (useAmo) {
      return OP.NBIT
    } else {
      return 1 
    }
  }
}

case class MilkReqConfig (
  isSim: Boolean,  

  readOnly: Boolean,
  nHart: Int,
  nAddrBit: Int,
  useAmo: Boolean
) extends MilkReqParams

// ******************************
//             DATA
// ******************************
trait MilkDataParams extends MilkBaseParams {
  def isSim: Boolean  

  def nHart: Int
  def readOnly: Boolean
  def nDataByte: Int
  def nDataBit: Int = nDataByte * 8
}

case class MilkDataConfig (
  isSim: Boolean,  

  readOnly: Boolean,
  nHart: Int,
  nDataByte: Int
) extends MilkDataParams

// ******************************
//              BUS
// ******************************
trait MilkParams extends MilkReqParams with MilkDataParams {
  def isSim: Boolean  

  def readOnly: Boolean
  def nHart: Int
  def nAddrBit: Int
  def useAmo: Boolean
  def nDataByte: Int
  //def cacheSize :Int
  //def lineSize  :Int

  //def useCoherency: Boolean
}

case class MilkConfig (
  isSim: Boolean,  

  readOnly: Boolean,
  nHart: Int,
  nAddrBit: Int,
  useAmo: Boolean,
  nDataByte: Int,
  //useCoherency: Boolean
) extends MilkParams

// ******************************
//            MEMORY
// ******************************
trait MilkMemParams {
  def pPort: Array[MilkParams]

  def nAddrBase: String
  def nByte: String
}

case class MilkMemConfig (
  pPort: Array[MilkParams],
  
  nAddrBase: String,
  nByte: String
) extends MilkMemParams

// ******************************
//          INTERCONNECT
// ******************************
trait MilkCrossbarParams extends GenParams {
  def pMaster: Array[MilkParams]
  def nMaster: Int = pMaster.size
  def useRound: Boolean

  def useMem: Boolean
  def pMem: Array[MilkMemParams]
  def nMem: Int = pMem.size
  def nDefault: Int 
  def nBus: Int
  def useDirect: Boolean
  def nSlave: Int = {
    if (useMem) {
      return nMem + nDefault
    } else {
      return nBus
    }
  }
  def pSlave: MilkParams = MILK.node(pMaster)
  
  def isSim: Boolean  

  def readOnly: Boolean = pSlave.readOnly
  def nHart: Int = pSlave.nHart
  def nAddrBit: Int = pSlave.nAddrBit
  def useAmo: Boolean = pSlave.useAmo
  def nOpBit: Int = pSlave.nOpBit
  def nDataByte: Int = pSlave.nDataByte
  def nDataBit: Int = nDataByte * 8

  def nDepth: Int
}

case class MilkCrossbarConfig (
  pMaster: Array[MilkParams],
  useRound: Boolean,
  useMem: Boolean,
  pMem: Array[MilkMemParams],
  nDefault: Int,
  nBus: Int,
  
  isSim: Boolean,  
  
  nDepth: Int,
  useDirect: Boolean
) extends MilkCrossbarParams
