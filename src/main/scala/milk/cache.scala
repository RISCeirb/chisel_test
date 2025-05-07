/*
 * File: cache.scala                                                           *
 * Created Date: 2025-02-20 02:20:33 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2025-02-20 02:58:45 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * Email: mathieu.escouteloup@ims-bordeaux.com                                 *
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


class MilkCacheMuxReq (p: MilkParams) extends Module {
  val io = IO(new Bundle {
    val b_m = Flipped(new MilkReqIO(p))

    val b_u = new MilkReqIO(p)
    val b_c = new MilkReqIO(p)
    
    val b_node = new GenRVIO(p, new MilkNodeBus(2), UInt(0.W))
  })

  // ******************************
  //          MULTIPLEXER
  // ******************************
  io.b_node.hart := io.b_m.hart
  if (!p.readOnly) io.b_node.ctrl.get.op := NODE.fromMilk(p, io.b_m.ctrl.op)
  io.b_node.ctrl.get.size := io.b_m.ctrl.size
  io.b_node.ctrl.get.zero := false.B

  when (io.b_m.ctrl.cache) {
    io.b_c <> io.b_m
    io.b_c.valid := io.b_m.valid & io.b_node.ready
    io.b_m.ready := io.b_c.ready & io.b_node.ready

    io.b_node.valid := io.b_m.valid & io.b_c.ready
    io.b_node.ctrl.get.node := 1.U

    io.b_u := DontCare
    io.b_u.valid := false.B
  }.otherwise {
    io.b_u <> io.b_m
    io.b_u.valid := io.b_m.valid & io.b_node.ready
    io.b_m.ready := io.b_u.ready & io.b_node.ready

    io.b_node.valid := io.b_m.valid & io.b_u.ready
    io.b_node.ctrl.get.node := 0.U

    io.b_c := DontCare
    io.b_c.valid := false.B
  }

  // ******************************
  //           SIMULATION
  // ******************************
  if (p.isSim) {
    dontTouch(io.b_node)
  } 
}

class MilkCacheMuxWrite (p: MilkParams) extends Module {
  val io = IO(new Bundle {
    val b_m = Flipped(new MilkDataIO(p))

    val b_u = new MilkDataIO(p)
    val b_c = new MilkDataIO(p)

    val b_node = Flipped(new GenRVIO(p, new MilkNodeBus(2), UInt(0.W)))
  })

  // ******************************
  //          MULTIPLEXER
  // ******************************
  when (io.b_node.ctrl.get.node === 1.U) {
    io.b_c <> io.b_m
    io.b_c.valid := io.b_m.valid & io.b_node.valid
    io.b_m.ready := io.b_c.ready & io.b_node.valid

    io.b_node.ready := io.b_m.valid & io.b_c.ready

    io.b_u := DontCare
    io.b_u.valid := false.B
  }.otherwise {
    io.b_u <> io.b_m
    io.b_u.valid := io.b_m.valid & io.b_node.valid
    io.b_m.ready := io.b_u.ready & io.b_node.valid

    io.b_node.ready := io.b_m.valid & io.b_u.ready

    io.b_c := DontCare
    io.b_c.valid := false.B
  }

  // ******************************
  //           SIMULATION
  // ******************************
  if (p.isSim) {
    
  } 
}

class MilkCacheMuxRead (p: MilkParams) extends Module {
  val io = IO(new Bundle {
    val b_m = new MilkDataIO(p)

    val b_u = Flipped(new MilkDataIO(p))
    val b_c = Flipped(new MilkDataIO(p))

    val b_node = Flipped(new GenRVIO(p, new MilkNodeBus(2), UInt(0.W)))
  })

  // ******************************
  //          MULTIPLEXER
  // ******************************
  when (io.b_node.ctrl.get.node === 1.U) {
    io.b_m <> io.b_c
    io.b_m.valid := io.b_c.valid & io.b_node.valid
    io.b_c.ready := io.b_m.ready & io.b_node.valid

    io.b_node.ready := io.b_m.ready & io.b_c.valid

    io.b_u := DontCare
    io.b_u.ready := false.B
  }.otherwise {
    io.b_m <> io.b_u
    io.b_m.valid := io.b_u.valid & io.b_node.valid
    io.b_u.ready := io.b_m.ready & io.b_node.valid

    io.b_node.ready := io.b_m.ready & io.b_u.valid

    io.b_c := DontCare
    io.b_c.ready := false.B
  }

  // ******************************
  //           SIMULATION
  // ******************************
  if (p.isSim) {

  } 
}

class MilkCacheMux (p: MilkParams, nDepth: Int) extends Module {
  val io = IO(new Bundle {   
    val b_m = Flipped(new MilkIO(p))

    val b_u = new MilkIO(p)
    val b_c = new MilkIO(p)
  })

  val m_req = Module(new MilkCacheMuxReq(p))
  val m_node = Module(new GenFifo(p, new MilkNodeBus(2), UInt(0.W), 4, nDepth, 1, 1, true))
  val m_write = Module(new MilkCacheMuxWrite(p))
  val m_read = Module(new MilkCacheMuxRead(p))  

  val init_done = Wire(Vec(2, Bool()))

  init_done(0) := 0.B
  init_done(1) := 0.B

  val r_done = RegInit(init_done)
  
  // ******************************
  //             MASTER
  // ******************************
  m_req.io.b_m <> io.b_m.req
  m_write.io.b_m <> io.b_m.write
  m_read.io.b_m <> io.b_m.read

  // ******************************
  //            UNCACHE
  // ******************************
  m_req.io.b_u <> io.b_u.req
  m_write.io.b_u <> io.b_u.write
  m_read.io.b_u <> io.b_u.read

  // ******************************
  //             CACHE
  // ******************************
  m_req.io.b_c <> io.b_c.req
  m_write.io.b_c <> io.b_c.write
  m_read.io.b_c <> io.b_c.read

  // ******************************
  //             NODE
  // ******************************
  for (h <- 0 until p.nHart) {
    m_node.io.i_flush(h) := false.B
  }

  m_node.io.b_in(0) <> m_req.io.b_node
  m_write.io.b_node <> m_node.io.b_out(0)
  m_read.io.b_node <> m_node.io.b_out(0)

  m_write.io.b_node.valid := m_node.io.b_out(0).valid & m_node.io.b_out(0).ctrl.get.w & ~r_done(0)
  m_read.io.b_node.valid := m_node.io.b_out(0).valid & m_node.io.b_out(0).ctrl.get.r & ~r_done(1)
  m_node.io.b_out(0).ready := (~m_node.io.b_out(0).ctrl.get.w | r_done(0) | m_write.io.b_node.ready) & (~m_node.io.b_out(0).ctrl.get.r | r_done(1) | m_read.io.b_node.ready)

  when (m_node.io.b_out(0).valid & m_node.io.b_out(0).ctrl.get.a) {
    when (r_done(1) | m_read.io.b_node.ready) {
      r_done(0) := false.B
    }.otherwise {
      r_done(0) := r_done(0) | m_write.io.b_node.ready
    }
    when (r_done(0) | m_write.io.b_node.ready) {
      r_done(1) := false.B
    }.otherwise {
      r_done(1) := r_done(1) | m_read.io.b_node.ready
    }
  }.otherwise {
    r_done(0) := false.B
    r_done(1) := false.B
  }  

  // ******************************
  //           SIMULATION
  // ******************************
  if (p.isSim) {

  } 
}

object MilkCacheMuxReq extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkCacheMuxReq(MilkConfig0),
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

object MilkCacheMuxWrite extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkCacheMuxWrite(MilkConfig0),
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

object MilkCacheMuxRead extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkCacheMuxRead(MilkConfig0),
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

object MilkCacheMux extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkCacheMux(MilkConfig0, 4),
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