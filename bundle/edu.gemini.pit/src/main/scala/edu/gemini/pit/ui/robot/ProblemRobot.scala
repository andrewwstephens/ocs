package edu.gemini.pit.ui.robot

import edu.gemini.pit.ui._
import action.AppPreferencesAction
import edu.gemini.model.p1.immutable._
import edu.gemini.pit.ui.editor.Institutions
import edu.gemini.pit.util.PDF
import edu.gemini.pit.catalog._
import java.util.Date
import view.obs.ObsListGrouping
import edu.gemini.model.p1.visibility.TargetVisibilityCalc
import edu.gemini.pit.ui.view.tac.TacView
import scalaz._
import Scalaz._
import java.text.SimpleDateFormat
import java.io.File
import edu.gemini.pit.model.{AppPreferences, Model}
import edu.gemini.pit.catalog.NotFound
import edu.gemini.pit.catalog.Error

object ProblemRobot {

  class Problem(val severity: Severity,
                val description: String,
                val section: String,
                fix: => Unit) extends Ordered[Problem] {

    def compare(other: Problem) = Some(severity.compare(other.severity)).filter(_ != 0).getOrElse(description.compareTo(other.description))

    def apply() {
      fix
    }

  }

  object Severity extends Enumeration {
    // Order is important; severity increases
    val Error, Todo, Warning, Info = Value
  }

  type Severity = Severity.Value

  private implicit class pimpLong(val n: Long) extends AnyVal {
    def ms = n
    def secs = ms * 1000
    def mins = secs * 60
    def hours = mins * 60
    def days = hours * 24
  }

}

class ProblemRobot(s: ShellAdvisor) extends Robot {

  // Bring our related types into scope

  import ProblemRobot._

  val MaxAttachmentSize = 30 // in megabytes
  val MaxAttachmentSizeBytes = MaxAttachmentSize*1000*1000 // kbytes as used on the phase1 backends

  // Our state
  type State = List[Problem]
  protected[this] val initialState = Nil

  // Register with the catalog handler
  require(s.catalogHandler != null)
  s.catalogHandler.addListener {
    a: CatalogRobot#State => refresh(model)
  }

  override protected def refresh(m: Option[Model]) {
    state = m.map(m => new Checker(m.proposal, s.shell.file).all).getOrElse(Nil)
  }

  // TODO: factor this out better; it's a leftover from the original implementation
  private class Checker(p: Proposal, xml: Option[File]) {

    lazy val all = {
      val ps =
        List(noObs, titleCheck, band3option, abstractCheck, tacCategoryCheck, keywordCheck, attachmentCheck, attachmentValidityCheck, attachmentSizeCheck, missingObsDetailsCheck, duplicateInvestigatorCheck, ftReviewerOrMentor, ftAffiliationMissmatch).flatten ++
          TimeProblems(p, s).all ++
          TimeProblems.partnerZeroTimeRequest(p, s) ++
          TacProblems(p, s).all ++
          List(incompleteInvestigator, missingObsElementCheck, cfCheck, emptyTargetCheck, emptyEphemerisCheck, initialEphemerisCheck, finalEphemerisCheck,
            badGuiding, badVisibility, iffyVisibility, singlePointEphemerisCheck, minTimeCheck, wrongSite, band3Orphan2, gpiCheck).flatten
      ps.sorted
    }

    private def when[A](b: Boolean)(a: => A) = if (b) Some(a) else None

    private lazy val titleCheck = when(p.title.isEmpty) {
      new Problem(Severity.Todo, "Please provide a title.", "Overview", s.inOverview(_.title.requestFocus()))
    }

    private lazy val abstractCheck = when(p.abstrakt.isEmpty) {
      new Problem(Severity.Todo, "Please provide an abstract.", "Overview", s.inOverview(_.abstrakt.requestFocus()))
    }

    private lazy val tacCategoryCheck = when(p.tacCategory.isEmpty) {
      new Problem(Severity.Todo, "Please select a TAC category.", "Overview", s.inOverview(_.tacCategory.peer.setPopupVisible(true)))
    }

    private lazy val keywordCheck = when(p.keywords.isEmpty) {
      new Problem(Severity.Todo, "Please provide keywords.", "Overview", s.inOverview(_.keywords.select.doClick()))
    }

