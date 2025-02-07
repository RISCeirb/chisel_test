package template.my_module

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class ALU(width: Int) extends Module {
  val io = IO(new Bundle {
    val pc       = Input(UInt(32.W))       // PC input
    val operand1 = Input(UInt(width.W))     // Operand 1
    val operand2 = Input(UInt(width.W))     // Operand 2
    val result   = Output(UInt(width.W))    // ALU result
    val branch   = Output(Bool())           // Branch flag
    val op       = Input(InstructionType()) // ALU operation
  })

  // Internal signals
  val op1_s = io.operand1.asSInt
  val op2_s = io.operand2.asSInt
  val temp = Wire(UInt(width.W))

  // Initialize temp to a default value (0)
  temp := 0.U
  io.result := temp
  io.branch := false.B

  // Utiliser un registre pour mémoriser le résultat précédent
  //val temp = RegInit(0.U(width.W))
  //temp := 0.U
  //io.branch := false.B


  // ALU Operation Logic
  switch(io.op) {
    // R-type instructions
    is(InstructionType.ADD) { temp := io.operand1 + io.operand2 }
    is(InstructionType.SUB) { temp := io.operand1 - io.operand2 }

    // Shift operations (SLL, SRL, SRA)
    is(InstructionType.SLL) { temp := io.operand1 << io.operand2(4, 0) }
    is(InstructionType.SRL) { temp := io.operand1 >> io.operand2(4, 0) }
    is(InstructionType.SRA) { temp := (op1_s >> io.operand2(4, 0)).asUInt }

    // Set less than (SLTU, SLT)
    is(InstructionType.SLTU) { temp := Mux(io.operand1 < io.operand2, 1.U, 0.U) }
    is(InstructionType.SLT) { temp := Mux(op1_s < op2_s, 1.U, 0.U) }

    // Bitwise operations (OR, XOR, AND)
    is(InstructionType.OR) { temp := io.operand1 | io.operand2 }
    is(InstructionType.XOR) { temp := io.operand1 ^ io.operand2 }
    is(InstructionType.AND) { temp := io.operand1 & io.operand2 }

    // I-type instructions (for immediate values)
    is(InstructionType.ADDI) { temp := io.operand1 + io.operand2 }
    is(InstructionType.ANDI) { temp := io.operand1 & io.operand2 }
    is(InstructionType.ORI) { temp := io.operand1 | io.operand2 }
    is(InstructionType.XORI) { temp := io.operand1 ^ io.operand2 }

    // LUI and AUIPC (U-type instructions)
    is(InstructionType.LUI) { temp := io.operand2 }
    is(InstructionType.AUIPC) { temp := io.operand2 + io.pc }

    // Branch operations
    is(InstructionType.BEQ) { io.branch := io.operand1 === io.operand2 }
    is(InstructionType.BNE) { io.branch := io.operand1 =/= io.operand2 }
    is(InstructionType.BLT) { io.branch := op1_s < op2_s }
    is(InstructionType.BGE) { io.branch := op1_s >= op2_s }
    is(InstructionType.BLTU) { io.branch := io.operand1 < io.operand2 }
    is(InstructionType.BGEU) { io.branch := io.operand1 >= io.operand2 }

    // J-type instructions (JAL, JALR)
    is(InstructionType.JALR) { temp := io.pc + 4.U }
    is(InstructionType.JAL) { temp := io.pc + 4.U }

    // S-type instructions
    is(InstructionType.SW) { temp := io.operand1 + io.operand2 }
    is(InstructionType.SH) { temp := io.operand1 + io.operand2 }
    is(InstructionType.SB) { temp := io.operand1 + io.operand2 }

    // I-type load instructions
    is(InstructionType.LW) { temp := io.operand1 + io.operand2 }
    is(InstructionType.LB) { temp := io.operand1 + io.operand2 }
    is(InstructionType.LH) { temp := io.operand1 + io.operand2 }
    is(InstructionType.LBU) { temp := io.operand1 + io.operand2 }
    is(InstructionType.LHU) { temp := io.operand1 + io.operand2 }

    // Default case (return 0 for invalid instructions)
    is(InstructionType.INVALID_INSTRUCTION) { temp := 0.U }
  }

  //io.result := temp
}

object ALU extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new ALU(32),
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

