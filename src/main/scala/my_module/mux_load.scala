package template.my_module

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class MUX_LOAD extends Module {
  val io = IO(new Bundle {
    val mem_data   = Input(UInt(32.W))
    val result_ALU = Input(UInt(32.W))
    val writeData  = Output(UInt(32.W))
    val uins       = Input(new Microinstruction)
  })

  // Définition des signaux pour les extensions de zéro et de signe
  val zeroExtended_16 = Wire(UInt(32.W))
  val signExtended_16 = Wire(UInt(32.W))
  val zeroExtended_8  = Wire(UInt(32.W))
  val signExtended_8  = Wire(UInt(32.W))

  // Extension de signe des 16 bits inférieurs
  signExtended_16 := Mux(io.mem_data(15), Cat(Fill(16, 1.U), io.mem_data(15, 0)), Cat(Fill(16, 0.U), io.mem_data(15, 0)))

  // Extension de signe des 8 bits inférieurs
  signExtended_8 := Mux(io.mem_data(7), Cat(Fill(24, 1.U), io.mem_data(7, 0)), Cat(Fill(24, 0.U), io.mem_data(7, 0)))

  // Extension de zéro des 16 bits inférieurs
  zeroExtended_16 := Cat(Fill(16, 0.U), io.mem_data(15, 0))

  // Extension de zéro des 8 bits inférieurs
  zeroExtended_8 := Cat(Fill(24, 0.U), io.mem_data(7, 0))

  // Sélection de writeData en fonction de la micro-instruction
  io.writeData := io.result_ALU // Valeur par défaut

  when(io.uins.MemToReg === 1.U) {
    switch(io.uins.instruction) {
      is(InstructionType.LW)  { io.writeData := io.mem_data }
      is(InstructionType.LH)  { io.writeData := signExtended_16 }
      is(InstructionType.LHU) { io.writeData := zeroExtended_16 }
      is(InstructionType.LB)  { io.writeData := signExtended_8 }
      is(InstructionType.LBU) { io.writeData := zeroExtended_8 }
    }
  }
}

object MUX_LOAD extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MUX_LOAD(),
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
