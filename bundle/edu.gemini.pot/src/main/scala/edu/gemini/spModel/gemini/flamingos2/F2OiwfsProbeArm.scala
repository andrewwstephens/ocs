package edu.gemini.spModel.gemini.flamingos2

import java.awt.Shape
import java.awt.geom.{Area, Point2D, Rectangle2D}

import edu.gemini.shared.util.immutable.ImPolygon
import edu.gemini.skycalc.Offset
import edu.gemini.spModel.core
import edu.gemini.spModel.core.{Angle, Coordinates}
import edu.gemini.spModel.gemini.gems.Gems
import edu.gemini.spModel.inst.ProbeArmGeometry
import edu.gemini.spModel.inst.ProbeArmGeometry.ArmAdjustment
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.rich.shared.immutable._

import scalaz._
import Scalaz._

// The geometry is dependent on the plate scale determined by the choice of Lyot wheel,
// and thus we must use a class instead of an object.
object F2OiwfsProbeArm extends ProbeArmGeometry {
  // Simplified Java access.
  val instance = this

  override protected val guideProbeInstance = Flamingos2OiwfsGuideProbe.instance

  override def unadjustedGeometry(ctx: ObsContext): Option[Shape] =
    ctx.getInstrument match {
      case f2: Flamingos2 => Some(f2Geometry(f2))
      case _              => None
    }

  private def f2Geometry(f2: Flamingos2): Shape = {
    val probeArm: Shape = {
      val plateScale = f2.getLyotWheel.getPlateScale
      val scaledLength = ProbePickoffArmLength * plateScale
      val hm = PickoffMirrorSize * plateScale / 2.0
      val htw = ProbeArmTaperedWidth / 2.0

      val (x0, y0) = (hm, -htw)
      val (x1, y1) = (x0 + ProbeArmTaperedLength, -hm)
      val (x2, y2) = (x0 + scaledLength, y1)
      val (x3, y3) = (x2, hm)
      val (x4, y4) = (x1, y3)
      val (x5, y5) = (x0, htw)

      val points = List((x0, y0), (x1, y1), (x2, y2), (x3, y3), (x4, y4), (x5, y5))
      ImPolygon(points)
    }

    val pickoffMirror: Shape = {
      val plateScale = f2.getLyotWheel.getPlateScale
      val scaledMirrorSize = PickoffMirrorSize * plateScale
      val xy = -scaledMirrorSize / 2.0
      new Rectangle2D.Double(xy, xy, scaledMirrorSize, scaledMirrorSize)
    }

    new Area(probeArm) <| (_.add(new Area(pickoffMirror)))
  }


  /*  COMMENT OUT FOR NOW, NEEDS UPDATE AND VERIFICATION
  override def armAdjustment(ctx0: ObsContext,
                             guideStarCoords: Coordinates,
                             offset0: Offset,
                             T: Point2D): Option[ArmAdjustment] = {
    import ProbeArmGeometry._

    for {
      ctx      <- Option(ctx0)
      offset   <- Option(offset0)
    } yield {
      val gemsFlag = ctx.getAOComponent.asScalaOpt.fold(false){ ao =>
        val aoNarrowType = ao.getNarrowType
        aoNarrowType.equals(Gems.SP_TYPE.narrowType)
      }

      val flamingos2  = ctx.getInstrument.asInstanceOf[Flamingos2]
      val flip        = if (flamingos2.getFlipConfig(gemsFlag)) -1 else 1
      val posAngle    = ctx.getPositionAngle.toRadians.getMagnitude
      val fovRotation = flamingos2.getRotationConfig(gemsFlag).toRadians.getMagnitude
      val guideStarPt = guideStarPoint(ctx, guideStarCoords)
      val angle       = armAngle(posAngle, fovRotation, guideStarPt, T, flip, flamingos2.getLyotWheel.getPlateScale)
      ArmAdjustment(angle, guideStarPt)
    }
  }

  private def armAngle(posAngle:    Double,
                       fovRotation: Double,
                       guideStar:   Point2D,
                       T:           Point2D,
                       flip:        Int,
                       plateScale:  Double): Angle = {
    val P = {
      val scaledPAO = ProbeArmOffset * plateScale
      val angle     = -posAngle - fovRotation
      new Point2D.Double(scaledPAO * flip * math.cos(angle), scaledPAO * flip * math.sin(angle))
    }
    val D = new Point2D.Double(guideStar.getX + T.getX - P.getX, guideStar.getY + T.getY - P.getY)

    val Q = {
      val scaledPBAL = ProbeBaseArmLength * plateScale
      val scaledPPAL = ProbePickoffArmLength * plateScale
      val distance   = math.min(math.sqrt(D.getX * D.getX + D.getY * D.getY), scaledPBAL + scaledPPAL)

      val a = (scaledPBAL * scaledPBAL - scaledPPAL * scaledPPAL + distance * distance) / (2 * distance)
      val h = flip * math.sqrt(scaledPBAL * scaledPBAL - a * a)

      new Point2D.Double(-guideStar.getX - T.getX + P.getX + (a * D.getX + h * D.getY) / distance,
                         -guideStar.getY - T.getY + P.getY + (a * D.getY - h * D.getX) / distance)
    }

    Angle.fromRadians(math.atan2(Q.getY, Q.getX))
  }
*/
  // Size of probe arm components in mm.
  val PickoffMirrorSize          = 19.8
  val ProbePickoffArmTotalLength = 203.40
  val ProbeBaseArmLength         = 109.63
  val ProbePickoffArmLength      = ProbePickoffArmTotalLength - PickoffMirrorSize/2.0
  val ProbeArmOffset             = 256.87

  // Width and length of tapered end of probe arm in arcsec.
  val ProbeArmTaperedWidth       = 15.0
  val ProbeArmTaperedLength      = 180.0

  /**
   * For a given context, guide star coordinates, and offset, calculate the arm adjustment that will be used for the
   * guide star at those coordinates.
   * @param ctx       context representing the configuration
   * @param guideStar guide star for which to calculate the adjustment
   * @param offset    offset for which to calculate the adjustment
   * @return          probe arm adjustments for this data
   */
  override def armAdjustment(ctx: ObsContext, guideStar: Coordinates, offset: core.Offset): Option[ArmAdjustment] = None
}