    private lazy val attachmentCheck = when(p.meta.attachment.isEmpty) {
      new Problem(Severity.Todo, "Please provide a PDF attachment.", "Overview", s.inOverview(_.attachment.select.doClick()))
    }

    def extractInvestigator(i:PrincipalInvestigator) = (i.firstName, i.lastName, i.email, i.phone, i.status, i.address.institution)

    def duplicateInvestigators(investigators:List[Investigator]) = {
      val uniqueInvestigators = for {
        i <- investigators
        if i.isComplete
      } yield extractInvestigator(i.toPi)
      uniqueInvestigators.distinct.size != uniqueInvestigators.size
    }

    def similarInvestigators(investigators:List[Investigator]) = {
      val uniqueInvestigators = for {
        i <- investigators
        if i.isComplete
      } yield i.fullName.toLowerCase
      uniqueInvestigators.distinct.size != uniqueInvestigators.size
    }

    private val duplicateInvestigatorCheck = if (duplicateInvestigators(p.investigators.all)) {
      Some(new Problem(Severity.Error, "Please remove duplicates from the investigator list.", "Overview", s.inOverview(_.investigators.editPi())))
    } else if (similarInvestigators(p.investigators.all)) {
      Some(new Problem(Severity.Warning, "Please check for duplications in the investigator list.", "Overview", s.inOverview(_.investigators.editPi())))
    } else {
      None
    }

    private lazy val attachmentValidityCheck = for {
      a <- p.meta.attachment
      if !PDF.isPDF(xml, a)
    } yield new Problem(Severity.Error, s"File ${a.getName} does not exist or is not a PDF file.", "Overview", s.inOverview(_.attachment.select.doClick()))

    private lazy val attachmentSizeCheck = for {
      a <- p.meta.attachment
      if a.length() > MaxAttachmentSizeBytes
    } yield new Problem(Severity.Error, s"Attachment '${a.getName}' is larger than ${MaxAttachmentSize}MB.", "Overview", s.inOverview(_.attachment.select.doClick()))

    private lazy val cfCheck = for {
      (t, Some(f)) <- s.catalogHandler.state
      (sev, msg) = f match {
        case Offline      => (Severity.Error, s"Catalog lookup failed for ${t.name} due to network connectivity problems.")
        case NotFound(t)  => (Severity.Error, s"""Catalog lookup returned no results for target "$t" """)
        case Error(e)     => (Severity.Error, s"Catalog lookup failed for ${t.name} due to an unexpected error: ")
      }
    } yield new Problem(sev, msg, "Targets", s.inTargetsView(_.edit(t)))

    private lazy val emptyTargetCheck = for {
      t <- p.targets
      if t.isEmpty
      if !s.catalogHandler.state.contains(t)
      msg = s"""Target "${t.name}" appears to be empty."""
    } yield new Problem(Severity.Error, msg, "Targets", s.inTargetsView(_.edit(t)))

    lazy val utc = new SimpleDateFormat("dd-MMM-yyyy")

    private lazy val initialEphemerisCheck = for {
      t @ NonSiderealTarget(_, n, e, _) <- p.targets
      if !t.isEmpty
      if !s.catalogHandler.state.contains(t)
      ds = e.map(_.validAt) if ds.size > 1 // only for an ephemeris with defined points
      diff = ds.min - p.semester.firstDay
      if diff > 1.days
      date = new Date(ds.min)
      msg = if (ds.min >= p.semester.lastDay)
        s"""Ephemeris for target "${t.name}" is undefined between ${utc.format(p.semester.firstDay)} and ${utc.format(p.semester.lastDay)} UTC."""
      else
        s"""Ephemeris for target "${t.name}" is undefined before ${utc.format(p.semester.firstDay)} and ${utc.format(date)} UTC."""
    } yield new Problem(Severity.Warning, msg, "Targets", s.inTargetsView(_.edit(t)))

