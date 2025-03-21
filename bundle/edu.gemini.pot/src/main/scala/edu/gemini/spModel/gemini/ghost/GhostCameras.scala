// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.spModel.gemini.ghost

import edu.gemini.spModel.config2.Config

import java.time.Duration

import scalaz.Equal

final case class GhostCameras(
  red:  GhostCamera.Red,
  blue: GhostCamera.Blue
) {

  /**
   * The dominant camera for the purposes of time calculation.  This is the
   * camera with the longest total exposure plus readout (if any).
   */
  val dominant: Option[GhostCamera] =
    red.totalTime.compareTo(blue.totalTime) match {
      case i if i < 0 => Some(blue)
      case i if i > 0 => Some(red)
      case _          => None
    }

  val dominantOrRed: GhostCamera =
    dominant.getOrElse(red)

  /**
   * Total exposure time for the dominant camera, or either camera if neither
   * is dominant.
   */
  val exposure: Duration =
    dominantOrRed.totalExposure

  /**
   * Total readout time for the dominant camera, or either camera if neither
   * is dominant.
   */
  val readout: Duration =
    dominantOrRed.totalReadout

  /**
   * Total exposure plus readout time for the dominant camera, or either camera
   * if neither is dominant.
   */
  val totalTime: Duration =
    dominantOrRed.totalTime

}

object GhostCameras {

  implicit val EqualGhostCameras: Equal[GhostCameras] =
    Equal.equalA

  /**
   * Reads the exposure time parameters from the "observe" system and creates
   * the corresponding GhostCameras instance.
   */
  def fromConfig(c: Config): GhostCameras =
    GhostCameras(
      GhostCamera.Red.fromConfig(c),
      GhostCamera.Blue.fromConfig(c)
    )

}
