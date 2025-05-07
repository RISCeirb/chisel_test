/*
 * File: config.scala                                                          *
 * Created Date: 2023-02-25 12:54:02 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2024-09-05 01:49:17 pm                                       *
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


// ******************************
//             AXI4
// ******************************
object MilkAxi4ConfigBase extends MilkAxi4Config (
  pPort = Array(MilkConfig5),

  nAddrBase = "h80000000",
  nByte = "h00080000",

  nDepth = 4
)

// ******************************
//             ASRAM
// ******************************
object MilkASramConfigBase extends MilkASramConfig (
  pPort = Array(MilkConfig1),

  nAddrBase = "h00000000",
  nByte = "h00080000",

  nASramDataBit = 8,
  nASramReqDepth = 2,
  nASramOpDepth = 2,
  nASramReadDepth = 2
)

// ******************************
//              MIG
// ******************************
object MilkMigConfigBase extends MilkMigConfig (
  pPort = Array(MilkConfig5, MilkConfig5),

  nAddrBase = "h00000000",
  nByte = "h10000000",
  nDataByte = 8,

  nReqFifoDepth = 4,
  nAckFifoDepth = 4,
  nReadFifoDepth = 4
)