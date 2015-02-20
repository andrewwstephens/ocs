package edu.gemini.ags.impl

import edu.gemini.ags.api.AgsMagnitude._
import edu.gemini.ags.api.{AgsMagnitude, AgsAnalysis, AgsStrategy}
import edu.gemini.catalog.api.{RadiusLimits, QueryConstraint}
import edu.gemini.shared.skyobject.SkyObject
import edu.gemini.spModel.ags.AgsStrategyKey
import edu.gemini.spModel.guide.{ValidatableGuideProbe, GuideProbe}
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.rich.shared.immutable._

import scala.concurrent.Future


case class ScienceTargetStrategy(key: AgsStrategyKey, guideProbe: ValidatableGuideProbe) extends AgsStrategy {

  // Since the science target is the used as the guide star, success is always guaranteed.
  override def estimate(ctx: ObsContext, mt: MagnitudeTable): Future[AgsStrategy.Estimate] =
    Future.successful(AgsStrategy.Estimate.GuaranteedSuccess)

  override def analyze(ctx: ObsContext, mt: MagnitudeTable): List[AgsAnalysis] =
    AgsAnalysis.analysis(ctx, mt, guideProbe).toList

  override def candidates(ctx: ObsContext, mt: MagnitudeTable): Future[List[(GuideProbe, List[SkyObject])]] = {
    val so = skyObjectFromScienceTarget(ctx.getTargets.getBase)
    Future.successful(List((guideProbe, List(so))))
  }

  override def select(ctx: ObsContext, mt: MagnitudeTable): Future[Option[AgsStrategy.Selection]] = {
    // The science target is the guide star, but must be converted from SPTarget to SkyObject.
    val skyObject = skyObjectFromScienceTarget(ctx.getTargets.getBase)
    val posAngle  = ctx.getPositionAngle
    val assignment = AgsStrategy.Assignment(guideProbe, skyObject)
    val selection  = AgsStrategy.Selection(posAngle, List(assignment))
    Future.successful(Some(selection))
  }

  override def magnitudes(ctx: ObsContext, mt: MagnitudeTable): List[(GuideProbe, MagnitudeCalc)] =
    mt(ctx, guideProbe).toList.map((guideProbe, _))

  override def queryConstraints(ctx: ObsContext, mt: MagnitudeTable): List[QueryConstraint] =
    (for {
      mc <- magnitudeCalc(ctx, mt)
      rl <- radiusLimits(ctx)
    } yield new QueryConstraint(ctx.getBaseCoordinates, rl, AgsMagnitude.manualSearchLimits(mc))).toList

  private def radiusLimits(ctx: ObsContext): Option[RadiusLimits] =
    RadiusLimitCalc.getAgsQueryRadiusLimits(guideProbe, ctx).asScalaOpt

  private def magnitudeCalc(ctx: ObsContext, mt: MagnitudeTable): Option[MagnitudeCalc] =
    mt(ctx, guideProbe)

  override val guideProbes: List[GuideProbe] = List(guideProbe)
}
