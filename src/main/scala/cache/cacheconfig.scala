package herd.core.betizu

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

import herd.common.mem.milk._
import herd.common.gen._


case class CacheConfig(
  cacheSize: Int,
  lineSize: Int,
  useHASH: Boolean
)

object mycacheconfig extends CacheConfig(
  cacheSize = 1024, 
  lineSize = 32,
  useHASH = false
)