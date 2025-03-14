package edu.gemini.phase2.skeleton.factory

import edu.gemini.spModel.core.MagnitudeBand
import edu.gemini.model.p1.immutable.{Igrins2Blueprint, Igrins2NoddingOption, Igrins2TelluricStars}
import org.scalacheck.Prop._
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary._
import org.specs2.ScalaCheck
import org.specs2.mutable.SpecificationLike

class Igrins2BlueprintTest extends TemplateSpec("IGRINS-2_BP.xml") with SpecificationLike with ScalaCheck {

  implicit val ArbitraryNodding: Arbitrary[Igrins2NoddingOption] =
    Arbitrary(Gen.oneOf(Igrins2NoddingOption.values))

  implicit val ArbitraryIgrins2Blueprint: Arbitrary[Igrins2Blueprint] =
    Arbitrary {
      for {
        n <- arbitrary[Igrins2NoddingOption]
        s <- arbitrary[Igrins2TelluricStars]
      } yield Igrins2Blueprint(n, s)
    }

  private val AnyBand: MagnitudeBand = MagnitudeBand.R // required, but not used

  "Igrins2" should {
    "include all notes" in {
      forAll { (b: Igrins2Blueprint) =>
        expand(proposal(b, Nil, AnyBand)) { (_, sp) =>
          val notes = List(
            "Phase II Checklist",
            "Observer Instructions"
          )
          groups(sp).forall(tg => notes.forall(n => existsNote(tg, n)))
        }
      }
    }

    "include correct observations for nodding option" in {
      forAll { (b: Igrins2Blueprint) =>
        val incl = Set(1, 2, 5, if (b.nodding == Igrins2NoddingOption.NodAlongSlit) 3 else 4)
        expand(proposal(b, Nil, AnyBand)) { (_, sp) =>
          groups(sp).forall { tg =>
            libs(tg) == incl
          }
        }
      }
    }
  }


}
