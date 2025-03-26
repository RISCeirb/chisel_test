package herd.core.betizu

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

import herd.common.mem.milk._
import herd.common.gen._

// Cache Direct Mappé avec bus
class DirectMappedCache(p: MilkParams, cacheSize: Int, lineSize: Int, useCoherency: Boolean) extends Module {
  val io = IO(new Bundle {
    val b_mem       = new MilkIO(p)    
    val b_cpu       = Flipped(new MilkIO(p)) 
    val b_control   = if (useCoherency) Some(new Bus_control(p)) else None
  })

  val addrWidth     = p.nAddrBit
  val dataWidth     = p.nDataBit
  // val useCoherency  = 0  //p.useCoherency

  // Paramètres du cache
  val numSets       = cacheSize / lineSize
  val indexWidth    = log2Ceil(numSets)
  val offset        = lineSize / (addrWidth / 8)
  val offsetWidth   = log2Ceil(lineSize / (addrWidth / 8))
  val tagWidth      = addrWidth - indexWidth - offsetWidth

  // Mémoires du cache
  val r_cacheMem    = RegInit(VecInit(Seq.fill(numSets)(VecInit(Seq.fill(lineSize / (dataWidth / 8))(0.U(dataWidth.W)))))) 
  val r_tagMem      = RegInit(VecInit(Seq.fill(numSets)(0.U(tagWidth.W)))) 

  // val r_validBits   = RegInit(VecInit(Seq.fill(numSets)(false.B))) 

  val r_validState = RegInit(VecInit(Seq.fill(numSets)(State_MSI.s_invalid))) 

  // Découpage de l'adresse

  val w_hit_index   = io.b_cpu.req.ctrl.addr(indexWidth + offsetWidth - 1 + 2 , offsetWidth +2)
  val w_hit_tag     = io.b_cpu.req.ctrl.addr(addrWidth - 1, indexWidth + offsetWidth +2)
  val w_hit_offset  = io.b_cpu.req.ctrl.addr(offsetWidth +2 - 1, 0 +2)

  val r_addr_miss   = RegInit(io.b_cpu.req.ctrl.addr)

  val w_miss_index  = r_addr_miss(indexWidth + offsetWidth - 1 + 2 , offsetWidth +2)
  val w_miss_tag    = r_addr_miss(addrWidth - 1, indexWidth + offsetWidth +2)
  val w_miss_offset = r_addr_miss(offsetWidth +2 - 1, 0 +2)

  // États de la FSM

  object State extends ChiselEnum {
    val read, miss = Value
  }

  val r_state = RegInit(
    if (useCoherency == false){
      State.read
    } else {
      State.read
    }
  )

  ///////////////////////// COHERENCY /////////////////////////////

  val r_state_previous = RegInit(r_state)
  r_state_previous    := r_state

  val hit_control = (r_state === State.read) & (r_state_previous === State.miss) & (r_addr_miss  === io.b_cpu.req.ctrl.addr) // Same request and back from loading data (Avoid branch issue)

   // Vérification du hit

  val hit           = if (useCoherency == false){ 
                        (r_validState(w_hit_index) =/= State_MSI.s_invalid) && (r_tagMem(w_hit_index) === w_hit_tag) 
                      } else {
                        (r_validState(w_hit_index) =/= State_MSI.s_invalid) && (r_tagMem(w_hit_index) === w_hit_tag) && !(io.b_control.get.rep_state === State_MSI.s_invalid) // || hit_control
                      }

  // Compteur pour gérer l'offset lors des transferts mémoire
  val r_count_req           = RegInit(0.U(offsetWidth.W))
  val r_count_data_mem      = RegInit(0.U(offsetWidth.W))
  //val r_count_req_max       = (lineSize / (dataWidth / 8)).U
//
  //dontTouch(r_count_req_max)

  val r_count_req_max = Wire(UInt(log2Ceil(lineSize / (dataWidth / 8) + 1).W))
  r_count_req_max := (lineSize / (dataWidth / 8)).U
  dontTouch(r_count_req_max)


  io.b_cpu                  := DontCare
  io.b_mem                  := DontCare

  if (useCoherency) {
    io.b_control.get        := DontCare
  }
  

