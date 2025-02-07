package template.my_module

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
 /*
class MModuleTest extends AnyFlatSpec with ChiselScalatestTester {

  "M_module" should "perform MUL operation correctly" in {
    test(new M_module(32)) { c =>
      // Test de la multiplication (MUL)
      c.io.i_sel.poke(OP.MUL)      // Sélection de l'opération MUL
      c.io.op1.poke(4.U)           // Premier opérande = 4
      c.io.op2.poke(3.U)           // Deuxième opérande = 3
      c.clock.step(1)              // Un cycle d'horloge
      c.io.result.expect(12.U)     // Le résultat doit être 4 * 3 = 12
    }
  }

  
  it should "perform MULH operation correctly" in {
    test(new M_module(32)) { c =>
      // Test de la multiplication haute (MULH)
      c.io.i_sel.poke(OP.MULH)             // Sélection de l'opération MULH
      c.io.op1.poke((-5).S(32.W).asUInt)   // Premier opérande = -5 (signed) converti en UInt
      c.io.op2.poke(3.S(32.W).asUInt)      // Deuxième opérande = 3 (signed) converti en UInt
      c.clock.step(1)                      // Un cycle d'horloge
      c.io.result.expect((-15).S(32.W).asUInt) // Résultat signé = -15 converti en UInt pour la comparaison
    }
  }

  it should "perform DIV operation correctly" in {
    test(new M_module(32)) { c =>
      // Test de la division (DIV)
      c.io.i_sel.poke(OP.DIV)      // Sélection de l'opération DIV
      c.io.op1.poke(10.U)          // Premier opérande = 10
      c.io.op2.poke(2.U)           // Deuxième opérande = 2
      c.clock.step(1)              // Un cycle d'horloge
      c.io.result.expect(5.U)      // Le résultat doit être 10 / 2 = 5
    }
  }

  it should "handle division by zero in DIV" in {
    test(new M_module(32)) { c =>
      // Test de la division par zéro (DIV)
      c.io.i_sel.poke(OP.DIV)      // Sélection de l'opération DIV
      c.io.op1.poke(10.U)          // Premier opérande = 10
      c.io.op2.poke(0.U)           // Deuxième opérande = 0 (division par zéro)
      c.clock.step(1)              // Un cycle d'horloge
      c.io.result.expect(0.U)      // Le résultat doit être 0 (protection contre la division par zéro)
    }
  }

  it should "perform REM operation correctly" in {
    test(new M_module(32)) { c =>
      // Test du reste de la division (REM)
      c.io.i_sel.poke(OP.REM)      // Sélection de l'opération REM
      c.io.op1.poke(10.U)          // Premier opérande = 10
      c.io.op2.poke(3.U)           // Deuxième opérande = 3
      c.clock.step(1)              // Un cycle d'horloge
      c.io.result.expect(1.U)      // Le reste de 10 / 3 est 1
    }
  }

  it should "perform DIVU operation correctly" in {
    test(new M_module(32)) { c =>
      // Test de la division entière non signée (DIVU)
      c.io.i_sel.poke(OP.DIVU)     // Sélection de l'opération DIVU
      c.io.op1.poke(10.U)          // Premier opérande = 10
      c.io.op2.poke(2.U)           // Deuxième opérande = 2
      c.clock.step(1)              // Un cycle d'horloge
      c.io.result.expect(5.U)      // Le résultat doit être 10 / 2 = 5
    }
  }

  it should "handle division by zero in DIVU" in {
    test(new M_module(32)) { c =>
      // Test de la division par zéro (DIVU)
      c.io.i_sel.poke(OP.DIVU)     // Sélection de l'opération DIVU
      c.io.op1.poke(10.U)          // Premier opérande = 10
      c.io.op2.poke(0.U)           // Deuxième opérande = 0 (division par zéro)
      c.clock.step(1)              // Un cycle d'horloge
      c.io.result.expect(0.U)      // Le résultat doit être 0 (protection contre la division par zéro)
    }
  }

  it should "perform REMU operation correctly" in {
    test(new M_module(32)) { c =>
      // Test du reste de la division entière non signée (REMU)
      c.io.i_sel.poke(OP.REMU)     // Sélection de l'opération REMU
      c.io.op1.poke(10.U)          // Premier opérande = 10
      c.io.op2.poke(3.U)           // Deuxième opérande = 3
      c.clock.step(1)              // Un cycle d'horloge
      c.io.result.expect(1.U)      // Le reste de 10 / 3 est 1
    }
  }

}
*/