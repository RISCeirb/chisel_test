package herd.core.betizu

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

import herd.common.mem.milk._

// Cache Direct Mappé avec bus
class DirectMappedCache(p: MilkParams, cacheSize: Int, lineSize: Int) extends Module {
  val io = IO(new Bundle {
    val b_mem       = new MilkIO(p)    
    val b_cpu       = Flipped(new MilkIO(p)) 
    if (p.nHart != 1){
      val invalidate  = Input(Bool())
      val b_control   = new MilkReqIO(p)
    } else None
  })

  val addrWidth = p.nAddrBit
  val dataWidth = p.nDataBit

  // Paramètres du cache
  val numSets       = cacheSize / lineSize
  val indexWidth    = log2Ceil(numSets)
  val offset        = lineSize / (dataWidth / 8)
  val offsetWidth   = log2Ceil(lineSize / (dataWidth / 8))
  val tagWidth      = addrWidth - indexWidth - offsetWidth

  // Mémoires du cache
  val r_cacheMem    = RegInit(VecInit(Seq.fill(numSets)(VecInit(Seq.fill(lineSize / (dataWidth / 8))(0.U(dataWidth.W)))))) 
  val r_tagMem      = RegInit(VecInit(Seq.fill(numSets)(0.U(tagWidth.W)))) 
  val r_validBits   = RegInit(VecInit(Seq.fill(numSets)(false.B))) 

  // Découpage de l'adresse

  val w_hit_index   = io.b_cpu.req.ctrl.addr(indexWidth + offsetWidth - 1 + 2 , offsetWidth +2)
  val w_hit_tag     = io.b_cpu.req.ctrl.addr(addrWidth - 1, indexWidth + offsetWidth +2)
  val w_hit_offset  = io.b_cpu.req.ctrl.addr(offsetWidth +2 - 1, 0 +2)

  val r_addr_miss   = RegInit(io.b_cpu.req.ctrl.addr)

  val w_miss_index  = r_addr_miss(indexWidth + offsetWidth - 1 + 2 , offsetWidth +2)
  val w_miss_tag    = r_addr_miss(addrWidth - 1, indexWidth + offsetWidth +2)
  val w_miss_offset = r_addr_miss(offsetWidth +2 - 1, 0 +2)

  // Vérification du hit
  val hit           = r_validBits(w_hit_index) && (r_tagMem(w_hit_index) === w_hit_tag)

  // États de la FSM

  // if (p.nHart == 1){
  //   object State extends ChiselEnum {
  //     val read, miss  = Value
  //   }
  //   val r_state       = RegInit(State.read)
  // } else {
  //   object State extends ChiselEnum {
  //     val control, read, miss  = Value
  //   }
  //   val r_state       = RegInit(State.control)
  // }

  object State extends ChiselEnum {
    val control, read, miss = Value
  }

  val r_state = RegInit(
    if (p.nHart == 1){
      State.read
    } else {
      State.control
    }
  )


  // Compteur pour gérer l'offset lors des transferts mémoire
  val r_count_req           = RegInit(0.U(offsetWidth.W))
  val r_count_data_mem      = RegInit(0.U(offsetWidth.W))
  val r_count_req_max       = (lineSize / (dataWidth / 8)).U

  io.b_cpu                  := DontCare
  io.b_mem                  := DontCare

  // io.b_control              := 

  // CPU INIT REQ

  io.b_cpu.req.ready        := false.B
  io.b_cpu.write.ready      := true.B
  io.b_cpu.read.valid       := false.B
  io.b_cpu.read.data        := "h00000000".U

  // MEM INIT REQ

  io.b_mem.req.valid        := false.B
  io.b_mem.req.ctrl.addr    := "h00000000".U
  io.b_mem.req.ctrl.size    := SIZE.B4.U
  io.b_mem.write.valid      := false.B
  io.b_mem.write.data       := "h00000000".U
  io.b_mem.read.ready       := true.B

  // CONTROL COHERENCE REQ INIT
 
  // io.b_control <> io.b_cpu.req

  // io.b_control.valid        := false.B
  // io.b_control.hart         := 
  // io.b_control.ctrl.op      := 
  // io.b_control.ctrl.size    := 
  // io.b_control.addr         := 
  // io.b_control.cache        := false.B

