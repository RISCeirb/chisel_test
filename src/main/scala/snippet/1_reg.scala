package template.snippet

import chisel3._
import chisel3.util._
import _root_.circt.stage.{ChiselStage}


class RegModule extends Module {
  val io = IO(new Bundle {
    val i_in = Input(UInt(32.W))    

    val o_out0 = Output(UInt(32.W))  
    val o_out1 = Output(UInt(32.W))  
  })  

  val r_reg0 = RegInit(0.U(32.W)) // 32-bit register with reset to 0
  val r_reg1 = Reg(UInt(32.W))    // 32-bit register without reset

  r_reg0 := io.i_in 
  r_reg1 := io.i_in

  io.o_out0 := r_reg0
  io.o_out1 := r_reg1
}

object RegModule extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new RegModule(),
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