  // CPU INIT REQ
  
  val w_req_cpu_ready = Wire(Bool())

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
 
  // io.b_control.get <> io.b_cpu.req

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


  ///////////////////// ATOMIC LR/SC UNIT ///////////////////

  if (p.useAmo) {
    println("Atomic is used!")
  } 

  //if (p.useAmo) {

    //Déclaration des registres pour le mécanisme de lock (verrouillage de l'adresse)
    val r_lock          = RegInit(false.B)            // Registre pour l'état du lock (false = non verrouillé, true = verrouillé)
    val r_lock_addr     = RegInit(0.U(addrWidth.W))   // Adresse du verrou
    val r_lock_hart     = RegInit(0.U(p.nHart.W))     // Hart (processeur) associé au verrou
    val r_success_ato   = RegInit(false.B)  

    when (hit){
      // Gestion de l'écriture du lock lors d'une opération Load Reserved (LR)
      when(io.b_cpu.req.ctrl.op === OP.LR) {  // Si l'opération demandée est LR (Load Reserved)
        r_lock       := true.B                   // Verrouille l'accès à l'adresse
        r_lock_addr  := io.b_cpu.req.ctrl.addr      // Enregistre l'adresse de la mémoire pour le lock
        r_lock_hart  := io.b_cpu.req.hart      // Enregistre le hart (processeur) pour l'opération LR
      }

      // Libération du lock sur Store (W) ou Store Conditional (SC)
      when(io.b_cpu.req.ctrl.op === OP.W || io.b_cpu.req.ctrl.op === OP.SC) { // Si l'opération est un Store (W) ou Store Conditional (SC)
        r_lock       := false.B                  // Libère le verrou, permettant l'accès à l'adresse par d'autres
        r_lock_addr  := 0.U                      // Réinitialise l'adresse du lock
        r_lock_hart  := 0.U                      // Réinitialise le hart associé au lock
      } .otherwise {
        // Si l'opération n'est ni LR, ni W/SC, le verrou reste inchangé.
      }

      when(io.b_cpu.req.ctrl.op === OP.SC && r_lock) {
        io.b_cpu.read.data := 0.U // Return zero value
      }.otherwise {
        io.b_cpu.read.data := 1.U // Return non zero value
      }

      r_success_ato := ((io.b_cpu.req.ctrl.op === OP.SC) && r_lock ) || (io.b_cpu.req.ctrl.op === OP.W)
    }

    dontTouch(r_success_ato)

  //} else {
  //  // Si useAmo est désactivé, aucune opération de lock n'est effectuée.
  //  None
  //}

  ////////////////////// ATOMIC ALU ////////////////////////

  // if (p.useAmo) {
  //   val w_res = Wire(UInt(p.nDataBit.W))
  //   w_res := DontCare
  //   switch (io.b_cpu.req.ctrl.op) {
  //     is (SWAP) {
  //       w_res := io.b_cpu.write.data
  //     }
  //     is (ADD) {
  //       w_res := io.b_cpu.write.data + r_cacheMem(w_miss_index)(w_miss_offset)
  //     }
  //     is (AND) {
  //       w_res := io.b_cpu.write.data & r_cacheMem(w_miss_index)(w_miss_offset)
  //     }
  //     is (OR) {
  //       w_res := io.b_cpu.write.data | r_cacheMem(w_miss_index)(w_miss_offset)
  //     }
  //     is (XOR) {
  //       w_res := io.b_cpu.write.data ^ r_cacheMem(w_miss_index)(w_miss_offset)
  //     }
  //     is (MAXU) {
  //       w_res := Mux((io.b_cpu.write.data > r_cacheMem(w_miss_index)(w_miss_offset)), io.b_cpu.write.data, r_cacheMem(w_miss_index)(w_miss_offset))
  //     }
  //     is (MAX) {
  //       w_res := Mux((io.b_cpu.write.data).asSInt > (r_cacheMem(w_miss_index)(w_miss_offset).asSInt), io.b_cpu.write.data, r_cacheMem(w_miss_index)(w_miss_offset))
  //     }
  //     is (MINU) {
  //       w_res := Mux((io.b_cpu.write.data < r_cacheMem(w_miss_index)(w_miss_offset)), io.b_cpu.write.data, r_cacheMem(w_miss_index)(w_miss_offset))
  //     }
  //     is (MIN) {
  //       w_res := Mux(((io.b_cpu.write.data).asSInt < r_cacheMem(w_miss_index)(w_miss_offset).asSInt), io.b_cpu.write.data, r_cacheMem(w_miss_index)(w_miss_offset))
  //     }
  //   }
  // } else None

