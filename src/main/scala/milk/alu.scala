/*
 * File: alu.scala                                                             *
 * Created Date: 2023-02-25 12:54:02 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2024-01-17 04:42:19 pm                                       *
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


class Alu (p: MilkParams) extends Module {
  import herd.common.mem.milk.AMO._

  val io = IO(new Bundle {
    val b_req = Flipped(new GenRVIO(p, UInt(AMO.NBIT.W), Vec(2, UInt(p.nDataBit.W))))
    val b_ack = new GenRVIO(p, UInt(0.W), UInt(p.nDataBit.W))
  })  

  // ******************************
  //             INPUT
  // ******************************
  io.b_req.ready := io.b_ack.ready

  // ******************************
  //              ALU
  // ******************************
  val w_res = Wire(UInt(p.nDataBit.W))
  
  w_res := DontCare
  switch (io.b_req.ctrl.get) {
    is (SWAP) {
      w_res := io.b_req.data.get(0)
    }
    is (ADD) {
      w_res := io.b_req.data.get(0) + io.b_req.data.get(1)
    }
    is (AND) {
      w_res := io.b_req.data.get(0) & io.b_req.data.get(1)
    }
    is (OR) {
      w_res := io.b_req.data.get(0) | io.b_req.data.get(1)
    }
    is (XOR) {
      w_res := io.b_req.data.get(0) ^ io.b_req.data.get(1)
    }
    is (MAXU) {
      w_res := Mux((io.b_req.data.get(0) > io.b_req.data.get(1)), io.b_req.data.get(0), io.b_req.data.get(1))
    }
    is (MAX) {
      w_res := Mux(((io.b_req.data.get(0)).asSInt > (io.b_req.data.get(1)).asSInt), io.b_req.data.get(0), io.b_req.data.get(1))
    }
    is (MINU) {
      w_res := Mux((io.b_req.data.get(0) < io.b_req.data.get(1)), io.b_req.data.get(0), io.b_req.data.get(1))
    }
    is (MIN) {
      w_res := Mux(((io.b_req.data.get(0)).asSInt < (io.b_req.data.get(1)).asSInt), io.b_req.data.get(0), io.b_req.data.get(1))
    }
  }

  // ******************************
  //             OUTPUT
  // ******************************
  io.b_ack.valid := io.b_req.valid
  io.b_ack.hart := io.b_req.hart
  io.b_ack.data.get := w_res

  // ******************************
  //           SIMULATION
  // ******************************
  if (p.isSim) {

  }
}

object Alu extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new Alu(MilkConfig6),
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
