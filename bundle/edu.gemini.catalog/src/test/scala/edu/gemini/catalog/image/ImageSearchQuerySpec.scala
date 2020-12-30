package edu.gemini.catalog.image

import edu.gemini.spModel.core.{Angle, Coordinates, Declination, RightAscension}
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import org.scalacheck.{Arbitrary, Shrink}
import org.scalatestplus.scalacheck.CheckerAsserting
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalatest.{Assertion, FlatSpec, Matchers}

import scalaz._

class ImageSearchQuerySpec extends FlatSpec with Matchers with ScalaCheckPropertyChecks with ImageCatalogArbitraries {
  // Do more tests to uncover edge cases
  implicit override val generatorDrivenConfig = PropertyCheckConfiguration(minSize = 5000, minSuccessful = 5000)

  case class TestCase(catalog: ImageCatalog, c: Coordinates, size: AngularSize, delta: Angle)

  // Custom arbitrary to properly scala the delta
  def testCase(min: Angle, max: Angle): Arbitrary[TestCase] = Arbitrary {
    for {
      catalog <- arbitrary[ImageCatalog]
      coords  <- arbitrary[Coordinates]
      delta   <- choose(min.toArcmins, max.toArcmins).map(Angle.fromArcmin)
      size    <- arbitrary[AngularSize]
    } yield TestCase(catalog, coords, size, delta)
  }

  "ImageSearchQuery" should
    "generate an appropriate filename" in {
      forAll { (catalog: ImageCatalog, c: Coordinates) =>
        val suffix = "fits.gz"
        val size = AngularSize(Angle.fromArcmin(8.5), Angle.fromArcmin(10))
        ImageSearchQuery(catalog, c, size, None).fileName(suffix) should fullyMatch regex ImageInFile.FileRegex
      }
    }

  "Comparing on distance" should "compare to itself" in {
      forAll { (catalog: ImageCatalog, c: Coordinates, size: AngularSize) =>
        ImageSearchQuery(catalog, c, size, None).isNearby(ImageSearchQuery(catalog, c, size, None)) shouldBe true
      }
    }
    it should "be nearby close coordinates in ra" in {
      forAll { (t: TestCase) =>
        val c1 = t.c.copy(ra = t.c.ra.offset(t.delta))
        ImageSearchQuery(t.catalog, t.c, t.size, None).isNearby(ImageSearchQuery(t.catalog, c1, t.size, None)) shouldBe true
      }(implicitly[PropertyCheckConfiguration], testCase(Angle.zero, ImageSearchQuery.maxDistance), implicitly[Shrink[TestCase]], implicitly[CheckerAsserting[Assertion]], implicitly, implicitly)
    }
    it should "be nearby close coordinates in dec" in {
      forAll { (t: TestCase) =>
        val c1 = t.c.copy(dec = t.c.dec.offset(t.delta)._1)
        ImageSearchQuery(t.catalog, t.c, t.size, None).isNearby(ImageSearchQuery(t.catalog, c1, t.size, None)) shouldBe true
      }(implicitly[PropertyCheckConfiguration], testCase(Angle.zero, ImageSearchQuery.maxDistance), implicitly[Shrink[TestCase]], implicitly[CheckerAsserting[Assertion]], implicitly, implicitly)
    }
    it should "be symmetric" in {
      forAll { (t: TestCase) =>
        val c1 = t.c.copy(dec = t.c.dec.offset(t.delta)._1)
        ImageSearchQuery(t.catalog, t.c, t.size, None).isNearby(ImageSearchQuery(t.catalog, c1, t.size, None)) shouldBe ImageSearchQuery(t.catalog, c1, t.size, None).isNearby(ImageSearchQuery(t.catalog, t.c, t.size, None))
      }(implicitly[PropertyCheckConfiguration], testCase(Angle.zero, Angle.fromDegrees(359.99)), implicitly[Shrink[TestCase]], implicitly[CheckerAsserting[Assertion]], implicitly, implicitly)
    }
    it should "work near zero" in {
      // Special case when the diff is very close to zero but negative
      val c1 = Coordinates(RightAscension.fromAngle(Angle.fromDegrees(263.94917083333326)),Declination.fromAngle(Angle.fromDegrees(329.5302805555556)).getOrElse(Declination.zero))
      val c2 = Coordinates(RightAscension.fromAngle(Angle.fromDegrees(263.94917)),Declination.fromAngle(Angle.fromDegrees(329.53027999999995)).getOrElse(Declination.zero))
      val size = AngularSize(Angle.fromArcmin(8.5), Angle.fromArcmin(10))
      ImageSearchQuery(DssGemini, c1, size, None).isNearby(ImageSearchQuery(DssGemini, c2, size, None)) shouldBe true
      ImageSearchQuery(DssGemini, c2, size, None).isNearby(ImageSearchQuery(DssGemini, c1, size, None)) shouldBe true
    }

}
