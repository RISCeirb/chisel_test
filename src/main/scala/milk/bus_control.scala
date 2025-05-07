package herd.common.mem.milk

import chisel3._
import chisel3.util._



// Enum pour les types d'opérations
object OpType {
  val READ  = 0.U
  val WRITE = 1.U
}

object State_MSI extends ChiselEnum {
  val s_invalid, s_shared, s_modified, s_exclu = Value
}

// Annuaire pour la cohérence MSI :
class Directory_class(nBlock: Int, tagWidth: Int) extends Bundle {
  val presence = Vec(nBlock, Bool()) // Vecteur de bits de présence
  val state    = State_MSI()         // 2 bits pour l'état MSI
  val tag      = UInt(tagWidth.W)
}

class Bus_control (p: MilkParams) extends Bundle {
  val addr          = Input(UInt(p.nAddrBit.W))
  val rep_state     = Input(State_MSI()) //Input(UInt(2.W))
  val req_control   = new Bus_arbiter(p)
  val ack_invalid   = Output(Bool())
  val ack_write     = Output(Bool())
}

class Bus_coherency (p: MilkParams) extends Bundle {
  val bus_ins       = new Bus_control(p)
  val bus_dat       = new Bus_control(p)
}

class Ctrl_arbiter (p: MilkParams) extends Bundle {
  val hart          = UInt(log2Ceil(p.nHart).W)
  val addr          = UInt(p.nAddrBit.W)
  val op            = UInt(p.nOpBit.W)
}

class Bus_arbiter (p: MilkParams) extends Bundle {
  val ready         = Input(Bool())
  val valid         = Output(Bool())
  val ctrl          = new Ctrl_arbiter(p)
}