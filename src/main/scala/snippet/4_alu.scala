package template.snippet

import chisel3._
import chisel3.util._
import _root_.circt.stage.{ChiselStage}

// Constants
object OP {
  def NBIT    = 3

  def ADD     = 0.U(NBIT.W)
  def SUB     = 1.U(NBIT.W)
  def OR      = 2.U(NBIT.W)
  def AND     = 3.U(NBIT.W)
  def XOR     = 4.U(NBIT.W)
  def SHIFTL  = 5.U(NBIT.W)
}

class AluModule(nBit: Int) extends Module {
  val io = IO(new Bundle {
    val i_sel = Input(UInt(OP.NBIT.W))
    val i_op1 = Input(UInt(nBit.W))
    val i_op2 = Input(UInt(nBit.W))

    val o_out = Output(UInt(nBit.W))
  })  

  io.o_out := 0.U     // Default value
  switch(io.i_sel) {
    is (OP.ADD) {
      io.o_out := io.i_op1 + io.i_op2
    }
    is (OP.SUB) {
      io.o_out := io.i_op1 - io.i_op2
    }
    is (OP.OR) {
      io.o_out := io.i_op1 | io.i_op2
    }
    is (OP.AND) {
      io.o_out := io.i_op1 & io.i_op2
    }
    is (OP.XOR) {
      io.o_out := io.i_op1 ^ io.i_op2
    }
    is (OP.SHIFTL) {
      io.o_out := io.i_op1 << io.i_op2(log2Ceil(nBit) - 1, 0)
    }
  }
}

object AluModule extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new AluModule(16),
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