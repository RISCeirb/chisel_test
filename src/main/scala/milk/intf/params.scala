/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 12:54:02 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2024-07-12 09:56:19 am                                       *
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
import scala.math._

import herd.common.gen._
import herd.common.mem.axi4.{Axi4Params, Axi4Config}
import herd.common.mem.asram.{ASramParams, ASramConfig}
import herd.common.mem.mig.{MigParams, MigConfig}


// ******************************
//             AXI4
// ******************************
trait MilkAxi4Params extends GenParams
                      with MilkMemParams {
  def pPort: Array[MilkParams]
  
  def isSim: Boolean = pPort(0).isSim

  def readOnly: Boolean = pPort(0).readOnly
  def nHart: Int = pPort(0).nHart
  def nAddrBit: Int = pPort(0).nAddrBit
  def nAddrBase: String
  def nByte: String 
  def nDataByte: Int = pPort(0).nDataByte
  def nDataBit: Int = nDataByte * 8

  def nAxi4Id: Int = 1
  def nDepth: Int

  def pAxi4: Axi4Params = new Axi4Config (
    isSim = pPort(0).isSim,

    nAddrBit = pPort(0).nAddrBit,
    nDataByte = pPort(0).nDataByte,
    nId = 4
  )
}

case class MilkAxi4Config (
  pPort: Array[MilkParams],

  nAddrBase: String,
  nByte: String,

  nDepth: Int
) extends MilkAxi4Params

// ******************************
//             ASRAM
// ******************************
trait MilkASramParams extends GenParams
                      with MilkMemParams {
  def pPort: Array[MilkParams]
  
  def isSim: Boolean = pPort(0).isSim

  def readOnly: Boolean = pPort(0).readOnly
  def nHart: Int = pPort(0).nHart
  def nAddrBit: Int = pPort(0).nAddrBit
  def nAddrBase: String
  def nByte: String 
  def nDataByte: Int = pPort(0).nDataByte
  def nDataBit: Int = nDataByte * 8

  def nASramAddrBit: Int = log2Ceil(BigInt(nByte.substring(1), 16))
  def nASramDataBit: Int
  def nASramReqDepth: Int
  def nASramOpDepth: Int
  def nASramReadDepth: Int

  def pASram: ASramParams = new ASramConfig (
    isSim = isSim,

    nAddrBit = nASramAddrBit,
    nDataBit = nASramDataBit
  )
}

case class MilkASramConfig (
  pPort: Array[MilkParams],

  nAddrBase: String,
  nByte: String,

  nASramDataBit: Int,
  nASramReqDepth: Int,
  nASramOpDepth: Int,
  nASramReadDepth: Int
) extends MilkASramParams

// ******************************
//              MIG
// ******************************
trait MilkMigParams extends GenParams
                      with MilkMemParams {
  def pPort: Array[MilkParams]
  def nPort: Int = pPort.size
  def pMilk: MilkParams = MILK.node(pPort)
  
  def isSim: Boolean = pMilk.isSim

  def readOnly: Boolean = pMilk.readOnly
  def nHart: Int = pMilk.nHart
  def nAddrBit: Int = log2Ceil(BigInt(nByte.substring(1), 16))
  def nAddrBase: String
  def nByte: String 
  def nDataByte: Int
  def nDataBit: Int = nDataByte * 8

  def nReqFifoDepth: Int
  def nAckFifoDepth: Int
  def nReadFifoDepth: Int

  def pMig: MigParams = new MigConfig (
    nAddrBit = nAddrBit,
    nDataByte = nDataByte
  )
}

case class MilkMigConfig (
  pPort: Array[MilkParams],

  nAddrBase: String,
  nByte: String,
  nDataByte: Int,

  nReqFifoDepth: Int,
  nAckFifoDepth: Int,
  nReadFifoDepth: Int
) extends MilkMigParams