  val alu     = if (p.useAmo) Some(Module(new Alu(p))) else None

  // if (p.useAmo) {
  //   io.b_cpu.req.ctrl.amo.get := DontCare
  // } 


  if (p.useAmo) {
  //  // val alu_input_data = Vec(2, UInt(p.nDataBit.W))
  //  // alu_input_data(0) := io.b_cpu.write.data
  //  // alu_input_data(1) := r_cacheMem(w_miss_index)(w_miss_offset)
  //  // val alu_input = new GenRVIO(p, io.b_cpu.req.ctrl.amo.get, alu_input_data)
  //  //val alu = Module(new Alu(p))
    alu.get.io.b_req.ctrl.get := io.b_cpu.req.ctrl.amo.get
    alu.get.io.b_req.data.get := io.b_cpu.write.data
    //alu.get.io.b_req.data(1).get := r_cacheMem(w_miss_index)(w_miss_offset)

  } else None



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

  if(useCoherency == false)

  {
    switch(r_state) {
      is(State.read) {
        when(io.b_cpu.req.valid)
        {
          when(hit) {
            //r_cpu_read_data := r_cacheMem(w_hit_index)(w_hit_offset) >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
            when (~r_cpu_read_valid | io.b_cpu.read.ready || r_cpu_read_valid | io.b_cpu.read.ready) {
              r_cpu_read_data := r_cacheMem(w_hit_index)(w_hit_offset) >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
              r_cpu_read_valid        := true.B
            }.otherwise{  
              r_cpu_read_valid        := false.B
            }

            // Ecriture/Lecture ready pour prochain cycle
            r_cpu_write_ready_cpu     := true.B
            io.b_mem.read.ready       := false.B
            r_state                   := State.read
          }.otherwise { 
            r_cpu_read_valid          := false.B
            r_cpu_write_ready_cpu     := false.B
            r_state                   := State.miss
          }       
        }.otherwise{
          r_cpu_read_valid            :=  (r_cpu_read_valid & ~io.b_cpu.read.ready)
        }
        when(io.b_cpu.write.valid && r_success_ato){

          // Mise à jour d'un champ spécifique en mémoire cache
          r_cacheMem(w_miss_index)(w_miss_offset) := w_write_data    
          r_tagMem(w_miss_index)                  := w_miss_tag
          r_validState(w_miss_index)               := State_MSI.s_modified // true.B 
        }
        r_count_req := 0.U
        r_count_data_mem := 0.U
        r_mem_read_ready := false.B
      }

      is(State.miss) {
        // Wait for data from memory and store it in the cache
        when(io.b_mem.read.valid) {
          r_cacheMem(w_miss_index)(r_count_data_mem) := io.b_mem.read.data
          when (r_count_data_mem < r_count_req){
          // Store received data into the cache
          r_count_data_mem := r_count_data_mem + 1.U
          }
          // If all expected data has been received, move back to State.read
          when(r_count_data_mem === r_count_req_max - 1.U) {
            r_state := State.read
            r_tagMem(w_miss_index) := w_miss_tag
            r_validState(w_miss_index) := State_MSI.s_shared //true.B 
            r_count_req := 0.U
            r_count_data_mem := 0.U
          }
        }
        // Only issue a new memory request if the previous request was respond
        when(io.b_mem.req.ready && (r_count_req < r_count_req_max) && ~(r_count_req_max - 1.U === r_count_req)) {
          r_count_req := r_count_req + 1.U
        }
        r_mem_read_ready := true.B    
        r_cpu_read_valid := false.B
      }
    }
  } else {

    ////////////////////// CONTROL FSM COHERENCY ////////////////////// 

    // Give the request to the controllor
    io.b_control.get.req_control <> io.b_cpu.req
    // r_validState(w_miss_index)  := ~ (io.b_control.get.rep_state === State_MSI.s_invalid) 
    switch(r_state) {
      is(State.read) {
        when(io.b_cpu.req.valid & io.b_control.get.req_control.ready)
        {
          when(hit) {
            //r_cpu_read_data := r_cacheMem(w_hit_index)(w_hit_offset) >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
            when (~r_cpu_read_valid | io.b_cpu.read.ready || r_cpu_read_valid | io.b_cpu.read.ready) {
              r_cpu_read_data := r_cacheMem(w_hit_index)(w_hit_offset) >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
              r_cpu_read_valid        := true.B
            }.otherwise{  
              r_cpu_read_valid        := false.B
            }

            // Ecriture/Lecture ready pour prochain cycle
            r_cpu_write_ready_cpu     := true.B
            io.b_mem.read.ready       := false.B
            r_state                   := State.read
          }.otherwise { 
            r_cpu_read_valid          := false.B
            r_cpu_write_ready_cpu     := false.B
            r_state                   := State.miss
          }       
        }.otherwise{
          r_cpu_read_valid            :=  (r_cpu_read_valid & ~io.b_cpu.read.ready)
        }
        when(io.b_cpu.write.valid){

          // Mise à jour d'un champ spécifique en mémoire cache
          r_cacheMem(w_miss_index)(w_miss_offset) := w_write_data    
          r_tagMem(w_miss_index)                  := w_miss_tag
          r_validState(w_miss_index)              := State_MSI.s_modified //true.B
        }
        r_count_req := 0.U
        r_count_data_mem := 0.U
        r_mem_read_ready := false.B
      }

      is(State.miss) {
        // Wait for data from memory and store it in the cache
        when(io.b_mem.read.valid) {
          r_cacheMem(w_miss_index)(r_count_data_mem) := io.b_mem.read.data
          when (r_count_data_mem < r_count_req){
          // Store received data into the cache
          r_count_data_mem := r_count_data_mem + 1.U
          }
          // If all expected data has been received, move back to State.read
          when(r_count_data_mem === r_count_req_max - 1.U) {
            r_state := State.read
            r_tagMem(w_miss_index) := w_miss_tag
            r_validState(w_miss_index) := State_MSI.s_shared //true.B
            r_count_req := 0.U
            r_count_data_mem := 0.U
          }
        }
        // Only issue a new memory request if the previous request was respond
        when(io.b_mem.req.ready && (r_count_req < r_count_req_max) && ~(r_count_req_max - 1.U === r_count_req)) {
          r_count_req := r_count_req + 1.U
        }
        r_mem_read_ready := true.B    
        r_cpu_read_valid := false.B
      }
    }

  }


