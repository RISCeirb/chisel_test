package herd.core.betizu

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

import herd.common.mem.milk._
import herd.common.gen._

// Cache Direct Mappé avec bus
class DirectMappedCache(p: MilkParams, /*cacheSize: Int, lineSize: Int,*/z: CacheConfig, useCoherency: Boolean) extends Module {
  val io = IO(new Bundle {
    val b_mem       = new MilkIO(p)    
    val b_cpu       = Flipped(new MilkIO(p)) 
    val b_control   = if (useCoherency) Some(new Bus_control(p)) else None
  })

  val addrWidth     = p.nAddrBit
  val dataWidth     = p.nDataBit

  val cacheSize     = z.cacheSize
  val lineSize      = z.lineSize

  // Paramètres du cache
  val numSets       = cacheSize / lineSize
  val indexWidth    = log2Ceil(numSets)
  val offset        = lineSize / (addrWidth / 8)
  val offsetWidth   = log2Ceil(lineSize / (addrWidth / 8))
  val tagWidth      = addrWidth - indexWidth - offsetWidth // BY BYTE

  // Mémoires du cache
  val r_cacheMem    = RegInit(VecInit(Seq.fill(numSets)(VecInit(Seq.fill(lineSize / (dataWidth / 8))(0.U(dataWidth.W)))))) 
  val r_tagMem      = RegInit(VecInit(Seq.fill(numSets)(0.U(tagWidth.W)))) 

  // val r_validBits   = RegInit(VecInit(Seq.fill(numSets)(false.B))) 

  val r_validState = RegInit(VecInit(Seq.fill(numSets)(State_MSI.s_invalid))) 

  // Découpage de l'adresse

  val w_hit_index   = io.b_cpu.req.ctrl.addr(indexWidth + offsetWidth - 1 + 2 , offsetWidth +2)
  val w_hit_tag     = io.b_cpu.req.ctrl.addr(addrWidth - 1, indexWidth + offsetWidth +2)
  val w_hit_offset  = io.b_cpu.req.ctrl.addr(offsetWidth +2 - 1, 0 +2)

  //val r_addr_miss   = RegInit(io.b_cpu.req.ctrl.addr)
  val r_addr_miss = RegInit(0.U(addrWidth.W))

  val w_miss_index  = r_addr_miss(indexWidth + offsetWidth - 1 + 2 , offsetWidth +2)
  val w_miss_tag    = r_addr_miss(addrWidth - 1, indexWidth + offsetWidth +2)
  val w_miss_offset = r_addr_miss(offsetWidth +2 - 1, 0 +2)

  dontTouch(w_hit_index)
  dontTouch(w_hit_tag)
  dontTouch(w_miss_index)
  dontTouch(w_miss_tag)

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

  /////////////////////////   ATOMIC  ////////////////////////////

  val r_lock          = RegInit(VecInit(Seq.fill(p.nHart)(false.B)))                
  val r_lock_addr     = RegInit(VecInit(Seq.fill(p.nHart)(0.U(addrWidth.W))))  
  val r_success_ato   = RegInit(false.B) 
  val w_lr_sc         = Wire(UInt(1.W))
  w_lr_sc            := 0.U

  val success_sc_lr = ((io.b_cpu.req.ctrl.op === OP.SC) && r_lock(io.b_cpu.req.hart) && (r_lock_addr(io.b_cpu.req.hart) === io.b_cpu.req.ctrl.addr))// permet de traiter SC comme READ ou WRITE

  ///////////////////// READ/WRITE SYNCHRO ///////////////////////

  val isRead = if (p.useAmo){ 
    io.b_cpu.req.ctrl.op === OP.R || io.b_cpu.req.ctrl.op === OP.LR  || ((success_sc_lr === 0.U) && (io.b_cpu.req.ctrl.op === OP.SC))
  } else {
    io.b_cpu.req.ctrl.op === OP.R
  }

  val isWrite = if (p.useAmo){ 
    (io.b_cpu.req.ctrl.op === OP.W) | (io.b_cpu.req.ctrl.op === OP.AMO) | success_sc_lr
  } else {
    io.b_cpu.req.ctrl.op === OP.W
  }

  val w_synchro_mem_write = Wire(Bool())

  w_synchro_mem_write := isRead || (isWrite && io.b_mem.req.ready)

  dontTouch(w_synchro_mem_write)