  // REGISTER INIT
  
  val r_mem_req_valid       = RegInit(io.b_cpu.req.valid)
  val r_req_ready           = RegInit(false.B)
  val r_req_ctrl_op_mem     = RegInit(io.b_cpu.req.ctrl.op)
  val r_req_hart_mem        = RegInit(io.b_cpu.req.hart)
  val r_req_ctrl_size_mem   = RegInit(SIZE.B4.U)


  val r_write_valid_mem     = RegInit(io.b_cpu.write.valid)
  val r_write_data_mem      = RegInit(io.b_cpu.write.data)

  val r_cpu_read_data       = RegInit(0.U)
  val r_cpu_read_valid      = RegInit(false.B)
  val r_cpu_write_ready_cpu = RegInit(false.B)

  val r_mem_req_ready       = RegInit(false.B)
  val r_mem_read_ready      = RegInit(false.B)


  ///////////////////// CONTROL WRITE ////////////////////// 

  val w_write_data = Wire(UInt(32.W))
  w_write_data := 0.U

  switch(r_req_ctrl_size_mem /*io.b_cpu.req.ctrl.size*/ ){
    is(SIZE.B4.U){ 
      w_write_data := io.b_cpu.write.data
    }
    is(SIZE.B2.U){ 
      switch(r_addr_miss(1,0)){
        is(0.U){
          w_write_data := Cat(r_cacheMem(w_miss_index)(w_miss_offset)(31,16),io.b_cpu.write.data(15,0))
        }
        is(1.U){
          w_write_data := Cat(r_cacheMem(w_miss_index)(w_miss_offset)(31,24),io.b_cpu.write.data(15,0),r_cacheMem(w_miss_index)(w_miss_offset)(7,0))
        }
        is(2.U){
          w_write_data := Cat(io.b_cpu.write.data(15,0),r_cacheMem(w_miss_index)(w_miss_offset)(15,0))
        }
      }
    }
    is(SIZE.B1.U){ 
      switch(r_addr_miss(1,0)){
        is(0.U){
            w_write_data := Cat(r_cacheMem(w_miss_index)(w_miss_offset)(31,8),io.b_cpu.write.data(7,0))
        }
        is(1.U){
            w_write_data := Cat(r_cacheMem(w_miss_index)(w_miss_offset)(31,16),io.b_cpu.write.data(7,0),r_cacheMem(w_miss_index)(w_miss_offset)(7,0))
        }
        is(2.U){
            w_write_data := Cat(r_cacheMem(w_miss_index)(w_miss_offset)(31,24),io.b_cpu.write.data(7,0),r_cacheMem(w_miss_index)(w_miss_offset)(15,0))
        }
        is(3.U){
            w_write_data := Cat(io.b_cpu.write.data(7,0),r_cacheMem(w_miss_index)(w_miss_offset)(23,0))
        }
      }
    }
  }

  ////////////////////// CONTROL FSM ////////////////////// 

