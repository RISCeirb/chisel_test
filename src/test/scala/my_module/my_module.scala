package template.my_module

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// Définition du module Counter
class Counter(val width: Int) extends Module {
  val io = IO(new Bundle {
    val count = Output(UInt(width.W))  // Sortie du compteur
    val enable = Input(Bool())         // Signal pour activer l'incrémentation
  })

  // Déclaration d'un registre pour le compteur
  val counterReg = RegInit(0.U(width.W))  // Initialisation du compteur à 0

  // Logique du compteur
  when (io.enable) {
    counterReg := counterReg + 1.U  // Incrémenter le compteur quand io.enable est vrai
  }

  // Assigner la sortie du compteur
  io.count := counterReg
}

// Test du module Counter
class CounterTest extends AnyFlatSpec with ChiselScalatestTester {
  "Counter" should "increment the count when enable is true" in {
    test(new Counter(4)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      // Vérifier la valeur initiale du compteur
      c.io.count.expect(0.U)

      // Activer l'incrémentation et vérifier les valeurs
      c.io.enable.poke(true.B)  // Activer l'incrémentation
      c.clock.step(1)           // Avancer d'une période d'horloge
      c.io.count.expect(1.U)    // Le compteur doit être à 1 après un cycle

      c.clock.step(1)           // Avancer d'une autre période d'horloge
      c.io.count.expect(2.U)    // Le compteur doit être à 2 après deux cycles

      // Désactiver l'incrémentation et vérifier que le compteur reste inchangé
      c.io.enable.poke(false.B) 
      c.clock.step(1)           // Avancer d'une période d'horloge
      c.io.count.expect(2.U)    // Le compteur ne doit pas changer, il doit rester à 2
    }
  }

  it should "not increment the count when enable is false" in {
    test(new Counter(4)).withAnnotations(Seq(WriteVcdAnnotation)) { c =>
      // Vérifier la valeur initiale du compteur
      c.io.count.expect(0.U)

      // Désactiver l'incrémentation et vérifier que le compteur reste à 0
      c.io.enable.poke(false.B)
      c.clock.step(1)           // Avancer d'une période d'horloge
      c.io.count.expect(0.U)    // Le compteur ne doit pas changer, il doit rester à 0

      c.clock.step(1)           // Avancer d'une autre période d'horloge
      c.io.count.expect(0.U)    // Le compteur doit rester à 0
    }
  }
}