  ///////////////////////// COHERENCY /////////////////////////////

  val isModified = r_validState(w_hit_index) === State_MSI.s_modified
  val isShared   = r_validState(w_hit_index) === State_MSI.s_shared
  val isInvalid  = r_validState(w_hit_index) === State_MSI.s_invalid


  dontTouch(isModified)
  dontTouch(isShared)
  dontTouch(isInvalid)


  val synchro_cpu_mem = if (useCoherency) {
    isInvalid || isShared && isRead || isModified 
  } else {
    true.B
  }

  val req_to_directory = if (useCoherency) {
    isInvalid || (isShared && isWrite)  
  } else {
    false.B
  }

  val synchro_directory  =  if (useCoherency) {
    io.b_control.get.req_control.ready
  } else {
    true.B
  }

  val w_controller_index =  if (useCoherency) {
                              io.b_control.get.addr(indexWidth + offsetWidth - 1 + 2 , offsetWidth +2)
                            } else {
                              WireDefault(0.U(indexWidth.W))
                            }
  val w_controller_tag   =  if (useCoherency) {
                              io.b_control.get.addr(addrWidth - 1, indexWidth + offsetWidth +2)
                            } else {
                              WireDefault(0.U(indexWidth.W))
                            }

  if (useCoherency) {
    dontTouch(w_controller_index)
    dontTouch(w_controller_tag)
    dontTouch(req_to_directory)
  } else None

  val r_index_invalid = RegInit(w_controller_index) 
  val r_invalid       = RegInit(false.B)
  val r_new_state     = RegInit(State_MSI.s_invalid)

  val go_invalid      = r_invalid && ~io.b_mem.req.valid // on va dans l'état d'invalidation si on a pas de requete mem en cours


  ///////////////////////// HIT CHECK /////////////////////////////

  // Vérification du hit

  val hit = (r_validState(w_hit_index) =/= State_MSI.s_invalid) && (r_tagMem(w_hit_index) === w_hit_tag) 


  // Compteur pour gérer l'offset lors des transferts mémoire
  val r_count_req           = RegInit(0.U((offsetWidth+1).W))
  val r_count_data_mem      = RegInit(0.U(offsetWidth.W))

  val r_count_req_max = Wire(UInt(log2Ceil(lineSize / (dataWidth / 8) + 1).W))
  r_count_req_max := (lineSize / (dataWidth / 8)).U

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


