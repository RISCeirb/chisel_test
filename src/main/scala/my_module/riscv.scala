package template.my_module

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

object InstructionType extends ChiselEnum {
  val LUI, AUIPC, JAL, JALR, BEQ, BNE, BLT, BGE, BLTU, BGEU,
      LB, LH, LW, LBU, LHU, SB, SH, SW, ADDI, SLTI, SLTIU, XORI, 
      ORI, ANDI, SLLI, SRLI, SRAI, ADD, SUB, SLL, SLT, SLTU, 
      XOR, SRL, SRA, OR, AND, FENCE, FENCEI, ECALL, 
      EBREAK, CSRRW, CSRRS, CSRRC, CSRRWI, CSRRSI, CSRRCI, 
      INVALID_INSTRUCTION,
      // M-extention
      MUL, MULH, MULHSU, MULHU, DIV, REM, DIVU, REMU = Value
}

object InstructionFormat extends ChiselEnum {
  val R, I, S, B, U, J, INVALID = Value
}

class Microinstruction extends Bundle {
  val RegWrite  = Bool()                  // Register file write control
  val MemToReg  = Bool()                  // Selects the data to the register file
  val MemWrite  = Bool()                  // Enable the data memory write
  val instruction = InstructionType()     // Decoded instruction
  val format    = InstructionFormat()     // Select (R, I, S, B, U, J) ALU second operand
}