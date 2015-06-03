package edu.gemini.itc.baseline

import edu.gemini.itc.baseline.util._
import edu.gemini.itc.shared.Flamingos2Parameters
import edu.gemini.spModel.gemini.flamingos2.Flamingos2._

/**
 * F2 baseline test fixtures.
 */
object BaselineF2 {

  lazy val Fixtures = KBandImaging ++ KBandSpectroscopy

  private lazy val KBandImaging = Fixture.kBandImgFixtures(List(
    Flamingos2Parameters(
      Filter.OPEN,                        // filter
      Disperser.NONE,                     // grism
      FPUnit.FPU_NONE,                    // FP mask
      ReadMode.FAINT_OBJECT_SPEC),        // read mode
    Flamingos2Parameters(
      Filter.H,                           // filter
      Disperser.NONE,                     // grism
      FPUnit.FPU_NONE,                    // FP mask
      ReadMode.MEDIUM_OBJECT_SPEC),       // read mode
    Flamingos2Parameters(
      Filter.J_LOW,                       // filter
      Disperser.NONE,                     // grism
      FPUnit.FPU_NONE,                    // FP mask
      ReadMode.BRIGHT_OBJECT_SPEC)        // read mode
  ))

  private lazy val KBandSpectroscopy = Fixture.kBandSpcFixtures(List(
    Flamingos2Parameters(
      Filter.J_LOW,
      Disperser.R1200JH,
      FPUnit.LONGSLIT_1,
      ReadMode.FAINT_OBJECT_SPEC),
    Flamingos2Parameters(
      Filter.H,
      Disperser.R1200HK,
      FPUnit.LONGSLIT_4,
      ReadMode.MEDIUM_OBJECT_SPEC),
    Flamingos2Parameters(
      Filter.H,
      Disperser.R3000,
      FPUnit.LONGSLIT_8,
      ReadMode.BRIGHT_OBJECT_SPEC)
  ))

}