  ///////////////////// CONTROL CPU ////////////////////// 

  when (r_state === State.read && io.b_cpu.req.valid){ // NEW REQ 
    r_addr_miss  := io.b_cpu.req.ctrl.addr  
  }
  
  // Propagation écriture main memory seulement si hit et write

  r_write_data_mem          := io.b_cpu.write.data   
  r_write_valid_mem         := io.b_cpu.write.valid  
  r_req_ctrl_op_mem         := io.b_cpu.req.ctrl.op
  r_req_hart_mem            := io.b_cpu.req.hart
  r_req_ctrl_size_mem       := io.b_cpu.req.ctrl.size
  
  if (useCoherency) {
    when(io.b_control.get.req_control.ready && hit && (r_state === State.read) && (io.b_cpu.req.ctrl.op === 1.U)){
      r_mem_req_valid           := io.b_cpu.req.valid
    }.otherwise {
      r_mem_req_valid           := false.B
    }
  } else {
    when((r_state === State.read) && hit && (io.b_cpu.req.ctrl.op === 1.U)){
      r_mem_req_valid           := io.b_cpu.req.valid
    }.otherwise {
      r_mem_req_valid           := false.B
    }
  }

  ///////////////////// ATOMIC OUTPUT ////////////////////// 

  // FORWARD DATA MEM LOAD/STORE

