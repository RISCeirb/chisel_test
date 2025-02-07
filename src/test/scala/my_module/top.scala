package template.my_module

import chiseltest._
import chisel3._
import org.scalatest.flatspec.AnyFlatSpec

// Test du module top
class TopTest extends AnyFlatSpec with ChiselScalatestTester {
  "top module" should "run for 1000 cycles" in {
    test(new top()).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      c.clock.step(3000) // Simule 1000 cycles d'horloge
    }
  }
}
