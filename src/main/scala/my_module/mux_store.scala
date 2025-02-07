package template.my_module

import chisel3._
import chisel3.util._
import _root_.circt.stage.{ChiselStage}

class MUX_STORE extends Module {
  val io = IO(new Bundle {
    val rs2            = Input(UInt(32.W))
    val data_to_mem    = Output(UInt(32.W))
    val uins           = Input(new Microinstruction)
  })

  // Define signals for zero-extended values
  val zeroExtended_16 = Wire(UInt(32.W))
  val zeroExtended_8  = Wire(UInt(32.W))

  // Zero extend the low 16 bits of instruction
  zeroExtended_16 := Cat("h0000".U(16.W), io.rs2(15, 0))

  // Zero extend the low 8 bits of instruction
  zeroExtended_8 := Cat("h000000".U(24.W), io.rs2(7, 0))

  io.data_to_mem := io.rs2
  switch(io.uins.instruction) {
    is(InstructionType.SW) { io.data_to_mem := io.rs2 }
    is(InstructionType.SH) { io.data_to_mem := zeroExtended_16 }
    is(InstructionType.SB) { io.data_to_mem := zeroExtended_8 }
  }
}

object MUX_STORE extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MUX_STORE(),
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
