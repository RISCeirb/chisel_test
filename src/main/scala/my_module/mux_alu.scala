package template.my_module

import chisel3._
import chisel3.util._
import _root_.circt.stage.{ChiselStage}


class MUX_ALU extends Module {
  val io = IO(new Bundle {
    val register2     = Input(UInt(32.W))
    val instruction   = Input(UInt(32.W))
    val data_mux      = Output(UInt(32.W))
    val uins          = Input(new Microinstruction)
  })

  // Extraction des valeurs immédiates avec extension de signe
  val signExtended_i = Mux(io.instruction(31) === 1.U, Cat(Fill(20, 1.U), io.instruction(31, 20)), Cat(Fill(20, 0.U), io.instruction(31, 20)))
  val signExtended_u = Cat(io.instruction(31, 12), Fill(12, 0.U))
  val signExtended_s = Mux(io.instruction(31) === 1.U, Cat(Fill(20, 1.U), io.instruction(31, 25), io.instruction(11, 7)), 
                                                  Cat(Fill(20, 0.U), io.instruction(31, 25), io.instruction(11, 7)))
  val signExtended_j = Mux(io.instruction(31) === 1.U, Cat(Fill(12, 1.U), io.instruction(31), io.instruction(19, 12), io.instruction(20), io.instruction(30, 21)), 
                                                  Cat(Fill(12, 0.U), io.instruction(31), io.instruction(19, 12), io.instruction(20), io.instruction(30, 21)))

  // Sélection du MUX en fonction du format d'instruction
  io.data_mux := 0.U // Valeur par défaut

  switch(io.uins.format) {
    is(InstructionFormat.R) { io.data_mux := io.register2 }    // R-type: rs2
    is(InstructionFormat.I) { io.data_mux := signExtended_i }  // I-type: immediate
    is(InstructionFormat.U) { io.data_mux := signExtended_u }  // U-type: upper immediate
    is(InstructionFormat.B) { io.data_mux := io.register2 }    // B-type: rs2 (pour comparaison)
    is(InstructionFormat.S) { io.data_mux := signExtended_s }  // S-type: store immediate
  }
}

object MUX_ALU extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new MUX_ALU(),
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