  if(p.nHart == 1)
  {
    switch(r_state) {
      is(State.read) {
        when(io.b_cpu.req.valid)
        {
          when(hit) {
            // r_cpu_read_data := r_cacheMem(w_hit_index)(w_hit_offset) >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
            when (~r_cpu_read_valid | io.b_cpu.read.ready || r_cpu_read_valid | io.b_cpu.read.ready) {
              r_cpu_read_data := r_cacheMem(w_hit_index)(w_hit_offset) >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
              r_cpu_read_valid        := true.B
              r_req_ready             := true.B
            }.otherwise{  
              r_cpu_read_valid        := false.B
              r_req_ready             := false.B
            }
            
            // Ecriture/Lecture ready pour prochain cycle
            r_cpu_write_ready_cpu     := true.B
            io.b_mem.read.ready       := false.B
            r_mem_req_valid           := false.B
            r_state                   := State.read
          }.otherwise { 
            r_cpu_read_valid          := false.B
            r_req_ready               := false.B
            r_mem_req_valid           := true.B
            r_cpu_write_ready_cpu     := false.B
            r_state                   := State.miss
          }       
        }.otherwise{
          r_cpu_read_valid            :=  (r_cpu_read_valid & ~io.b_cpu.read.ready)
          r_req_ready                 :=  false.B //(r_cpu_read_valid & io.b_cpu.read.ready)
        }
        when(io.b_cpu.write.valid){

          // Mise à jour d'un champ spécifique en mémoire cache
          r_cacheMem(w_miss_index)(w_miss_offset) := w_write_data    
          r_tagMem(w_miss_index)                  := w_miss_tag
          r_validBits(w_miss_index)               := true.B
        }
        r_count_req := 0.U
        r_count_data_mem := 0.U
        r_mem_read_ready := false.B
      }

      is(State.miss) {
        // Wait for data from memory and store it in the cache
        when(io.b_mem.read.valid) {
          //r_mem_req_valid := true.B
          r_cacheMem(w_miss_index)(r_count_data_mem) := io.b_mem.read.data
          when (r_count_data_mem < r_count_req){
          // Store received data into the cache
          r_count_data_mem := r_count_data_mem + 1.U
          }
          // If all expected data has been received, move back to State.read
          when(r_count_data_mem === r_count_req_max - 1.U) {
            r_state := State.read
            r_tagMem(w_miss_index) := w_miss_tag
            r_validBits(w_miss_index) := true.B
            r_count_req := 0.U
            r_count_data_mem := 0.U
            r_req_ready := true.B
            r_cpu_read_valid := true.B
          }
        }
        // Only issue a new memory request if the previous request was respond
        when(io.b_mem.req.ready && (r_count_req < r_count_req_max) && ~(r_count_req_max - 1.U === r_count_req)) {
          r_count_req := r_count_req + 1.U
        }
        r_mem_read_ready := true.B    
        r_mem_req_valid  := false.B
      }
    }
  } else {

    switch(r_state) {

      is(State.control) {
        when(io.b_cpu.req.valid)
        {
          // Give the request to the controllor

          //io.b_control <> io.b_cpu.req
          //b_control.valid       := io.b_cpu.req.valid
          //b_control.hart        := io.b_cpu.req.valid
          //b_control.ctrl.op     := io.b_cpu.req.valid
          //b_control.ctrl.addr   := io.b_cpu.req.valid

          //when(io.b_control.ready){
          //  r_state := State.read
          //}
        }
      }

      is(State.read) {
        when(io.b_cpu.req.valid)
        {
          when(hit) {
            // r_cpu_read_data := r_cacheMem(w_hit_index)(w_hit_offset) >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
            when (~r_cpu_read_valid | io.b_cpu.read.ready || r_cpu_read_valid | io.b_cpu.read.ready) {
              r_cpu_read_data := r_cacheMem(w_hit_index)(w_hit_offset) >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
              r_cpu_read_valid        := true.B
              r_req_ready             := true.B
            }.otherwise{  
              r_cpu_read_valid        := false.B
              r_req_ready             := false.B
            }
            
            // Ecriture/Lecture ready pour prochain cycle
            r_cpu_write_ready_cpu     := true.B
            io.b_mem.read.ready       := false.B
            r_mem_req_valid           := false.B
            r_state                   := State.read
          }.otherwise { 
            r_cpu_read_valid          := false.B
            r_req_ready               := false.B
            r_mem_req_valid           := true.B
            r_cpu_write_ready_cpu     := false.B
            r_state                   := State.miss
          }       
        }.otherwise{
          r_cpu_read_valid            :=  (r_cpu_read_valid & ~io.b_cpu.read.ready)
          r_req_ready                 :=  false.B //(r_cpu_read_valid & io.b_cpu.read.ready)
        }
        when(io.b_cpu.write.valid){

          // Mise à jour d'un champ spécifique en mémoire cache
          r_cacheMem(w_miss_index)(w_miss_offset) := w_write_data    
          r_tagMem(w_miss_index)                  := w_miss_tag
          r_validBits(w_miss_index)               := true.B
        }
        r_count_req := 0.U
        r_count_data_mem := 0.U
        r_mem_read_ready := false.B
      }

      is(State.miss) {
        // Wait for data from memory and store it in the cache
        when(io.b_mem.read.valid) {
          //r_mem_req_valid := true.B
          r_cacheMem(w_miss_index)(r_count_data_mem) := io.b_mem.read.data
          when (r_count_data_mem < r_count_req){
          // Store received data into the cache
          r_count_data_mem := r_count_data_mem + 1.U
          }
          // If all expected data has been received, move back to State.read
          when(r_count_data_mem === r_count_req_max - 1.U) {
            r_state := State.control
            r_tagMem(w_miss_index) := w_miss_tag
            r_validBits(w_miss_index) := true.B
            r_count_req := 0.U
            r_count_data_mem := 0.U
            r_req_ready := true.B
            r_cpu_read_valid := true.B
          }
        }
        // Only issue a new memory request if the previous request was respond
        when(io.b_mem.req.ready && (r_count_req < r_count_req_max) && ~(r_count_req_max - 1.U === r_count_req)) {
          r_count_req := r_count_req + 1.U
        }
        r_mem_read_ready := true.B    
        r_mem_req_valid  := false.B
      }
    }

  }


