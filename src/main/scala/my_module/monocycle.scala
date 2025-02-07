package template.my_module

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class RISC_V_Monocycle(PC_START_ADDRESS: Int = 0) extends Module {
  val io = IO(new Bundle {

    // Instruction memory interface
    val instructionAddress = Output(UInt(32.W))
    val instruction        = Input(UInt(32.W))

    // Data memory interface
    val dataAddress = Output(UInt(32.W))
    val data_i      = Input(UInt(32.W))
    val data_o      = Output(UInt(32.W))
    val MemWrite    = Output(Bool())
  })

  // Microinstruction signal (equivalent to uins in VHDL)
  val uins = Wire(new Microinstruction)

  // PC signals
  val pc     = RegInit(PC_START_ADDRESS.U(32.W))
  val new_pc = Wire(UInt(32.W))

  // Control Path Module
  val controlPath = Module(new ControlPath)
  controlPath.io.instruction := io.instruction
  uins := controlPath.io.uins

  // Data Path Module
  val dataPath = Module(new datapath(PC_START_ADDRESS))
  dataPath.io.uins := uins
  new_pc := dataPath.io.new_pc
  dataPath.io.pc := pc
  dataPath.io.instruction := io.instruction
  dataPath.io.data_i := io.data_i
  io.dataAddress := dataPath.io.dataAddress
  io.data_o := dataPath.io.data_o

  // Program Counter (PC)
  pc := new_pc


  io.instructionAddress := pc
  io.MemWrite := uins.MemWrite
}


object RISC_V_Monocycle extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new RISC_V_Monocycle(0),
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
