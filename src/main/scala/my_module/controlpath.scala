package template.my_module

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage


// Module ControlPath
class ControlPath extends Module {
  val io = IO(new Bundle {
    val instruction  = Input(UInt(32.W))
    val uins         = Output(new Microinstruction)
  })

  // Extraction des champs opcode, funct3, funct7
  val opcode = io.instruction(6, 0)
  val funct3 = io.instruction(14, 12)
  val funct7 = io.instruction(31, 25)

  // Instruction Type - Using switch-case for opcode
  io.uins.instruction := InstructionType.INVALID_INSTRUCTION  

  switch(opcode) {
    is("b0110111".U) { io.uins.instruction := InstructionType.LUI }
    is("b0010111".U) { io.uins.instruction := InstructionType.AUIPC }
    is("b1101111".U) { io.uins.instruction := InstructionType.JAL }
    is("b1100111".U) {
      // Check funct3 within this case
      switch(funct3) {
        is("b000".U) { io.uins.instruction := InstructionType.JALR }
        
      }
    }
    is("b1100011".U) {
      // Check funct3 for branch instructions
      switch(funct3) {
        is("b000".U) { io.uins.instruction := InstructionType.BEQ }
        is("b001".U) { io.uins.instruction := InstructionType.BNE }
        is("b100".U) { io.uins.instruction := InstructionType.BLT }
        is("b101".U) { io.uins.instruction := InstructionType.BGE }
        is("b110".U) { io.uins.instruction := InstructionType.BLTU }
        is("b111".U) { io.uins.instruction := InstructionType.BGEU }
        
      }
    }
    is("b0000011".U) {
      // Check funct3 for load instructions
      switch(funct3) {
        is("b000".U) { io.uins.instruction := InstructionType.LB }
        is("b001".U) { io.uins.instruction := InstructionType.LH }
        is("b010".U) { io.uins.instruction := InstructionType.LW }
        is("b100".U) { io.uins.instruction := InstructionType.LBU }
        is("b101".U) { io.uins.instruction := InstructionType.LHU }
        
      }
    }
    is("b0100011".U) {
      // Check funct3 for store instructions
      switch(funct3) {
        is("b000".U) { io.uins.instruction := InstructionType.SB }
        is("b001".U) { io.uins.instruction := InstructionType.SH }
        is("b010".U) { io.uins.instruction := InstructionType.SW }
        
      }
    }
    is("b0010011".U) {
      // Check funct3 for arithmetic immediate instructions
      switch(funct3) {
        is("b000".U) { io.uins.instruction := InstructionType.ADDI }
        is("b010".U) { io.uins.instruction := InstructionType.SLTI }
        is("b011".U) { io.uins.instruction := InstructionType.SLTIU }
        is("b100".U) { io.uins.instruction := InstructionType.XORI }
        is("b110".U) { io.uins.instruction := InstructionType.ORI }
        is("b111".U) { io.uins.instruction := InstructionType.ANDI }
        is("b001".U) {
          // Check funct7 for shift left immediate (SLLI)
          switch(funct7) {
            is("b0000000".U) { io.uins.instruction := InstructionType.SLLI }
            
          }
        }
        is("b101".U) {
          // Check funct7 for shift right immediate (SRLI/SRAI)
          switch(funct7) {
            is("b0000000".U) { io.uins.instruction := InstructionType.SRLI }
            is("b0100000".U) { io.uins.instruction := InstructionType.SRAI }
            
          }
        }
        
      }
    }
    is("b0110011".U) {
      // Check funct3 for register-register instructions
      switch(funct3) {
        is("b000".U) {
          // Check funct7 for ADD/SUB
          switch(funct7) {
            is("b0000000".U) { io.uins.instruction := InstructionType.ADD }
            is("b0100000".U) { io.uins.instruction := InstructionType.SUB }
            
          }
        }
        is("b001".U) { io.uins.instruction := InstructionType.SLL }
        is("b010".U) { io.uins.instruction := InstructionType.SLT }
        is("b011".U) { io.uins.instruction := InstructionType.SLTU }
        is("b100".U) { io.uins.instruction := InstructionType.XOR }
        is("b101".U) {
          // Check funct7 for SRL/SRA
          switch(funct7) {
            is("b0000000".U) { io.uins.instruction := InstructionType.SRL }
            is("b0100000".U) { io.uins.instruction := InstructionType.SRA }
            
          }
        }
        is("b110".U) { io.uins.instruction := InstructionType.OR }
        is("b111".U) { io.uins.instruction := InstructionType.AND }
        
      }
    }
    is("b0001111".U) {
      // Check funct3 for FENCE instructions
      switch(funct3) {
        is("b000".U) { io.uins.instruction := InstructionType.FENCE }
        is("b001".U) { io.uins.instruction := InstructionType.FENCEI }
        
      }
    }
    is("b1110011".U) {
      // Check funct3 for CSR instructions
      switch(funct3) {
        is("b001".U) { io.uins.instruction := InstructionType.CSRRW }
        is("b010".U) { io.uins.instruction := InstructionType.CSRRS }
        is("b011".U) { io.uins.instruction := InstructionType.CSRRC }
        is("b101".U) { io.uins.instruction := InstructionType.CSRRWI }
        is("b110".U) { io.uins.instruction := InstructionType.CSRRSI }
        is("b111".U) { io.uins.instruction := InstructionType.CSRRCI }
        
      }
    }
    
  }

  // InstructionFormat - Using switch-case for opcode
  io.uins.format := InstructionFormat.INVALID  
  
  switch(opcode) {
    is("b0110011".U) { io.uins.format := InstructionFormat.R }
    is("b0010011".U) { io.uins.format := InstructionFormat.I }
    is("b0000011".U) { io.uins.format := InstructionFormat.I }
    is("b1100111".U) { io.uins.format := InstructionFormat.I }
    is("b0110111".U) { io.uins.format := InstructionFormat.U }
    is("b0010111".U) { io.uins.format := InstructionFormat.U }
    is("b0100011".U) { io.uins.format := InstructionFormat.S }
    is("b1100011".U) { io.uins.format := InstructionFormat.B }
    is("b1101111".U) { io.uins.format := InstructionFormat.J }

  }

  // RegWrite signal activated for all but S and B (Store and Branch)
  io.uins.RegWrite := io.uins.format === InstructionFormat.R || 
                      io.uins.format === InstructionFormat.I || 
                      io.uins.format === InstructionFormat.U || 
                      io.uins.format === InstructionFormat.J

  // MemToReg signal activated for load instructions
  io.uins.MemToReg := io.uins.instruction === InstructionType.LW || 
                      io.uins.instruction === InstructionType.LH || 
                      io.uins.instruction === InstructionType.LHU || 
                      io.uins.instruction === InstructionType.LB || 
                      io.uins.instruction === InstructionType.LBU

  // MemWrite signal activated for store instructions
  io.uins.MemWrite := io.uins.instruction === InstructionType.SW || 
                      io.uins.instruction === InstructionType.SH || 
                      io.uins.instruction === InstructionType.SB
}

object ControlPath extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new ControlPath(),
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
