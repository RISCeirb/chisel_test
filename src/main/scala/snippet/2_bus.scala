package template.snippet

import chisel3._
import chisel3.util._
import _root_.circt.stage.{ChiselStage}


class BusBundle extends Bundle {
  val valid = Bool()
  val data = UInt(8.W)
}

class BundleModule extends Module {
  val io = IO(new Bundle {
    val i_in = Input(new BusBundle())    

    val o_out0 = Output(new BusBundle())  
    val o_out1 = Output(new BusBundle())  
  })  

  val init_reg0 = Wire(new BusBundle())

  init_reg0 := DontCare             // Indicate that value is not important
  init_reg0.valid := false.B        // Force valid signal to low (similar to 0.B)

  val r_reg0 = RegInit(init_reg0)   // BusBundle register with reset to the value of init_reg0
  val r_reg1 = Reg(new BusBundle)   // BusBundle register without reset

  r_reg0 := io.i_in 
  r_reg1 := io.i_in

  io.o_out0 := r_reg0
  io.o_out1 := r_reg1
}

object BundleModule extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new BundleModule(),
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