    private lazy val finalEphemerisCheck = for {
      t @ NonSiderealTarget(_, n, e, _) <- p.targets
      if !t.isEmpty
      if !s.catalogHandler.state.contains(t)
      ds = e.map(_.validAt) if ds.size > 1 // only for an ephemeris with defined points
      diff = p.semester.lastDay - ds.max
      if diff > 1.days
      date = new Date(ds.max)
      msg = if (ds.max <= p.semester.firstDay)
        s"""Ephemeris for target "${t.name}" is undefined between ${utc.format(p.semester.firstDay)}} and ${utc.format(p.semester.lastDay)} UTC."""
      else
        s"""Ephemeris for target "${t.name}" is undefined between ${utc.format(date)} and ${utc.format(p.semester.lastDay)} UTC."""
    } yield new Problem(Severity.Warning, msg, "Targets", s.inTargetsView(_.edit(t)))

    private lazy val emptyEphemerisCheck = for {
      t@NonSiderealTarget(_, n, e, _) <- p.targets
      if !t.isEmpty
      if !s.catalogHandler.state.contains(t)
      if e.isEmpty
      msg = "Ephemeris for target \"%s\" is undefined.".format(t.name)
    } yield new Problem(Severity.Warning, msg, "Targets", s.inTargetsView(_.edit(t)))

    private val gpiCheck = {
      def gpiMagnitudesPresent(target: SiderealTarget):List[(Severity, String)] = {
        val requiredMagnitudes = "I" :: "Y" :: "J" :: "H" :: "K" :: Nil
        val obsMagnitudes = target.magnitudes.map(_.band.value)
        if (!requiredMagnitudes.forall(obsMagnitudes.contains)) {
          List((Severity.Error, "The magnitude information in the GPI target component should include the bandpasses I, Y, J, H, and K"))
        } else {
          Nil
        }
      }
      def gpiIChecks(target: SiderealTarget):List[(Severity.Value, String)] = for {
          m <- target.magnitudes
          if m.band.value == "I"
          iMag = m.value
          if iMag < 3.0 || iMag > 8.0
          severity = if (iMag <= 1.0 || iMag > 10.0) Severity.Error else Severity.Warning
          message = if (iMag < 3.0 && iMag > 1.0) {
              s"""GPI Target "${target.name}" may be too bright for the OIWFS"""
            } else if (iMag <= 1.0) {
              s"""GPI Target "${target.name}" too bright to work with the OIWFS"""
            } else {
              s"""GPI Target "${target.name}" is too faint for proper AO (OIWFS) operation, the AO performance will be poor"""
            }
        } yield (severity, message)
      def gpiIfsChecks(obsMode: GpiObservingMode, disperser: GpiDisperser, target: SiderealTarget):List[(Severity.Value, String)] = for {
          m <- target.magnitudes
          scienceBand <- GpiObservingMode.scienceBand(obsMode)
          if scienceBand.startsWith(m.band.value)
          scienceMag = m.value
          disperserLimit = if (disperser.value.equalsIgnoreCase("Prism")) 0.0 else 2.0
          coronographLimit = 0.0 + disperserLimit
          directLimit = 8.5 + disperserLimit
          if (scienceMag < coronographLimit && GpiObservingMode.isCoronographMode(obsMode)) || (scienceMag < directLimit && GpiObservingMode.isDirectMode(obsMode))
        } yield (Severity.Warning, s"""GPI Target "${target.name}" risks saturating the science detector even for short exposure times""")

      def gpiLowfsChecks(obsMode: GpiObservingMode, target: SiderealTarget):List[(Severity.Value, String)] = for {
        m <- target.magnitudes
        if m.band.value == "H" && GpiObservingMode.isCoronographMode(obsMode)
        hMag = m.value
        if hMag < 2.0 || hMag > 10.0
        message = if (hMag < 2.0) {
            s"""GPI Target "${target.name}" is too bright, it will saturate the LOWFS"""
          } else  {
            s"""GPI Target "${target.name}" is too faint for proper CAL (LOWFS) operation and thus mask centering on the coronograph will be severely affected"""
          }
      } yield (Severity.Warning, message)

      val gpiTargetsWithProblems: List[(SiderealTarget, List[(Severity, String)])] = for {
          o <- p.observations
          b <- o.blueprint
          if b.isInstanceOf[GpiBlueprint]
          obsMode = b.asInstanceOf[GpiBlueprint].observingMode
          disperser = b.asInstanceOf[GpiBlueprint].disperser
          t @ SiderealTarget(_, _, _, _, _, mag) <- o.target
        } yield (t, (gpiMagnitudesPresent(t) :: gpiIChecks(t) :: gpiLowfsChecks(obsMode, t) :: gpiIfsChecks(obsMode, disperser, t) :: Nil).flatten)
      for {
        gpiProblems <- gpiTargetsWithProblems
        target      =  gpiProblems._1
        problem     <- gpiProblems._2
        severity    =  problem._1
        message     =  problem._2
      } yield new Problem(severity, message, "Targets", s.inTargetsView(_.edit(target)))
    }

