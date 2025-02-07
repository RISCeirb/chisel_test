package template.empty

import chisel3._
import chisel3.util._
import _root_.circt.stage.{ChiselStage}


class Empty(nBit: Int) extends Module {
  val io = IO(new Bundle {
    val i_in = Input(UInt(nBit.W))    
    val o_out = Output(UInt(nBit.W))  
  })  

  io.o_out := io.i_in
}

object Empty extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new Empty(4),
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