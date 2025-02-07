/*
 * File: 5_params.scala                                                        *
 * Created Date: 2025-02-04 10:55:41 am                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2025-02-04 11:05:34 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * Email: mathieu.escouteloup@ims-bordeaux.com                                 *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2025 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package template.snippet

import chisel3._
import chisel3.util._
import _root_.circt.stage.{ChiselStage}

trait ArgParams {
  val nBit: Int
  val nData: Int
}

case class ArgConfig (
  nBit: Int,
  nData: Int
) extends ArgParams

object ArgConfigBase extends ArgConfig (
  nBit = 16,
  nData = 4
)

class ArgModule(p: ArgParams) extends Module {
  val io = IO(new Bundle {
    val i_op1 = Input(Vec(p.nData, UInt(p.nBit.W)))
    val i_op2 = Input(Vec(p.nData, UInt(p.nBit.W)))

    val o_res = Output(Vec(p.nData, UInt(p.nBit.W)))
  })  

  for (d <- 0 until p.nData) {
    io.o_res(d) := io.i_op1(d) + io.i_op2(d)
  }
}

object ArgModule extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new ArgModule(ArgConfigBase),
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