package template.snippet

import chisel3._
import chisel3.util._
import _root_.circt.stage.{ChiselStage}


class BaseModule extends Module {
  val io = IO(new Bundle {
    val i_in = Input(UInt(32.W))    
    val o_out = Output(UInt(32.W))  
  })  

  io.o_out := io.i_in
}

object BaseModule extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new BaseModule(),
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