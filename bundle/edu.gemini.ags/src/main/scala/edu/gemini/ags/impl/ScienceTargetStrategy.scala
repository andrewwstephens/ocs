package edu.gemini.ags.impl

import edu.gemini.ags.api.AgsMagnitude._
import edu.gemini.pot.ModelConverters._
import edu.gemini.ags.api.{AgsAnalysis, AgsStrategy}
import edu.gemini.spModel.ags.AgsStrategyKey
import edu.gemini.spModel.core.MagnitudeBand
import edu.gemini.spModel.core.Target.SiderealTarget
import edu.gemini.spModel.guide.{ValidatableGuideProbe, GuideProbe}
import edu.gemini.spModel.obs.context.ObsContext

import scala.concurrent.Future

case class ScienceTargetStrategy(key: AgsStrategyKey, guideProbe: ValidatableGuideProbe, override val probeBands: List[MagnitudeBand]) extends AgsStrategy {

  // Since the science target is the used as the guide star, success is always guaranteed.
  override def estimate(ctx: ObsContext, mt: MagnitudeTable): Future[AgsStrategy.Estimate] =
    Future.successful(AgsStrategy.Estimate.GuaranteedSuccess)

  override def analyze(ctx: ObsContext, mt: MagnitudeTable, guideProbe: ValidatableGuideProbe, guideStar: SiderealTarget): Option[AgsAnalysis] =
    AgsAnalysis.analysis(ctx, mt, guideProbe, guideStar, probeBands)

  override def analyze(ctx: ObsContext, mt: MagnitudeTable): List[AgsAnalysis] =
    AgsAnalysis.analysis(ctx, mt, guideProbe, probeBands).toList

  override def candidates(ctx: ObsContext, mt: MagnitudeTable): Future[List[(GuideProbe, List[SiderealTarget])]] = {
    val so = ctx.getTargets.getBase.toNewModel
    Future.successful(List((guideProbe, List(so))))
  }

  override def select(ctx: ObsContext, mt: MagnitudeTable): Future[Option[AgsStrategy.Selection]] = {
    // The science target is the guide star, but must be converted from SPTarget to SkyObject.
    val siderealTarget = ctx.getTargets.getBase.toNewModel
    val posAngle       = ctx.getPositionAngle.toNewModel
    val assignment     = AgsStrategy.Assignment(guideProbe, siderealTarget)
    val selection      = AgsStrategy.Selection(posAngle, List(assignment))
    Future.successful(Some(selection))
  }

  override def magnitudes(ctx: ObsContext, mt: MagnitudeTable): List[(GuideProbe, MagnitudeCalc)] =
    mt(ctx, guideProbe).toList.map((guideProbe, _))

  override val guideProbes: List[GuideProbe] = List(guideProbe)

}