    private lazy val singlePointEphemerisCheck = for {
      t @ NonSiderealTarget(_, n, e, _) <- p.targets
      if !t.isEmpty
      if !s.catalogHandler.state.contains(t)
      if e.size == 1
      msg = s"""Ephemeris for target "${t.name}" contains only one point; please specify at least two."""
    } yield new Problem(Severity.Warning, msg, "Targets", s.inTargetsView(_.edit(t)))

    private lazy val missingObsElementCheck = {
      def fix[A](b: Band, g: ObsListGrouping[A]) {
        s.inObsListView(b, _.Fixes.fixEmpty(g))
      }

      def check[A](s: String, g: ObsListGrouping[A]) =
        p.observations.filter(g.lens.get(_).isEmpty) match {
          case Nil => None
          case h :: Nil => Some(new Problem(Severity.Error, s"One observation has no $s.", "Observations", fix(h.band, g)))
          case h :: tail => Some(new Problem(Severity.Error, "%d observations have no %s.".format(1 + tail.length, s), "Observations", fix(h.band, g)))
        }

      List(
        check("instrument configuration", ObsListGrouping.Blueprint),
        check("target", ObsListGrouping.Target),
        check("observing conditions", ObsListGrouping.Condition)).flatten
    }

    private def indicateObservation(o: Observation) {
      s.inObsListView(o.band, _.Fixes.indicateObservation(o))
    }

    private lazy val missingObsDetailsCheck =
      p.observations.filter(_.time.isEmpty) match {
        case Nil => None
        case h :: Nil => Some(new Problem(Severity.Error, "One observation has no observation time.", "Observations", indicateObservation(h)))
        case h :: tail => Some(new Problem(Severity.Error, "%d observations have no observation times.".format(1 + tail.length), "Observations", indicateObservation(h)))
      }

    private def hasBestGuidingConditions(o: Observation): Boolean =
      o.condition exists {
        c =>
          c.cc == CloudCover.BEST && c.iq == ImageQuality.BEST && c.sb == SkyBackground.BEST
      }

    private def guidingMessage(o: Observation): String = {
      val base = "Observation unlikely to have usable guide stars."
      if (hasBestGuidingConditions(o)) base else s"$base Try better conditions?"
    }

    private lazy val badGuiding = for {
      o <- p.observations
      m <- o.meta
      g <- m.guiding if g.evaluation == GuidingEvaluation.FAILURE
    } yield new Problem(Severity.Warning, guidingMessage(o), "Observations", indicateObservation(o))

    private def visibilityMessage(tmpl: String, sem: Semester, o: Observation): String =
      tmpl.format(
        o.blueprint.map(_.site.name).getOrElse("this telescope"),
        sem.display
      )

    private lazy val badVisibility = for {
      o @ Observation(Some(_), Some(_), Some(_), Some(_), _) <- p.observations
      v <- if (p.proposalClass.isSpecial) TargetVisibilityCalc.getOnDec(p.semester, o) else TargetVisibilityCalc.get(p.semester, o)
      if v == TargetVisibility.Bad
    } yield new Problem(Severity.Error,
        visibilityMessage("Target is inaccessible at %s during %s.  Consider an alternative.", p.semester, o),
        "Observations",
        indicateObservation(o))


    private lazy val iffyVisibility = for {
      o @ Observation(Some(_), Some(_), Some(_), Some(_), _) <- p.observations
      v <- if (p.proposalClass.isSpecial) TargetVisibilityCalc.getOnDec(p.semester, o) else TargetVisibilityCalc.get(p.semester, o)
      if v == TargetVisibility.Limited
    } yield new Problem(Severity.Warning,
        visibilityMessage("Target has limited visibility at %s during %s.", p.semester, o),
        "Observations",
        indicateObservation(o))

