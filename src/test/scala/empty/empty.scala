package template.empty

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec


class EmptyTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Empty"
  // test class body here
  "Empty test" should "pass" in {
    // test case body here
    test(new Empty(32)).withAnnotations (Seq( /*VerilatorBackendAnnotation,*/ WriteVcdAnnotation )){ dut =>
      // test body here
      dut.io.i_in.poke(1.U)
      dut.clock.step()
      dut.clock.step()
    }
  }

  "Empty test 2" should "pass" in {
    // test case body here
    test(new Empty(32)).withAnnotations (Seq( /*VerilatorBackendAnnotation,*/ WriteVcdAnnotation )){ dut =>
      // test body here
      dut.io.i_in.poke(4.U)
      dut.clock.step(20)
    }
  }

  "Empty test 3" should "fail" in {
    // test case body here
    test(new Empty(8)).withAnnotations (Seq( /*VerilatorBackendAnnotation,*/ WriteVcdAnnotation )){ dut =>
      // test body here
      dut.io.i_in.poke(5.U)
      dut.clock.step(20)
      dut.io.o_out.expect(4.U)
    }
  }
}
