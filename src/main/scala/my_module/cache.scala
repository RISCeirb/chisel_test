package template.my_module

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

// Définition du bus pour les échanges CPU <-> Cache et Cache <-> Mémoire
class Bus(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val writeEnable = Input(Bool())             // Enable écriture
  val addr   = Input(UInt(addrWidth.W))       // Adresse mémoire
  val data   = Input(UInt(dataWidth.W))       // Données à écrire (CPU -> Cache, Mémoire -> Cache)
  val readData = Output(UInt(dataWidth.W))    // Données lues (Cache -> CPU, Cache -> Mémoire)
}

// Cache Direct Mappé avec bus
class DirectMappedCache(val addrWidth: Int, val dataWidth: Int, val cacheSize: Int, val lineSize: Int) extends Module {
  val io = IO(new Bundle {
    val cpu = new Bus(addrWidth, dataWidth)  // Bus entre CPU et Cache
    val mem = Flipped(new Bus(addrWidth, dataWidth)) // Bus entre Cache et Mémoire principale
  })

  // Paramètres du cache
  val numSets = cacheSize / lineSize
  val indexWidth = log2Ceil(numSets)
  val offsetWidth = log2Ceil(lineSize / (dataWidth / 8))
  val tagWidth = addrWidth - indexWidth - offsetWidth

  // Mémoires du cache
  val cacheMem = RegInit(VecInit(Seq.fill(numSets)(VecInit(Seq.fill(lineSize / (dataWidth / 8))(0.U(dataWidth.W))))))
  val tagMem = RegInit(VecInit(Seq.fill(numSets)(0.U(tagWidth.W))))
  val validBits = RegInit(VecInit(Seq.fill(numSets)(false.B)))

  // Découpage de l'adresse
  val index = io.cpu.addr(indexWidth + offsetWidth - 1, offsetWidth)
  val tag = io.cpu.addr(addrWidth - 1, indexWidth + offsetWidth)
  val offset = io.cpu.addr(offsetWidth - 1, 0)

  // Vérification du hit
  val hit = validBits(index) && (tagMem(index) === tag)

  // États de la FSM
  object State extends ChiselEnum {
    val idle, read, miss, waitMem = Value
  }

  val state = RegInit(State.idle)

  // Par défaut, pas d'écriture mémoire
  io.mem.writeEnable := false.B
  io.mem.addr := 0.U
  io.mem.data := 0.U
  io.cpu.readData := 0.U

  // FSM du cache
  switch(state) {
    is(State.idle) {
      when(hit) {
        // Cache hit: Read data from cache
        io.cpu.readData := cacheMem(index)(offset)
        when(io.cpu.writeEnable) {
          // Write data to cache
          cacheMem(index)(offset) := io.cpu.data
          tagMem(index) := tag
          validBits(index) := true.B
        }
        state := State.read
      }.otherwise {
        // Cache miss: Move to miss state
        state := State.miss
      }
    }

    is(State.read) {
      // After handling read or write, transition to idle
      state := State.idle
    }

    is(State.miss) {
      // Handle miss: Fetch data from memory
      io.mem.addr := io.cpu.addr
      io.mem.data := io.cpu.data
      io.mem.writeEnable := io.cpu.writeEnable
      state := State.waitMem
    }

    is(State.waitMem) {
      // After the memory transaction, update cache
      cacheMem(index)(offset) := io.mem.readData
      tagMem(index) := tag
      validBits(index) := true.B
      state := State.read
    }
  }
}

// Génération du Verilog

object DirectMappedCache extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new DirectMappedCache(32, 32, 2048, 128),
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
