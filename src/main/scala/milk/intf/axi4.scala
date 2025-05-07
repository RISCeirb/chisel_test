/*
 * File: axi4.scala                                                            *
 * Created Date: 2023-02-25 12:54:02 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2024-07-12 10:04:40 am                                       *
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
import herd.common.mem.axi4.{Axi4IO, Axi4ReadAddrIO, Axi4WriteAddrIO, Axi4ReadDataIO, Axi4WriteDataBus, Axi4WriteDataIO, Axi4WriteRespIO}
import herd.common.mem.axi4.{SIZE => AXI4SIZE, BURST => AXI4BURST, PROT => AXI4PROT, CACHE => AXI4CACHE, QOS => AXI4QOS}


class MilkAxi4Req (p: MilkAxi4Params) extends Module {  
  val io = IO(new Bundle {
    val b_milk = Flipped(new MilkReqIO(p.pPort(0)))

    val b_raxi4 = new Axi4ReadAddrIO(p.pAxi4)
    val b_waxi4 = new Axi4WriteAddrIO(p.pAxi4)

    val b_idnode = new GenRVIO(p, new MilkNodeBus(p.nAxi4Id), UInt(0.W))
  })

  // ******************************
  //         READY / VALID
  // ******************************
  io.b_milk.ready := io.b_idnode.ready & (io.b_milk.ctrl.ra & io.b_raxi4.ready) | (io.b_milk.ctrl.wa & io.b_waxi4.ready)

  io.b_idnode.valid := io.b_milk.valid & (io.b_milk.ctrl.ra & io.b_raxi4.ready) | (io.b_milk.ctrl.wa & io.b_waxi4.ready)
  io.b_idnode.hart := io.b_milk.hart
  io.b_idnode.ctrl.get.op := NODE.fromMilk(p.pPort(0), io.b_milk.ctrl.op)
  io.b_idnode.ctrl.get.size := io.b_milk.ctrl.size
  io.b_idnode.ctrl.get.node := 0.U
  io.b_idnode.ctrl.get.zero := 0.B

  io.b_raxi4.valid := io.b_milk.valid & io.b_idnode.ready & io.b_milk.ctrl.ra
  io.b_waxi4.valid := io.b_milk.valid & io.b_idnode.ready & io.b_milk.ctrl.wa  

  // ******************************
  //            AXI4
  // ******************************
  io.b_raxi4.ctrl.id := 0.U
  io.b_raxi4.ctrl.addr := io.b_milk.ctrl.addr
  io.b_raxi4.ctrl.len := 0.U
  io.b_raxi4.ctrl.size := AXI4SIZE.fromMilk(io.b_milk.ctrl.size)
  io.b_raxi4.ctrl.burst := AXI4BURST.NONE
  io.b_raxi4.ctrl.lock := 0.B
  io.b_raxi4.ctrl.cache := AXI4CACHE.NONE
  io.b_raxi4.ctrl.prot := AXI4PROT.NONE
  io.b_raxi4.ctrl.qos := AXI4QOS.NONE

  io.b_waxi4.ctrl.id := 0.U
  io.b_waxi4.ctrl.addr := io.b_milk.ctrl.addr 
  io.b_waxi4.ctrl.len := 0.U
  io.b_waxi4.ctrl.size := AXI4SIZE.fromMilk(io.b_milk.ctrl.size)
  io.b_waxi4.ctrl.burst := AXI4BURST.NONE
  io.b_waxi4.ctrl.lock := 0.B
  io.b_waxi4.ctrl.cache := AXI4CACHE.NONE
  io.b_waxi4.ctrl.prot := AXI4PROT.NONE
  io.b_waxi4.ctrl.qos := AXI4QOS.NONE  

  // ******************************
  //           SIMULATION
  // ******************************
  if (p.isSim) {
    dontTouch(io.b_idnode)
  } 
}

class MilkAxi4Read (p: MilkAxi4Params) extends Module {  
  val io = IO(new Bundle {
    val b_milk = new MilkDataIO(p.pPort(0))

    val b_idnode = Flipped(new GenRVIO(p, new MilkNodeBus(p.nAxi4Id), UInt(0.W)))

    val b_daxi4 = Flipped(new Axi4ReadDataIO(p.pAxi4))
  })

  // ******************************
  //             DATA
  // ******************************
  io.b_milk.valid := io.b_idnode.valid & io.b_idnode.ctrl.get.r & io.b_daxi4.valid
  io.b_milk.hart := io.b_idnode.hart
  io.b_idnode.ready := io.b_idnode.ctrl.get.r & io.b_milk.ready & io.b_daxi4.valid

  io.b_daxi4.ready := io.b_idnode.valid & io.b_idnode.ctrl.get.r & io.b_milk.ready  

  io.b_milk.data := io.b_daxi4.data

  // ******************************
  //           SIMULATION
  // ******************************
  if (p.isSim) {
    dontTouch(io.b_idnode)
  } 
}

class MilkAxi4Write (p: MilkAxi4Params) extends Module {  
  val io = IO(new Bundle {
    val b_milk = Flipped(new MilkDataIO(p.pPort(0)))

    val b_idnode = Flipped(new GenRVIO(p, new MilkNodeBus(p.nAxi4Id), UInt(0.W)))

    val b_daxi4 = new Axi4WriteDataIO(p.pAxi4)
    val b_baxi4 = Flipped(new Axi4WriteRespIO(p.pAxi4))
  })

  val m_daxi4 = Module(new GenFifo(p, new Axi4WriteDataBus(p.pAxi4), UInt((p.nDataByte * 8).W), 4, 2, 1, 1, true))

  // ******************************
  //             DATA
  // ******************************
  // ------------------------------
  //             FIFO
  // ------------------------------
  io.b_milk.ready := io.b_idnode.valid & io.b_idnode.ctrl.get.w & m_daxi4.io.b_in(0).ready
  io.b_idnode.ready := io.b_idnode.ctrl.get.w & io.b_milk.valid & m_daxi4.io.b_in(0).ready

  m_daxi4.io.b_in(0).valid := io.b_idnode.valid & io.b_idnode.ctrl.get.w & io.b_milk.valid
  m_daxi4.io.b_in(0).hart := DontCare
  m_daxi4.io.b_in(0).ctrl.get.last := true.B
  m_daxi4.io.b_in(0).ctrl.get.strb := SIZE.toMask(p.nDataByte, io.b_idnode.ctrl.get.size)  
  m_daxi4.io.b_in(0).data.get := io.b_milk.data

  // ------------------------------
  //             PORT
  // ------------------------------
  for (h <- 0 until p.nHart) {
    m_daxi4.io.i_flush(h) := false.B
  }

  io.b_daxi4.valid := m_daxi4.io.b_out(0).valid
  io.b_daxi4.ctrl := m_daxi4.io.b_out(0).ctrl.get
  io.b_daxi4.data := m_daxi4.io.b_out(0).data.get
  m_daxi4.io.b_out(0).ready := io.b_daxi4.ready

  // ******************************
  //             RESP
  // ******************************
  io.b_baxi4.ready := true.B

  // ******************************
  //           SIMULATION
  // ******************************
  if (p.isSim) {
    dontTouch(io.b_idnode)
  } 
}

class MilkAxi4 (p: MilkAxi4Params) extends Module {  
  val io = IO(new Bundle {
    val b_milk = Flipped(new MilkIO(p.pPort(0)))

    val b_axi4 = new Axi4IO(p.pAxi4)
  })

  val m_req = Module(new MilkAxi4Req(p))
  val m_idnode = Module(new GenFifo(p, new MilkNodeBus(p.nAxi4Id), UInt(0.W), 3, p.nDepth, 1, 1, true))
  val m_write = Module(new MilkAxi4Write(p))
  val m_read = Module(new MilkAxi4Read(p))  

  val init_iddone = Wire(Vec(2, Bool()))

  init_iddone(0) := 0.B
  init_iddone(1) := 0.B   

  val r_iddone = RegInit(init_iddone)
  
  // ******************************
  //             MILK
  // ******************************
  m_req.io.b_milk <> io.b_milk.req
  m_write.io.b_milk <> io.b_milk.write
  m_read.io.b_milk <> io.b_milk.read  

  // ******************************
  //             AXI4
  // ******************************
  m_req.io.b_raxi4 <> io.b_axi4.ar
  m_req.io.b_waxi4 <> io.b_axi4.aw
  m_write.io.b_daxi4 <> io.b_axi4.dw
  m_write.io.b_baxi4 <> io.b_axi4.bw
  m_read.io.b_daxi4 <> io.b_axi4.dr   

  // ******************************
  //              NODE
  // ******************************
  m_idnode.io.b_in(0) <> m_req.io.b_idnode
  m_write.io.b_idnode <> m_idnode.io.b_out(0)
  m_read.io.b_idnode <> m_idnode.io.b_out(0)

  m_write.io.b_idnode.valid := m_idnode.io.b_out(0).valid & m_idnode.io.b_out(0).ctrl.get.w & ~r_iddone(0)
  m_read.io.b_idnode.valid := m_idnode.io.b_out(0).valid & m_idnode.io.b_out(0).ctrl.get.r & ~r_iddone(1)
  m_idnode.io.b_out(0).ready := (~m_idnode.io.b_out(0).ctrl.get.w | r_iddone(0) | m_write.io.b_idnode.ready) & (~m_idnode.io.b_out(0).ctrl.get.r | r_iddone(1) | m_read.io.b_idnode.ready)

  when (m_idnode.io.b_out(0).valid & m_idnode.io.b_out(0).ctrl.get.a) {
    when (r_iddone(1) | m_read.io.b_idnode.ready) {
      r_iddone(0) := false.B
    }.otherwise {
      r_iddone(0) := r_iddone(0) | m_write.io.b_idnode.ready
    }
    when (r_iddone(0) | m_write.io.b_idnode.ready) {
      r_iddone(1) := false.B
    }.otherwise {
      r_iddone(1) := r_iddone(1) | m_read.io.b_idnode.ready
    }
  }.otherwise {
    r_iddone(0) := false.B
    r_iddone(1) := false.B
  }   

  // ******************************
  //            REPORT
  // ******************************
  def report (): Unit = {
    println("------------------------------")
    println("Interface: AXI4")
    println("Data size: " + p.nDataBit)
    println("Address base: 0x" + p.nAddrBase.substring(1))
    println("Memory size: 0x" + p.nByte.substring(1))
    println("------------------------------")
  }

  // ******************************
  //          SIMULATION
  // ******************************  
  if (p.isSim) {
    
  }
}

object MilkAxi4Req extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkAxi4Req(MilkAxi4ConfigBase),
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

object MilkAxi4Read extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkAxi4Read(MilkAxi4ConfigBase),
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

object MilkAxi4Write extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkAxi4Write(MilkAxi4ConfigBase),
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

object MilkAxi4 extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MilkAxi4(MilkAxi4ConfigBase),
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

