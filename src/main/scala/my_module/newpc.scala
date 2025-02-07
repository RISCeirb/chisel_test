package template.my_module

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class NEW_PC extends Module {
  val io = IO(new Bundle {
    val rs1          = Input(UInt(32.W))       // rs1 (Register 1) input
    val pc           = Input(UInt(32.W))       // PC input
    val instruction  = Input(UInt(32.W))       // Instruction input
    val new_pc       = Output(UInt(32.W))      // Output for the new calculated PC
    val branch       = Input(Bool())           // Branch signal
    val uins         = Input(new Microinstruction) // Control signals
  })

  // Signals for incremented PC and extended sign values
  val incrementedPC = io.pc + 4.U(32.W) // PC + 4 (next instruction address)

  // Sign extension for different instruction types
  val signExtended_i = Wire(UInt(32.W))
  val signExtended_u = Wire(UInt(32.W))
  val signExtended_s = Wire(UInt(32.W))
  val signExtended_b = Wire(UInt(32.W))
  val signExtended_j = Wire(UInt(32.W))

  // Sign extending the 12 bits for I-type instruction
  signExtended_i := Mux(io.instruction(31), Cat("b111111111111".U(20.W), io.instruction(31, 20)), Cat("b000000000000".U(20.W), io.instruction(31, 20)))
  
  // Sign extending the 12 bits for U-type instruction
  signExtended_u := Cat(io.instruction(31, 12), "b000000000000".U(12.W))
  
  // Sign extending the 12 bits for S-type instruction
  signExtended_s := Mux(io.instruction(31), Cat("b111111111111".U(20.W), io.instruction(31, 25), io.instruction(11, 7)),
                       Cat("b000000000000".U(20.W), io.instruction(31, 25), io.instruction(11, 7)))

  // Sign extending the 13 bits for B-type instruction
  signExtended_b := Mux(io.instruction(31), Cat("b11111111111".U(21.W), io.instruction(31), io.instruction(7), io.instruction(30, 25), io.instruction(11, 8), "b0".U(1.W)),
                       Cat("b00000000000".U(21.W), io.instruction(31), io.instruction(7), io.instruction(30, 25), io.instruction(11, 8), "b0".U(1.W)))

  // Sign extending the 12 bits for J-type instruction
  signExtended_j := Mux(io.instruction(31), Cat("b11111111".U(8.W), io.instruction(31), io.instruction(19, 12), io.instruction(20), io.instruction(30, 21), "b0".U(1.W)),
                       Cat("b00000000".U(8.W), io.instruction(31), io.instruction(19, 12), io.instruction(20), io.instruction(30, 21), "b0".U(1.W)))

  // Default to incrementedPC
  io.new_pc := incrementedPC

  // Case structure for different instruction types
  when (io.uins.instruction === InstructionType.BEQ) {
    when (io.branch) {
      io.new_pc := io.pc + signExtended_b
    }
  }.elsewhen (io.uins.instruction === InstructionType.BNE) {
    when (io.branch) {
      io.new_pc := io.pc + signExtended_b
    }
  }.elsewhen (io.uins.instruction === InstructionType.BGE) {
    when (io.branch) {
      io.new_pc := io.pc + signExtended_b
    }
  }.elsewhen (io.uins.instruction === InstructionType.BGEU) {
    when (io.branch) {
      io.new_pc := io.pc + signExtended_b
    }
  }.elsewhen (io.uins.instruction === InstructionType.BLT) {
    when (io.branch) {
      io.new_pc := io.pc + signExtended_b
    }
  }.elsewhen (io.uins.instruction === InstructionType.BLTU) {
    when (io.branch) {
      io.new_pc := io.pc + signExtended_b
    }
  }.elsewhen (io.uins.instruction === InstructionType.JAL) {
    io.new_pc := io.pc + signExtended_j
  }.elsewhen (io.uins.instruction === InstructionType.JALR) {
    io.new_pc := io.rs1 + signExtended_i
  }.otherwise {
    io.new_pc := incrementedPC
  }
}

object NEW_PC extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new NEW_PC(),
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

