package template.my_module

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class datapath(PC_START_ADDRESS: Int = 0) extends Module {
  val io = IO(new Bundle {
    val pc             = Input(UInt(32.W))      // Current PC value
    val new_pc         = Output(UInt(32.W))     // New calculated PC
    val instruction    = Input(UInt(32.W))       // Instruction input from memory
    val dataAddress    = Output(UInt(32.W))      // Address to Data memory
    val data_i         = Input(UInt(32.W))       // Data input from memory
    val data_o         = Output(UInt(32.W))      // Data output to memory
    val uins           = Input(new Microinstruction) // Control signals for the datapath
  })

  // Signals for various datapath operations
  //val incrementedPC = Wire(UInt(32.W))
  val result = Wire(UInt(32.W))
  val readData1 = Wire(UInt(32.W))
  val readData2 = Wire(UInt(32.W))
  val ALUoperand2 = Wire(UInt(32.W))
  val writeData = Wire(UInt(32.W))  // This will be written to RegisterFile and MUX_LOAD
  val branch = Wire(Bool())  // Branch signal

  // Instantiate the NEW_PC module to calculate the new PC
  val pcCalculus = Module(new NEW_PC)
  pcCalculus.io.rs1 := readData1
  pcCalculus.io.pc := io.pc
  pcCalculus.io.instruction := io.instruction
  pcCalculus.io.branch := branch
  pcCalculus.io.uins := io.uins
  io.new_pc := pcCalculus.io.new_pc

  // Instantiate the MUX_ALU module for selecting the second operand for the ALU
  val muxALU = Module(new MUX_ALU)
  muxALU.io.register2 := readData2
  muxALU.io.instruction := io.instruction
  muxALU.io.uins := io.uins

  // Corrected: Connect ALUoperand2 to the output of the MUX_ALU
  ALUoperand2 := muxALU.io.data_mux

  // Instantiate the MUX_LOAD module for selecting data to be written to registers
  val muxDataLoad = Module(new MUX_LOAD)
  muxDataLoad.io.mem_data := io.data_i
  muxDataLoad.io.result_ALU := result
  muxDataLoad.io.uins := io.uins
  writeData := muxDataLoad.io.writeData 

  // Instantiate the MUX_STORE module for selecting the data to be written to memory
  val muxDataStore = Module(new MUX_STORE)
  muxDataStore.io.rs2 := readData2
  io.data_o := muxDataStore.io.data_to_mem 
  muxDataStore.io.uins := io.uins

  // ALU computation
  val alu = Module(new ALU(32))
  alu.io.pc := io.pc
  alu.io.operand1 := readData1
  alu.io.operand2 := ALUoperand2
  result := alu.io.result 
  alu.io.op := io.uins.instruction

  // Address to data memory comes from ALU result
  branch := alu.io.branch 
  io.dataAddress := result

  // Register file (RF)
  val regFile = Module(new RegisterFile)
  regFile.io.write := io.uins.RegWrite
  regFile.io.readRegister1 := io.instruction(19, 15)  // rs1 field
  regFile.io.readRegister2 := io.instruction(24, 20)  // rs2 field
  regFile.io.writeRegister := io.instruction(11, 7)   // rd field
  regFile.io.writeData := writeData
  readData1 := regFile.io.readData1
  readData2 := regFile.io.readData2 

  // Programme counter


}

object datapath extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new datapath(0),
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
