package template.snippet

import chisel3._
import chisel3.util._
import _root_.circt.stage.{ChiselStage}


class IOBundle extends Bundle {
  val ready = Input(Bool())
  val valid = Output(Bool())
  val data = Output(UInt(8.W))
}

class IOModule extends Module {
  val io = IO(new Bundle {
    val b_in = Flipped(new IOBundle())  // Change directions

    val b_out = new IOBundle()          // Keep directions
  })  

  val init_reg = Wire(new BusBundle())  // Register must not have directions (or only Output)

  init_reg := DontCare           
  init_reg.valid := false.B      

  val r_reg = RegInit(init_reg)  

  when (~r_reg.valid | io.b_out.ready) {
    r_reg.valid := io.b_in.valid
    r_reg.data := io.b_in.data
  }

  io.b_in.ready := ~r_reg.valid | io.b_out.ready
  io.b_out.valid := r_reg.valid
  io.b_out.data := r_reg.data
}

object IOModule extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new IOModule(),
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