package edu.gemini.rollover.servlet

import edu.gemini.spModel.gemini.obscomp.{SPProgram, SPSiteQuality}
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality._
import edu.gemini.spModel.obs.plannedtime.PlannedTimeCalculator
import edu.gemini.spModel.target.obsComp.TargetObsComp
import edu.gemini.pot.sp._
import edu.gemini.spModel.target.system.CoordinateParam.Units

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import edu.gemini.skycalc.Angle

object RolloverObservation {
  def toRollover(obs: ISPObservation): Option[RolloverObservation] =
    for {
      id   <- Option(obs.getObservationID)
      prog <- program(obs)
      p    <- partner(prog)
      t    <- target(obs)
      c    <- conditions(obs)
      ms    <- time(obs)
    } yield RolloverObservation(id, p, t, c, ms)

  private def findByType(obs: ISPObservation, compType: SPComponentType): Option[ISPObsComponent] =
    for {
      comps <- Option(obs.getObsComponents)
      comp  <- comps.asScala.find(_.getType == compType)
    } yield comp

  @tailrec private def program(node: ISPNode): Option[ISPProgram] =
    node.getParent match {
      case null => None
      case p: ISPProgram    => Some(p)
      case n: ISPNode => program(n)
    }

  private def partner(p: ISPProgram): Option[String] =
    for {
      dataObj <- Option(p.getDataObject.asInstanceOf[SPProgram])
      aff     <- Option(dataObj.getPIAffiliate)
    } yield aff.displayValue

  private def target(o: ISPObservation): Option[RolloverTarget] =
    for {
      targetComp <- findByType(o, TargetObsComp.SP_TYPE)
      dataObj    <- Option(targetComp.getDataObject.asInstanceOf[TargetObsComp])
      targetEnv  <- Option(dataObj.getTargetEnvironment)
      science    <- Option(targetEnv.getBase)
      name       <- Option(science.getTarget.getName)
      ra         <- Option(science.getTarget.getRa.getAs(Units.DEGREES))
      dec        <- Option(science.getTarget.getDec.getAs(Units.DEGREES))
    } yield RolloverTarget(name, new Angle(ra, Angle.Unit.DEGREES), new Angle(dec, Angle.Unit.DEGREES))

  private def conditions(o: ISPObservation): Option[RolloverConditions] =
    for {
      sqComp  <- findByType(o, SPSiteQuality.SP_TYPE)
      dataObj <- Option(sqComp.getDataObject.asInstanceOf[SPSiteQuality])
      cc      <- Option(dataObj.getCloudCover)
      iq      <- Option(dataObj.getImageQuality)
      sb      <- Option(dataObj.getSkyBackground)
      wv      <- Option(dataObj.getWaterVapor)
    } yield RolloverConditions(cc, iq, sb, wv)

  private def time(o: ISPObservation): Option[Long] =
    try {
      for {
        pt <- Option(PlannedTimeCalculator.instance.calc(o))
        ms <- Option(pt.totalTime())
      } yield ms
    } catch {
      case ex: Exception => None
    }
}

case class RolloverTarget(name: String, ra: Angle, dec: Angle)
case class RolloverConditions(cc: CloudCover, iq: ImageQuality, sb: SkyBackground, wv: WaterVapor)
case class RolloverObservation(id: SPObservationID, partner: String, target: RolloverTarget, conds: RolloverConditions, remainingTime: Long)
