package template.my_module

import chisel3._
import chisel3.util._
import _root_.circt.stage.{ChiselStage}

class RegisterFile extends Module {
  val io = IO(new Bundle {
    val write          = Input(Bool())
    val readRegister1  = Input(UInt(5.W))   // 5-bit register address for readRegister1
    val readRegister2  = Input(UInt(5.W))   // 5-bit register address for readRegister2
    val writeRegister  = Input(UInt(5.W))   // 5-bit register address for writeRegister
    val writeData      = Input(UInt(32.W))  // Data to write to register
    val readData1      = Output(UInt(32.W)) // Data to read from register 1
    val readData2      = Output(UInt(32.W)) // Data to read from register 2
  })

  // Array to hold register values
  val reg = RegInit(VecInit(Seq.fill(32)(0.U(32.W)))) // Initialize registers to 0
  
  // Write enable signal for each register
  val writeEnable = Wire(Vec(32, Bool()))
  writeEnable := VecInit(Seq.fill(32)(false.B)) 

  // Generate write enable logic for each register
  for (i <- 1 until 32) {
    // Register $0 is the constant 0, no write enabled
    writeEnable(i) := (io.write && io.writeRegister === i.U)
  }

  // Writing data to registers
  when(io.write && io.writeRegister > 0.U) {
    reg(io.writeRegister) := io.writeData
  }

  // Read data from the specified registers
  io.readData1 := reg(io.readRegister1)
  io.readData2 := reg(io.readRegister2)
}

object RegisterFile extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new RegisterFile(),
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