  if (p.useAmo) {
    when (io.b_cpu.req.ctrl.op === OP.AMO) { // RESULT AMO
      r_cpu_read_data      := alu.get.io.b_ack.data.get
    }.elsewhen(io.b_cpu.write.valid && (io.b_mem.req.ctrl.addr(addrWidth - 1, 3)  === io.b_cpu.req.ctrl.addr(addrWidth - 1, 3)))
    {
      r_cpu_read_data      := w_write_data >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
    }
  } else {
    when (io.b_cpu.write.valid && (io.b_mem.req.ctrl.addr(addrWidth - 1, 3)  === io.b_cpu.req.ctrl.addr(addrWidth - 1, 3)))
    {
      r_cpu_read_data      := w_write_data >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
    }
  }

  dontTouch(w_write_data)

  // Control signal cpu

  io.b_cpu.write.ready      := r_cpu_write_ready_cpu    
  io.b_cpu.read.data        := r_cpu_read_data
  io.b_cpu.read.valid       := r_cpu_read_valid

  if (useCoherency) {
    w_req_cpu_ready := io.b_control.get.req_control.ready & (io.b_cpu.req.valid | io.b_cpu.read.ready ) & (~io.b_cpu.read.ready | io.b_cpu.read.valid) & hit & (r_state === State.read) // & io.b_control.get.req_control.ready
  } else {
    w_req_cpu_ready :=  (io.b_cpu.req.valid | io.b_cpu.read.ready ) & (~io.b_cpu.read.ready | io.b_cpu.read.valid) & hit & (r_state === State.read) // & io.b_control.get.req_control.ready
  }
  
  io.b_cpu.req.ready := w_req_cpu_ready


  ////////////////////// CONTROL MEM //////////////////////

  when(r_state === State.miss){
    //Lecture de la memoire pour mettre à jour les donnés
    io.b_mem.req.ctrl.size   := SIZE.B4.U //r_req_ctrl_size_mem
    // io.b_mem.read.ready       := true.B    // r_mem_read_ready
    io.b_mem.req.hart        := r_req_hart_mem
    // io.b_mem.req.valid       := r_mem_req_valid
    io.b_mem.req.ctrl.op     := 0.U
    io.b_mem.req.ctrl.addr   := (((r_addr_miss(addrWidth - 1, offsetWidth + 2) << offsetWidth) + r_count_req) << 2).asUInt
  }.otherwise{ 
    //Ecriture dans la mémoire en propagent le registre
    // io.b_mem.req.valid         := r_mem_req_valid 
    io.b_mem.req.ctrl.size     :=      /*io.b_cpu.req.ctrl.size */  r_req_ctrl_size_mem
    io.b_mem.req.hart          :=      /*io.b_cpu.req.hart      */  r_req_hart_mem
    io.b_mem.req.ctrl.op       :=      /*io.b_cpu.req.ctrl.op   */  r_req_ctrl_op_mem  
    io.b_mem.req.ctrl.addr     :=      /*io.b_cpu.req.ctrl.addr */  r_addr_miss 
    io.b_mem.write.data        :=      /*io.b_cpu.write.data    */  r_write_data_mem 
    io.b_mem.write.valid       :=      /*io.b_cpu.write.valid   */  r_write_valid_mem
  }
  
  r_mem_req_ready := io.b_mem.req.ready

  // REQ MEM
  // r_state === State.miss && ~ (r_count_data_mem === r_count_req_max - 1.U) 

  // (r_state === State.miss && ~ (r_count_data_mem === r_count_req_max - 1.U) && b_mem.req.ready) || (r_state === State.read && io.b_cpu.req.valid && ~hit) 

  when(r_state === State.miss && ~ (r_count_data_mem === r_count_req_max - 1.U)){ //&& r_mem_req_ready)) // || (r_state === State.read && io.b_cpu.req.valid && ~hit) ){
    io.b_mem.req.valid := true.B
  }.otherwise {
    io.b_mem.req.valid := r_mem_req_valid // && ( io.b_mem.req.ctrl.op === 1.U ) //&&  r_mem_req_ready 
  }

  // MEM READ READY
  when(r_state === State.miss){
    io.b_mem.read.ready := true.B
  }.otherwise {
    io.b_mem.read.ready := false.B
  }

}

// Génération du Verilog

object DirectMappedCache extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new DirectMappedCache(MilkConfig0, 512, 64,false),
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
