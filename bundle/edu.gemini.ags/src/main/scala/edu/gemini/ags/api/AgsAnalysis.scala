package edu.gemini.ags.api

import edu.gemini.ags.api.AgsGuideQuality.Unusable
import edu.gemini.ags.api.AgsMagnitude._
import edu.gemini.ags.impl._
import edu.gemini.spModel.core.Target.SiderealTarget
import edu.gemini.spModel.core.{Magnitude, MagnitudeBand}
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality.ImageQuality
import edu.gemini.spModel.guide.{ValidatableGuideProbe, GuideProbe, GuideProbeGroup, GuideSpeed}
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.rich.shared.immutable._
import edu.gemini.spModel.target.SPTarget

sealed trait AgsGuideQuality {
  def message: String
}

object AgsGuideQuality {
  case object DeliversRequestedIq extends AgsGuideQuality {
    override val message = "Delivers requested IQ."
  }
  case object PossibleIqDegradation extends AgsGuideQuality {
    override val message = "Slower guiding required; may not deliver requested IQ."
  }
  case object IqDegradation extends AgsGuideQuality {
    override val message = "Slower guiding required; will not deliver requested IQ."
  }
  case object PossiblyUnusable extends AgsGuideQuality {
    override val message = "May not be able to guide."
  }
  case object Unusable extends AgsGuideQuality {
    override val message = "Unable to guide."
  }

  val All: List[AgsGuideQuality] =
    List(DeliversRequestedIq, PossibleIqDegradation, IqDegradation, PossiblyUnusable, Unusable)
}

sealed trait AgsAnalysis {
  def quality: AgsGuideQuality = Unusable
  def message(withProbe: Boolean): String
}

object AgsAnalysis {
  case class NoGuideStarForProbe(guideProbe: GuideProbe) extends AgsAnalysis {
    override def message(withProbe: Boolean): String = {
      val p = if (withProbe) s"${guideProbe.getKey} " else ""
      s"No ${p}guide star selected."
    }
  }

  case class NoGuideStarForGroup(guideGroup: GuideProbeGroup) extends AgsAnalysis {
    override def message(withProbe: Boolean): String =
      s"No ${guideGroup.getKey} guide star selected."
  }

  case class MagnitudeTooFaint(guideProbe: GuideProbe, target: SiderealTarget) extends AgsAnalysis {
    override def message(withProbe: Boolean): String = {
      val p = if (withProbe) s"use ${guideProbe.getKey}" else "guide"
      s"Cannot $p with the star in these conditions, even using the slowest guide speed."
    }
  }

  case class MagnitudeTooBright(guideProbe: GuideProbe, target: SiderealTarget) extends AgsAnalysis {
    override def message(withProbe: Boolean): String = {
      val p = if (withProbe) s"${guideProbe.getKey} g" else "G"
      s"${p}uide star is too bright to guide."
    }
  }

  case class NotReachable(guideProbe: GuideProbe, target: SiderealTarget) extends AgsAnalysis {
    override def message(withProbe: Boolean): String = {
      val p = if (withProbe) s"with ${guideProbe.getKey} " else ""
      s"The star is not reachable ${p}at all positions."
    }
  }

  case class NoMagnitudeForBand(guideProbe: GuideProbe, target: SiderealTarget, band: MagnitudeBand) extends AgsAnalysis {
    override def message(withProbe: Boolean): String = {
      val p = if (withProbe) s"${guideProbe.getKey} g" else "G"
      s"${p}uide star ${band.name}-band magnitude is missing. Cannot determine guiding performance."
    }
    override val quality = AgsGuideQuality.PossiblyUnusable
  }

  case class Usable(guideProbe: GuideProbe, target: SiderealTarget, guideSpeed: GuideSpeed, override val quality: AgsGuideQuality) extends AgsAnalysis {
    override def message(withProbe: Boolean): String = {
      val qualityMessage = quality match {
        case AgsGuideQuality.DeliversRequestedIq => ""
        case _                                   => s"${quality.message} "
      }
      val p = if (withProbe) s"${guideProbe.getKey} " else ""

      s"$qualityMessage${p}Guide Speed: ${guideSpeed.name}."
    }
  }

  def guideProbe(a: AgsAnalysis): Option[GuideProbe] = a match {
    case NoGuideStarForProbe(p)      => Some(p)
    case NoGuideStarForGroup(_)      => None
    case MagnitudeTooFaint(p, _)     => Some(p)
    case MagnitudeTooBright(p, _)    => Some(p)
    case NotReachable(p, _)          => Some(p)
    case NoMagnitudeForBand(p, _, _) => Some(p)
    case Usable(p, _, _, _)          => Some(p)
  }