    private lazy val minTimeCheck = {

      val subs:List[Submission] = p.proposalClass match {
        case n: GeminiNormalProposalClass => n.subs match {
          case Left(ss)  => ss
          case Right(ss) => List(ss)
        }
        case e: ExchangeProposalClass      => e.subs
        case s: SpecialProposalClass       => List(s.sub)
        case l: LargeProgramClass          => List(l.sub)
        case f: FastTurnaroundProgramClass => List(f.sub)
      }

      subs.filter(sub => sub.request.time.hours < sub.request.minTime.hours).map {
        sub =>
          val kind = sub match {
            case n: NgoSubmission            => Partners.name(n.partner)
            case e: ExchangeSubmission       => Partners.name(e.partner)
            case s: SpecialSubmission        => s.specialType.value
            case l: LargeProgramSubmission   => "large program"
            case f: FastTurnaroundSubmission => "fast-turnaround"
          }
          new Problem(Severity.Error, s"Requested time for $kind is less than minimum requested time.", TimeProblems.SCHEDULING_SECTION,
            s.inPartnersView(_.editSubmissionTime(sub)))
      }

    }

    private lazy val band3option = (p.meta.band3OptionChosen, p.proposalClass) match {
      case (false, q: QueueProposalClass) => Some(new Problem(Severity.Todo, "Please select a Band 3 option.", TimeProblems.SCHEDULING_SECTION, s.showPartnersView))
      case _                              => None
    }

    private lazy val band3Orphan2 = for {
      o <- p.observations
      if o.band == Band.BAND_3 && (p.proposalClass match {
        case q: QueueProposalClass         if q.band3request.isDefined => false
        //case f: FastTurnaroundProgramClass if f.band3request.isDefined => false
        case _ => true
      })
    } yield {
      new Problem(Severity.Error,
      "Allow consideration for Band 3 or delete the Band 3 observation.", "Observations", {
        s.showPartnersView()
        s.inObsListView(o.band, v => v.Fixes.indicateObservation(o))
      })
    }

    private lazy val ftReviewerOrMentor = for {
        f @ FastTurnaroundProgramClass(_, _, _, _, _, _, r, m, _, _) <- Some(p.proposalClass)
        if r.isEmpty || (~r.map(_.status != InvestigatorStatus.PH_D) && m.isEmpty)
      } yield new Problem(Severity.Error,
            "A Fast Turnaround program must select a reviewer or a mentor with PhD degree", TimeProblems.SCHEDULING_SECTION, {
              s.showPartnersView()
            })

    private lazy val ftAffiliationMissmatch = for {
        pi                                                                      <- Option(p.investigators.pi)
        piNgo                                                                   <- Option(Institutions.institution2Ngo(pi.address.institution, pi.address.country))
        f @ FastTurnaroundProgramClass(_, _, _, _, _, _, _, _, affiliateNgo, _) <- Option(p.proposalClass)
        same <- (affiliateNgo |@| piNgo){_ == _}
        if ~(affiliateNgo |@| piNgo){_ != _}
      } yield new Problem(Severity.Info,
            s"The Fast Turnaround affiliation country: '${~Partners.name.get(affiliateNgo.get)}' is different from the PI's country: '${~Partners.name.get(piNgo.get)}'.", TimeProblems.SCHEDULING_SECTION, {
              s.showPartnersView()
            })

    private lazy val wrongSite = for {
      o <- p.observations
      b <- o.blueprint if (p.proposalClass match {
      case e: ExchangeProposalClass if e.partner == ExchangePartner.KECK => b.site != Site.Keck
      case e: ExchangeProposalClass if e.partner == ExchangePartner.SUBARU => b.site != Site.Subaru
      case _ => b.site != Site.GN && b.site != Site.GS
    })
    } yield {
      val host = p.proposalClass match {
        case e: ExchangeProposalClass if e.partner == ExchangePartner.KECK => Site.Keck.name
        case e: ExchangeProposalClass if e.partner == ExchangePartner.SUBARU => Site.Subaru.name
        case _ => "Gemini"
      }
      new Problem(Severity.Error, s"Scheduling request is for $host but resource resides at ${b.site.name}", "Observations", {
        s.showPartnersView()
        s.inObsListView(o.band, v => v.Fixes.indicateObservation(o))
      })
    }

