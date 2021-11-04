// Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.spModel.gemini.ghost

import edu.gemini.spModel.config2.Config

import java.time.Duration

import scalaz.Equal

final case class GhostCameras(
  red: GhostCamera.Red,
  blue: GhostCamera.Blue
) {

  val totalDuration: Duration = {
    val r = red.totalDuration
    val b = blue.totalDuration
    if (r.compareTo(b) > 0) r else b
  }

  val totalSeconds: Double =
    totalDuration.toMillis.toDouble / 1000.0

}

object GhostCameras {

  implicit val EqualGhostCameras: Equal[GhostCameras] =
    Equal.equalA

  def fromGhostComponent(g: GhostExposureTimeProvider): GhostCameras =
    GhostCameras(
      GhostCamera.Red.fromGhostComponent(g),
      GhostCamera.Blue.fromGhostComponent(g)
    )

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