  // REGISTER INIT

    
  val r_mem_req_ctrl_amo = if (p.useAmo) RegInit(io.b_cpu.req.ctrl.amo.get) else RegInit(0.U)
  
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
    when (hit && w_synchro_mem_write && synchro_cpu_mem && r_state === State.read) {
      // Gestion de l'écriture du lock lors d'une opération Load Reserved (LR)
      when(io.b_cpu.req.ctrl.op === OP.LR) {
        r_lock(io.b_cpu.req.hart) := true.B
        r_lock_addr(io.b_cpu.req.hart) := io.b_cpu.req.ctrl.addr
      }
      // Libération du lock sur Store (W) ou Store Conditional (SC)
      when((io.b_cpu.req.ctrl.op === OP.W || io.b_cpu.req.ctrl.op === OP.SC) && (r_lock_addr(io.b_cpu.req.hart) === io.b_cpu.req.ctrl.addr)) {
        r_lock(io.b_cpu.req.hart) := false.B
        r_lock_addr(io.b_cpu.req.hart) := 0.U
      }
      when(success_sc_lr){
        w_lr_sc := 0.U // Return zero value // Success 
      }.otherwise {
        w_lr_sc := 1.U // Return non zero value  // Fail
      }
      r_success_ato := ~ w_lr_sc
    }

  } else {
    // Si useAmo est désactivé, aucune opération de lock n'est effectuée.
    None
  }

  ////////////////////// ATOMIC ALU ////////////////////////

  val alu     = if (p.useAmo) Some(Module(new Alu(p))) else None

  val r_atomic_res     = RegInit(0.U(dataWidth.W))


  if (p.useAmo) {

    val alu_input_data = Wire(Vec(2, UInt(p.nDataBit.W)))

    alu_input_data(1) := r_cacheMem(w_miss_index)(w_miss_offset)
    alu_input_data(0) := io.b_cpu.write.data

    r_mem_req_ctrl_amo := io.b_cpu.req.ctrl.amo.get

    val amo_type = r_mem_req_ctrl_amo

    // INPUT CONNECT
    alu.get.io.b_req.valid    := io.b_cpu.req.valid 
    alu.get.io.b_req.hart     := io.b_cpu.req.hart
    alu.get.io.b_req.ctrl.get := amo_type
    alu.get.io.b_req.data.get := alu_input_data

    // OUTPUT

    alu.get.io.b_ack <> DontCare

    r_atomic_res := alu.get.io.b_ack.data.get

  } else None


  ///////////////////// NON ALIGN WRITE ////////////////////// 

  val w_non_align_write = Wire(UInt(32.W))
  w_non_align_write := 0.U

  switch(r_req_ctrl_size_mem){
    is(SIZE.B4.U){ 
      w_non_align_write := io.b_cpu.write.data
    }
    is(SIZE.B2.U){ 
      switch(r_addr_miss(1,0)){
        is(0.U){
          w_non_align_write := Cat(r_cacheMem(w_miss_index)(w_miss_offset)(31,16),io.b_cpu.write.data(15,0))
        }
        is(1.U){
          w_non_align_write := Cat(r_cacheMem(w_miss_index)(w_miss_offset)(31,24),io.b_cpu.write.data(15,0),r_cacheMem(w_miss_index)(w_miss_offset)(7,0))
        }
        is(2.U){
          w_non_align_write := Cat(io.b_cpu.write.data(15,0),r_cacheMem(w_miss_index)(w_miss_offset)(15,0))
        }
      }
    }
    is(SIZE.B1.U){ 
      switch(r_addr_miss(1,0)){
        is(0.U){
            w_non_align_write := Cat(r_cacheMem(w_miss_index)(w_miss_offset)(31,8),io.b_cpu.write.data(7,0))
        }
        is(1.U){
            w_non_align_write := Cat(r_cacheMem(w_miss_index)(w_miss_offset)(31,16),io.b_cpu.write.data(7,0),r_cacheMem(w_miss_index)(w_miss_offset)(7,0))
        }
        is(2.U){
            w_non_align_write := Cat(r_cacheMem(w_miss_index)(w_miss_offset)(31,24),io.b_cpu.write.data(7,0),r_cacheMem(w_miss_index)(w_miss_offset)(15,0))
        }
        is(3.U){
            w_non_align_write := Cat(io.b_cpu.write.data(7,0),r_cacheMem(w_miss_index)(w_miss_offset)(23,0))
        }
      }
    }
  }

  //////////////////// CONTROL WRITE //////////////////////

  val w_write_data_mem = Wire(UInt(32.W))
  w_write_data_mem := 0.U

  if (p.useAmo){
    when ((r_state === State.read) & io.b_cpu.write.valid) {
      when (r_req_ctrl_op_mem === OP.W || (r_req_ctrl_op_mem === OP.SC & r_success_ato)){
        r_cacheMem(w_miss_index)(w_miss_offset):= w_non_align_write 
        w_write_data_mem                       := io.b_cpu.write.data
      }.elsewhen(r_req_ctrl_op_mem === OP.AMO){
        r_cacheMem(w_miss_index)(w_miss_offset):= alu.get.io.b_ack.data.get 
        w_write_data_mem                       := alu.get.io.b_ack.data.get 
      }
      r_tagMem(w_miss_index)                   := w_miss_tag
      r_validState(w_miss_index)               := State_MSI.s_modified 
    }
  } else {
    when ((r_state === State.read) & io.b_cpu.write.valid) {
      r_cacheMem(w_miss_index)(w_miss_offset)  := w_non_align_write    
      r_tagMem(w_miss_index)                   := w_miss_tag
      r_validState(w_miss_index)               := State_MSI.s_modified 
    }
  }

  dontTouch(w_write_data_mem)

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
              r_cpu_read_data         := r_cacheMem(w_hit_index)(w_hit_offset) >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
              r_cpu_read_valid        := true.B
            }.otherwise{  
              r_cpu_read_valid        := false.B
            }

            // Ecriture/Lecture ready pour prochain cycle
            r_cpu_write_ready_cpu     := true.B
            r_state                   := State.read
          }.otherwise { 
            r_cpu_read_valid          := false.B
            r_cpu_write_ready_cpu     := false.B
            r_state                   := State.miss
          }       
        }.otherwise{
          r_cpu_read_valid            :=  (r_cpu_read_valid & ~io.b_cpu.read.ready)
        }
        r_count_req := 0.U
        r_count_data_mem := 0.U
        r_mem_read_ready := false.B
      }

      is(State.miss) {
        // Wait for data from memory and store it in the cache
        r_tagMem(w_miss_index) := w_miss_tag
        when(io.b_mem.read.valid) {
          r_cacheMem(w_miss_index)(r_count_data_mem) := io.b_mem.read.data
          when (r_count_data_mem < r_count_req){
          // Store received data into the cache
          r_count_data_mem := r_count_data_mem + 1.U
          }
          // If all expected data has been received, move back to State.read
          when(r_count_data_mem === r_count_req_max - 1.U) {
            r_state := State.read
            r_validState(w_miss_index) := State_MSI.s_shared //true.B 
            r_count_req := 0.U
            r_count_data_mem := 0.U
          }
        }
        // Only issue a new memory request if the previous request was respond
        when(io.b_mem.req.ready && (r_count_req < r_count_req_max) && ~(r_count_req_max === r_count_req) /*&& io.b_mem.read.valid*/) {
          r_count_req := r_count_req + 1.U
        }
        r_mem_read_ready := true.B    
        r_cpu_read_valid := false.B
      }
    }
  } else {

    ////////////////////// CONTROL FSM COHERENCY ////////////////////// 


    //////////////////////////  STATE CHANGE  /////////////////////////

    val replace     = (w_controller_tag === r_tagMem(w_controller_index))
    val collision   = (w_controller_tag === r_tagMem(w_controller_index) && (w_controller_tag === w_hit_tag) && (w_controller_index === w_hit_index))
    val downgrade   = r_validState(w_controller_index) === State_MSI.s_modified && io.b_control.get.rep_state =/= State_MSI.s_modified

    dontTouch(replace)
    dontTouch(collision)
    dontTouch(downgrade)

    //when(io.b_control.get.req_control.ready){
      when(replace /*&& ~ collision*/){ // permet d'actualiser les états des lignes qui ne sont pas une requetes cpu courantes
        r_validState(w_controller_index)        := io.b_control.get.rep_state
      }
      //}.elsewhen(replace && collision && ~ downgrade){ // actualise la ligne courante si on passe à un état plus priviligié
      //  r_validState(w_controller_index)        := io.b_control.get.rep_state
      //}.elsewhen(replace && collision /*&& downgrade*/){ // état moins priviligié, routine d'invalidation 
        //r_index_invalid := w_controller_index
        //r_invalid       := true.B
        //r_new_state     := io.b_control.get.rep_state
      //}
    //}

    ///////////////////////////  LOCK FREE  ///////////////////////////

    val r_snoop_inval = RegInit(VecInit(Seq.fill(p.nHart)(false.B)))

    if (p.useAmo) {
      for (i <- 0 until p.nHart) {
        when(r_lock(i) && (io.b_control.get.rep_state === State_MSI.s_invalid) && r_lock_addr(i) === io.b_control.get.addr){
            r_lock(i)      := false.B
            r_lock_addr(i) := 0.U
            r_snoop_inval(i) := true.B
        }.otherwise {
          r_snoop_inval(i) := false.B
        }
      }
      dontTouch(r_snoop_inval) // debug signal
    }

    ///////////////////////// READ/WRITE HIT/MISS /////////////////////

    // Give the request to the controllor
    when (req_to_directory) {
      io.b_control.get.req_control.valid      := io.b_cpu.req.valid
    }.otherwise {
      io.b_control.get.req_control.valid      := false.B
    }

    io.b_control.get.req_control.ctrl.hart        := io.b_cpu.req.hart
    io.b_control.get.req_control.ctrl.op          := isWrite 
    io.b_control.get.req_control.ctrl.addr        := io.b_cpu.req.ctrl.addr
    io.b_control.get.ack_write                    := io.b_mem.write.valid 
    io.b_control.get.ack_invalid                  := (~ r_invalid) || (r_invalid && replace && collision) // forwarding 

    switch(r_state) {
      is(State.read) {
        when(io.b_cpu.req.valid & synchro_directory){
          when(hit & synchro_cpu_mem) {
            when (~r_cpu_read_valid | io.b_cpu.read.ready || r_cpu_read_valid | io.b_cpu.read.ready) {
              r_cpu_read_data := r_cacheMem(w_hit_index)(w_hit_offset) >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
              r_cpu_read_valid        := true.B
            }.otherwise{  
              r_cpu_read_valid        := false.B
            }

            // Ecriture/Lecture ready pour prochain cycle
            r_cpu_write_ready_cpu     := true.B
            r_state                   := State.read
          }.otherwise { 
            r_cpu_read_valid          := false.B
            r_cpu_write_ready_cpu     := false.B
            r_state                   := State.miss
          }       
        }.otherwise{
          r_cpu_read_valid            :=  (r_cpu_read_valid & ~io.b_cpu.read.ready)
        }
        r_count_req := 0.U
        r_count_data_mem := 0.U
        r_mem_read_ready := false.B
      }

      is(State.miss) {
        // Wait for data from memory and store it in the cache
        r_tagMem(w_miss_index) := w_miss_tag
        when(io.b_mem.read.valid) {
          r_cacheMem(w_miss_index)(r_count_data_mem) := io.b_mem.read.data
          when (r_count_data_mem < r_count_req){
          // Store received data into the cache
          r_count_data_mem := r_count_data_mem + 1.U
          }
          // If all expected data has been received, move back to State.read
          when(r_count_data_mem === r_count_req_max - 1.U) {
              r_state          := State.read
              r_count_req      := 0.U
              r_count_data_mem := 0.U
          }
        }
        // Only issue a new memory request if the previous request was respond
        when(io.b_mem.req.ready && (r_count_req < r_count_req_max) && ~(r_count_req_max  === r_count_req) /*&& io.b_mem.read.valid*/) {
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

  val r_enable_a_mem       = RegInit(false.B)

  when (~ success_sc_lr && (io.b_cpu.req.ctrl.op === OP.SC)) {
    r_enable_a_mem := false.B
  }.otherwise{
    r_enable_a_mem := true.B
  }

  when(r_write_valid_mem && ~io.b_mem.write.ready) {
    // DO NOTHING 
  }.otherwise{
    r_write_data_mem          := w_write_data_mem
    r_write_valid_mem         := io.b_cpu.write.valid && r_enable_a_mem
  }

  io.b_mem.write.data       :=  r_write_data_mem     
  io.b_mem.write.valid      :=  r_write_valid_mem

  r_req_ctrl_op_mem         := io.b_cpu.req.ctrl.op
  r_req_hart_mem            := io.b_cpu.req.hart
  r_req_ctrl_size_mem       := io.b_cpu.req.ctrl.size
  

  //////////////// REQ MEM ////////////////
  if (useCoherency) {
    if (p.useAmo) {
      when(synchro_directory & ~ w_synchro_mem_write && synchro_cpu_mem && hit && (r_state === State.read) && isWrite){
        r_mem_req_valid           := io.b_cpu.req.valid
      }.otherwise {
        r_mem_req_valid           := false.B
      }
    } else {
      when(synchro_directory & synchro_cpu_mem && (r_state === State.read) && hit && (io.b_cpu.req.ctrl.op === OP.W)){
        r_mem_req_valid           := io.b_cpu.req.valid
      }.otherwise {
        r_mem_req_valid           := false.B
      }
    }
  } else {
    if (p.useAmo) {
      when( ~ w_synchro_mem_write && (r_state === State.read) && hit && isWrite){
        r_mem_req_valid           := io.b_cpu.req.valid
      }.otherwise {
        r_mem_req_valid           := false.B
      }
    } else {
      when((r_state === State.read) && hit && (io.b_cpu.req.ctrl.op === OP.W)){
        r_mem_req_valid           := io.b_cpu.req.valid
      }.otherwise {
        r_mem_req_valid           := false.B
      }
    }
  }
  
  when(r_state === State.miss && ~ (r_count_req === r_count_req_max)){
      io.b_mem.req.valid := true.B
  }.otherwise {
      io.b_mem.req.valid := r_mem_req_valid && synchro_cpu_mem && synchro_directory && hit
  }


  ///////////////////// ATOMIC OUTPUT ////////////////////// 

  // FORWARD DATA MEM LOAD/STORE AND NON-ALIGN-LOAD

  if (p.useAmo) {
    when (io.b_cpu.req.ctrl.op === OP.SC) {
      r_cpu_read_data      := w_lr_sc
    }.elsewhen (io.b_cpu.write.valid && (io.b_mem.req.ctrl.addr(addrWidth - 1, 2)  === io.b_cpu.req.ctrl.addr(addrWidth - 1, 2)))
    {
      r_cpu_read_data      := w_non_align_write >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
    }
  } else { 
    when (io.b_cpu.write.valid && (io.b_mem.req.ctrl.addr(addrWidth - 1, 2)  === io.b_cpu.req.ctrl.addr(addrWidth - 1, 2)))
    {
      r_cpu_read_data      := w_non_align_write >> (io.b_cpu.req.ctrl.addr(1,0)##0.U(3.W)) //SHIFT FOR LH,LHU,LB,LBU
    }
  }

  dontTouch(w_non_align_write)

  // Control signal cpu

  io.b_cpu.read.data        := r_cpu_read_data
  io.b_cpu.write.ready      := r_cpu_write_ready_cpu    
  io.b_cpu.read.valid       := r_cpu_read_valid

  if (useCoherency) {
    w_req_cpu_ready :=   synchro_directory & (io.b_cpu.req.valid | io.b_cpu.read.ready ) & (~io.b_cpu.read.ready | io.b_cpu.read.valid) & hit & (r_state === State.read) 
  } else {
    w_req_cpu_ready :=  (io.b_cpu.req.valid | io.b_cpu.read.ready ) & (~io.b_cpu.read.ready | io.b_cpu.read.valid) & hit & (r_state === State.read) 
  }

  
  if (useCoherency) { // SYNC DIRECTORY ON WRITE REQ
    io.b_cpu.req.ready := w_req_cpu_ready && synchro_cpu_mem && w_synchro_mem_write 
  } else {
    io.b_cpu.req.ready := w_req_cpu_ready && w_synchro_mem_write
  }

  dontTouch(w_req_cpu_ready)

  ////////////////////// CONTROL MEM //////////////////////


  if (p.useAmo) {
    when(r_state === State.miss){
      //Lecture de la memoire pour mettre à jour les donnés
      io.b_mem.req.ctrl.size      := SIZE.B4.U //r_req_ctrl_size_mem
      io.b_mem.req.hart           := r_req_hart_mem
      io.b_mem.req.ctrl.op        := 0.U
      io.b_mem.req.ctrl.addr      := (((r_addr_miss(addrWidth - 1, offsetWidth + 2) << offsetWidth) + r_count_req) << 2).asUInt
    }.otherwise{ 
      //Ecriture dans la mémoire en propagent le registre
      io.b_mem.req.ctrl.size     :=    r_req_ctrl_size_mem
      io.b_mem.req.hart          :=    r_req_hart_mem
      io.b_mem.req.ctrl.addr     :=    r_addr_miss 
      when ((r_req_ctrl_op_mem === OP.SC & r_success_ato) || r_req_ctrl_op_mem === OP.AMO || r_req_ctrl_op_mem === OP.W) {
        io.b_mem.req.ctrl.op     :=    OP.W
      }
    }
    
  } else {
    when(r_state === State.miss){
      //Lecture de la memoire pour mettre à jour les donnés
      io.b_mem.req.ctrl.size   := SIZE.B4.U //r_req_ctrl_size_mem
      io.b_mem.req.hart        := r_req_hart_mem
      io.b_mem.req.ctrl.op     := 0.U
      io.b_mem.req.ctrl.addr   := (((r_addr_miss(addrWidth - 1, offsetWidth + 2) << offsetWidth) + r_count_req) << 2).asUInt
    }.otherwise{ 
      //Ecriture dans la mémoire en propagent le registre
      io.b_mem.req.ctrl.size     :=   r_req_ctrl_size_mem
      io.b_mem.req.hart          :=   r_req_hart_mem
      io.b_mem.req.ctrl.op       :=   r_req_ctrl_op_mem  
      io.b_mem.req.ctrl.addr     :=   r_addr_miss 
    }
  }

  // MEM READ READY
  when(r_state === State.miss){
    io.b_mem.read.ready := true.B
  }.otherwise {
    io.b_mem.read.ready := false.B
  }

  dontTouch(io.b_mem.write.ready)

}

// Génération du Verilog

object DirectMappedCache extends App {
  _root_.circt.stage.ChiselStage.emitSystemVerilog(
    new DirectMappedCache(MilkConfig0, mycacheconfig, false),
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
