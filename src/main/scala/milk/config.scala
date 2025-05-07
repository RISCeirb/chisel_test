/*
 * File: config.scala                                                          *
 * Created Date: 2023-02-25 12:54:02 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2025-04-26 04:08:26 pm                                       *
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


// ******************************
//              BUS
// ******************************
object MilkConfig0 extends MilkConfig (
  isSim = true,  

  readOnly = false,
  nHart = 1,
  nAddrBit = 32,
  useAmo = false,
  nDataByte = 4
) 

object MilkConfig1 extends MilkConfig (
  isSim = true,  

  readOnly = false,
  nHart = 1,
  nAddrBit = 32,
  useAmo = false,
  nDataByte = 8
)

object MilkConfig2 extends MilkConfig (
  isSim = true,  

  readOnly = false,
  nHart = 1,
  nAddrBit = 32,
  useAmo = true,
  nDataByte = 8
)

object MilkConfig5 extends MilkConfig (
  isSim = true,  

  readOnly = false,
  nHart = 2,
  nAddrBit = 32,
  useAmo = false,
  nDataByte = 4
)

object MilkConfig6 extends MilkConfig (
  isSim = true,  

  readOnly = true,
  nHart = 2,
  nAddrBit = 32,
  useAmo = false,
  nDataByte = 4
)

object MilkConfig7 extends MilkConfig (
  isSim = true,  

  readOnly = false,
  nHart = 2,
  nAddrBit = 64,
  useAmo = false,
  nDataByte = 8
)

// ******************************
//            MEMORY
// ******************************
object MilkMemConfig0 extends MilkMemConfig (
  pPort = Array(MilkConfig6),
  nAddrBase = "h00",
  nByte = "h10"
)

object MilkMemConfig1 extends MilkMemConfig (
  pPort = Array(MilkConfig6),
  nAddrBase = "h10",
  nByte = "h30"
)

object MilkMemConfig2 extends MilkMemConfig (
  pPort = Array(MilkConfig6),
  nAddrBase = "h40",
  nByte = "h10"
)

// ******************************
//          INTERCONNECT
// ******************************
object MilkCrossbarConfigBase extends MilkCrossbarConfig (
  pMaster = Array(MilkConfig5, MilkConfig5, MilkConfig5),
  useRound = true,
  useMem = false,
  pMem = Array(MilkMemConfig0, MilkMemConfig1),
  nDefault = 1,
  nBus = 2,
  
  isSim = true,
  
  nDepth = 2,
  useDirect = false
)