  /**
   * Analysis of the selected guide star (if any) in the given context.
   */
  def analysis(ctx: ObsContext, mt: MagnitudeTable, guideProbe: ValidatableGuideProbe): Option[AgsAnalysis] = {
    def selection(ctx: ObsContext, guideProbe: GuideProbe): Option[SPTarget] =
      for {
        gpt   <- ctx.getTargets.getPrimaryGuideProbeTargets(guideProbe).asScalaOpt
        gStar <- gpt.getPrimary.asScalaOpt
      } yield gStar

    selection(ctx, guideProbe).fold(Some(NoGuideStarForProbe(guideProbe)): Option[AgsAnalysis]) { guideStar =>
      AgsAnalysis.analysis(ctx, mt, guideProbe, guideStar)
    }
  }

  /**
   * Analysis for Java.
   */
  def analysisForJava(ctx: ObsContext, mt: MagnitudeTable, guideProbe: ValidatableGuideProbe, guideStar: SPTarget) =
    analysis(ctx, mt, guideProbe, guideStar).asGeminiOpt

  /**
   * Analysis of the given guide star in the given context, regardless of which
   * guide star is actually selected in the target environment.
   */
  def analysis(ctx: ObsContext, mt: MagnitudeTable, guideProbe: ValidatableGuideProbe, guideStar: SPTarget): Option[AgsAnalysis] =
    if (!guideProbe.validate(guideStar, ctx)) Some(NotReachable(guideProbe, guideStar.toNewModel))
    else magnitudeAnalysis(ctx, mt, guideProbe, guideStar.toNewModel)

  private def magnitudeAnalysis(ctx: ObsContext, mt: MagnitudeTable, guideProbe: ValidatableGuideProbe, guideStar: SiderealTarget): Option[AgsAnalysis] = {
    import GuideSpeed._
    import AgsGuideQuality._

    val conds = ctx.getConditions

    // Handles the case where the magnitude falls outside of the acceptable ranges for any guide speed.
    // This handles Andy's 0.5 rule where we might possibly be able to guide if the star is only 0.5 too dim, and
    // otherwise returns the appropriate analysis indicating too dim or too bright.
    def outsideLimits(magCalc: MagnitudeCalc, mag: Magnitude): AgsAnalysis = {
      val adj             = 0.5
      val saturationLimit = magCalc(conds, FAST).saturationConstraint
      val faintnessLimit  = magCalc(conds, SLOW).faintnessConstraint.brightness
      val saturated       = saturationLimit.exists(_.brightness > mag.value)

      def almostTooFaint: Boolean = !saturated && mag.value <= faintnessLimit + adj
      def tooFaint:       Boolean = mag.value > faintnessLimit + adj

      if (almostTooFaint) Usable(guideProbe, guideStar, SLOW, PossiblyUnusable)
      else if (tooFaint)  MagnitudeTooFaint(guideProbe, guideStar)
      else                MagnitudeTooBright(guideProbe, guideStar)
    }

    // Called when we know that a valid guide speed can be chosen for the given guide star.
    // Determine the quality and return an analysis indicating that the star is usable.
    def usable(guideSpeed: GuideSpeed): AgsAnalysis = {
      def worseOrEqual(iq: ImageQuality) = conds.iq.compareTo(iq) >= 0

      val quality = guideSpeed match {
        case FAST =>
          DeliversRequestedIq
        case MEDIUM =>
          if (worseOrEqual(ImageQuality.PERCENT_70)) DeliversRequestedIq
          else PossibleIqDegradation
        case SLOW =>
          if (worseOrEqual(ImageQuality.PERCENT_85)) DeliversRequestedIq
          else if (worseOrEqual(ImageQuality.PERCENT_70)) PossibleIqDegradation
          else IqDegradation
      }

      Usable(guideProbe, guideStar, guideSpeed, quality)
    }

    for {
      mc <- mt(ctx, guideProbe)
      probeBand = band(mc)
    } yield {
      val magOpt      = guideStar.magnitudeIn(probeBand)
      val analysisOpt = magOpt.map(mag => fastestGuideSpeed(mc, mag, conds).fold(outsideLimits(mc, mag))(usable))
      analysisOpt.getOrElse(NoMagnitudeForBand(guideProbe, guideStar, probeBand))
    }
  }
}