  ///////////////////// CONTROL CPU ////////////////////// 

  when (r_state === State.read && io.b_cpu.req.valid){ // NEW REQ 
    r_addr_miss  := io.b_cpu.req.ctrl.addr  
  }
  
  // Propagation écriture main memory
  r_write_data_mem          := io.b_cpu.write.data   
  r_write_valid_mem         := io.b_cpu.write.valid  
  r_req_ctrl_op_mem         := io.b_cpu.req.ctrl.op
  r_req_hart_mem            := io.b_cpu.req.hart
  r_mem_req_valid           := io.b_cpu.req.valid
  r_req_ctrl_size_mem       := io.b_cpu.req.ctrl.size

  // Forward data 
  when (io.b_cpu.write.valid && (io.b_mem.req.ctrl.addr  === io.b_cpu.req.ctrl.addr))
  {
    r_cpu_read_data         := w_write_data >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
  }

  // Control signal cpu

  io.b_cpu.write.ready      := r_cpu_write_ready_cpu    
  io.b_cpu.read.data        := r_cpu_read_data
  io.b_cpu.read.valid       := r_cpu_read_valid
  
  //when (~ io.b_cpu.read.ready && hit && io.b_cpu.req.valid && ~r_req_ready){
  //  io.b_cpu.req.ready        := false.B
  //}.otherwise{
  //  io.b_cpu.req.ready        := hit && r_req_ready 
  //}

  /// !!!!!!!!!!!!!!!!!!!!!! Problème avec io.b_cpu.read.ready qui est égale à 0 à l'init

  io.b_cpu.req.ready        := hit && r_req_ready

  ////////////////////// CONTROL MEM //////////////////////

  when(r_state === State.miss){
    //Lecture de la memoire pour mettre à jour les donnés
    io.b_mem.req.ctrl.size    := SIZE.B4.U //r_req_ctrl_size_mem
    // io.b_mem.read.ready       := true.B    // r_mem_read_ready
    io.b_mem.req.hart         := r_req_hart_mem
    io.b_mem.req.valid        := r_mem_req_valid
    io.b_mem.req.ctrl.op      := 0.U
    io.b_mem.req.ctrl.addr := (((r_addr_miss(addrWidth - 1, offsetWidth + 2) << offsetWidth) + r_count_req) << 2).asUInt
  }.otherwise{ 
    //Ecriture dans la mémoire en propagent le registre
    io.b_mem.req.ctrl.size    := r_req_ctrl_size_mem
    io.b_mem.req.valid        := r_mem_req_valid 
    io.b_mem.req.hart         := r_req_hart_mem
    io.b_mem.req.ctrl.op      := r_req_ctrl_op_mem  
    io.b_mem.req.ctrl.addr    := r_addr_miss 
    io.b_mem.write.data       := r_write_data_mem 
    io.b_mem.write.valid      := r_write_valid_mem
  }

}

// Génération du Verilog

object DirectMappedCache extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new DirectMappedCache(MilkConfig0, 512, 64),
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

//r_cpu_read_data := r_cacheMem(w_hit_index)(w_hit_offset) >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //shift by 8 bits for lb addr when 8 bits or 16 bits data  
//r_cpu_read_valid := ~r_cpu_read_valid | (r_cpu_read_valid & ~io.b_cpu.read.ready)
  