    private lazy val noObs = if (p.observations.isEmpty) {
      Some(new Problem(Severity.Todo, "Please create observations with conditions, targets, and resources.", "Observations", ()))
    } else None

    private lazy val incompleteInvestigator = for {
      i <- p.investigators.all if !i.isComplete
    } yield new Problem(Severity.Todo, s"Please provide full contact information for ${i.fullName}.", "Overview", s.inOverview {
        v =>
          v.edit(i)
      })

  }

}

import ProblemRobot._
import TimeProblems._

object TimeProblems {
  // Are two time amounts close enough to be considered the same?
  def sameTime(t1: TimeAmount, t2: TimeAmount): Boolean =
    (t1.hours - t2.hours).abs < 0.001

  // The goal here is to not show too much precision and yet not say two
  // times are different and print out two amounts that look the same.
  def formatDifferingTimes(t1: TimeAmount, t2: TimeAmount, prec: Int): Option[(String, String)] =
    if (t1.units != t2.units)
      formatDifferingTimes(t1.toHours, t2.toHours, prec)
    else
      prec match {
        case n if n < 0 => formatDifferingTimes(t1, t2, 0)
        case n if n < 4 =>
          val s1 = t1.format(n)
          val s2 = t2.format(n)
          if (s1.equals(s2)) formatDifferingTimes(t1, t2, n + 1) else Some((s1, s2))
        case _ => None
      }

  val SCHEDULING_SECTION = "Time Requests"

  // REL-2032 Check that none of the requested times per partner are zero
  def partnerZeroTimeRequest(p: Proposal, s:ShellAdvisor): List[ProblemRobot.Problem] = p.proposalClass match {
    case g:GeminiNormalProposalClass =>
      val probs = for {
          sub <- g.subs.swap.right
        } yield for {
            ps <- sub
            if ps.request.time.value <= 0.0
          } yield new Problem(Severity.Error, s"Please specify a time request for ${Partners.name.getOrElse(ps.partner, "")} or remove partner", SCHEDULING_SECTION, s.inPartnersView(_.editSubmissionTime(ps)))
      probs.right.getOrElse(Nil)
    case _                            => Nil
  }
}

