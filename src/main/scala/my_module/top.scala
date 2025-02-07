package template.my_module

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

// Définition du module top avec les mémoires
class top extends Module {

  val io = IO(new Bundle {

   // Instruction memory interface
    val instructionAddress = Output(UInt(32.W))
    val instruction        = Output(UInt(32.W))

    // Data memory interface
    val dataAddress = Output(UInt(32.W))
    val data_i      = Output(UInt(32.W))
    val data_o      = Output(UInt(32.W))
    val MemWrite    = Output(Bool())

  })

  val RARS_INSTRUCTION_OFFSET = "h00400000".U(32.W)
  val RARS_DATA_OFFSET = "h10010000".U(32.W)


  // Signaux de test
  val instructionAddress = Wire(UInt(32.W))  
  val dataAddress        = Wire(UInt(32.W))  
  val instruction        = Wire(UInt(32.W))
  val data_i             = Wire(UInt(32.W))
  val data_o             = Wire(UInt(32.W))
  val MemWrite           = Wire(Bool())

  // Instanciation du processeur monocycle
  val riscVMonocycle = Module(new RISC_V_Monocycle(0x00400000))
  instructionAddress := riscVMonocycle.io.instructionAddress
  riscVMonocycle.io.instruction := instruction
  dataAddress := riscVMonocycle.io.dataAddress
  riscVMonocycle.io.data_i := data_i
  data_o := riscVMonocycle.io.data_o
  MemWrite := riscVMonocycle.io.MemWrite


  /*
  // Instanciation de la mémoire de données
  val w_data = Wire(Vec(32, UInt(32.W)))  // Mémoire de données de taille 64

  // Initialisation des valeurs de la mémoire de données
  w_data := VecInit(
    "h00000004".U, "h00000004".U, "h00000004".U, "h00000004".U,
    "h00000002".U, "h00000002".U, "h00000002".U, "h00000002".U,
    "h00000001".U, "h00000001".U, "h00000001".U, "h00000001".U,
    "h00000005".U, "h00000005".U, "h00000005".U, "h00000005".U,
    "h00000004".U, "h00000004".U, "h00000004".U, "h00000004".U,
    "h00000007".U, "h00000007".U, "h00000007".U, "h00000007".U,
    "h00000003".U, "h00000003".U, "h00000003".U, "h00000003".U,
    "h00000007".U, "h00000007".U, "h00000007".U, "h00000007".U
  )

  // Registre pour la mémoire de données
  val r_mem_data = RegInit(w_data)

  // Mise à jour de la mémoire de données
  when(MemWrite) {
    r_mem_data((dataAddress - RARS_DATA_OFFSET)(4,0)) := data_o
  }
  data_i := r_mem_data((dataAddress - RARS_DATA_OFFSET)(4,0)) // Lecture des données depuis la mémoire

  // Instanciation de la mémoire d'instructions
  val w_ins = Wire(Vec(88, UInt(32.W))) // Taille de 22 pour les instructions //22*4

  // Initialisation des instructions
  w_ins := VecInit(
    "h00100f13".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h00100513".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h04050663".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h0fc10297".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "hff428293".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h0fc10f97".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h008f8f93".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h000faf83".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h00000513".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h0002a303".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h0042a383".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h0063a5b3".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h01e58a63".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h00428293".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "hffff8f93".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "hfdef86e3".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "hfe5ff06f".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h0062a223".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h0072a023".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h00100513".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "hfe5ff06f".U, "h00000000".U, "h00000000".U, "h00000000".U,
    "h0000006f".U, "h00000000".U, "h00000000".U, "h00000000".U
  )

  val r_mem_ins = RegInit(w_ins)
  instruction := r_mem_ins((instructionAddress- RARS_INSTRUCTION_OFFSET)(6, 0))

  // test

  // Assignation des signaux internes à la sortie IO
  io.instructionAddress := instructionAddress
  io.instruction := instruction
  io.dataAddress := dataAddress
  io.data_i := data_i
  io.data_o := data_o
  io.MemWrite := MemWrite

  */

}

object top extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new top(),
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