case class TimeProblems(p: Proposal, s: ShellAdvisor) {
  lazy val requested = p.proposalClass.requestedTime
  def obsTimeSum(b: Band) = TimeAmount.sum(for {
    o <- p.observations if o.band == b
    t <- o.time
  } yield t)
  lazy val obs = obsTimeSum(Band.BAND_1_2)
  lazy val obsB3 = obsTimeSum(Band.BAND_3)

  lazy val b3Req = p.proposalClass match {
    case q: QueueProposalClass => q.band3request
    case _ => None
  }
  lazy val b3ReqOrZero = b3Req.map(_.time).getOrElse(TimeAmount.empty)
  lazy val jointNotAllowed = {
    def checkForNotAllowedJointProposals(subs: Option[List[NgoSubmission]]):List[Problem] = {
      val r = for {
          subs <- subs
          if subs.size > 1
        } yield for {
            p <- subs.filter(s => Partners.jointProposalNotAllowed.contains(s.partner))
          } yield new Problem(Severity.Error, s"${~Partners.name.get(p.partner)} cannot be part of a joint proposal, please update the time request.", SCHEDULING_SECTION,
              s.showPartnersView())
      r.sequence.flatten
    }

    p.proposalClass match {
      case p: GeminiNormalProposalClass => checkForNotAllowedJointProposals(p.subs.left.toOption)
      case e: ExchangeProposalClass     => checkForNotAllowedJointProposals(e.subs.some)
      case x                            => Nil
    }
  }

  private def when[A](b: Boolean)(a: => A) = if (b) Some(a) else None

  private def requestedTimeCheck(r: TimeAmount, o: TimeAmount, b: Band) =
    when(r.hours > 0 && !sameTime(r, o)) {
      val b3 = if (b == Band.BAND_3) "Band 3 " else ""
      val msg = (formatDifferingTimes(r, o, 2) map {
        case (s1, s2) => s"Requested ${b3}time, $s1, differs from the sum of times for all ${b3}observations, $s2."
      }).getOrElse(s"Requested ${b3}time differs from the sum of times for all ${b3}observations.")
      new Problem(Severity.Warning, msg, SCHEDULING_SECTION, {
        s.showPartnersView()
        s.showObsListView(b)
      })
    }

  def requestedTimeDiffers = requestedTimeCheck(requested, obs, Band.BAND_1_2)
  def requestedB3TimeDiffers = requestedTimeCheck(b3ReqOrZero, obsB3, Band.BAND_3)

  def noTimeRequest = when(requested.hours <= 0.0) {
    new Problem(Severity.Todo, "Please specify a time request.", SCHEDULING_SECTION, s.showPartnersView())
  }

  private def b3Problem(f: SubmissionRequest => Boolean, sev: Severity, msg: String) = when(b3Req.exists(r => f(r))) {
    new Problem(sev, msg, SCHEDULING_SECTION, s.inPartnersView(_.editBand3Time()))
  }

  def noBand3Time = b3Problem(_.time.hours <= 0.0, Severity.Todo, "Please enter the total requested time for a Band 3 allocation.")
  def noMinBand3Time = b3Problem(r => r.time.hours > 0.0 && r.minTime.hours <= 0.0, Severity.Todo, "Please enter the minimum required time for a usable Band 3 allocation.")
  def band3MinTime = b3Problem(r => r.time.hours < r.minTime.hours, Severity.Error, "The minimum Band 3 required time must not be longer than the total Band 3 requested time.")

  def all = List(requestedTimeDiffers, requestedB3TimeDiffers, noTimeRequest, noBand3Time, noMinBand3Time, band3MinTime).flatten ++ jointNotAllowed
}


object TacProblems {

  val TAC_SECTION = "TAC"
  type Partner = Any
  // ugh
  def tac = AppPreferences.current.mode == AppPreferences.PITMode.TAC
  def name(p: Partner) = Partners.name.getOrElse(p, "<unknown>")

}

case class TacProblems(p: Proposal, s: ShellAdvisor) {

  import TacProblems._

  lazy val responses = TacView.responses(p.proposalClass)
  lazy val accepts = responses.map {
    case (p, r) =>
      r.decision flatMap {
        case SubmissionDecision(Right(a)) => Some((p, a))
        case _ => None
      }
  }.flatten.toMap

  def tacProblem(f: => Boolean, sev: Severity, msg: String, partner: Partner): Option[Problem] =
    Option(f).filter(_ == true).map(_ => new Problem(sev, msg, TAC_SECTION, s.showTacView(partner)))

  def noResponses = Option(responses).filter(_.isEmpty).map(_ => new Problem(
    Severity.Error,
    "This proposal has no responses; TAC mode is not applicable.",
    TAC_SECTION,
    new AppPreferencesAction(s.shell)()))

  def noDecision = responses.map {
    case (p, r) =>
      tacProblem(
        r.decision.isEmpty,
        Severity.Todo,
        s"Please provide a TAC decision for ${name(p)}.",
        p)
  }

  def noEmail = accepts.map {
    case (p, a) =>
      tacProblem(a.email.trim.isEmpty, Severity.Todo, s"Please provide a contact email address for ${name(p)}.", p)
  }

  def noRanking = accepts.map {
    case (p, a) =>
      tacProblem(a.ranking == 0, Severity.Todo, s"Please provide a non-zero ranking for ${name(p)}.", p)
  }

  def noTimes = accepts.map {
    case (p, a) =>
      tacProblem(a.recommended.isEmpty, Severity.Todo, s"Please provide a recommended time for ${name(p)}.", p)
  }

  def badTimes = accepts.map {
    case (p, a) =>
      tacProblem(a.recommended.hours < a.minRecommended.hours, Severity.Error, s"Minimum time is greater than recommended time for ${name(p)}.", p)
  }

  def all: List[Problem] = if (tac) (
    noDecision ++
      noEmail ++
      noRanking ++
      noTimes ++
      badTimes ++
      List(noResponses)
    ).flatten.toList
  else Nil

}