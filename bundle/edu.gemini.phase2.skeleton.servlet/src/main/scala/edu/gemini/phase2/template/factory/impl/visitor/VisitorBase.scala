package edu.gemini.phase2.template.factory.impl.visitor

import edu.gemini.phase2.template.factory.impl._
import edu.gemini.spModel.gemini.visitor.blueprint.SpVisitorBlueprint
import edu.gemini.spModel.gemini.visitor.VisitorInstrument
import edu.gemini.pot.sp.{ISPGroup, ISPObservation, SPComponentType}

//noinspection MutatorLikeMethodIsParameterless
trait VisitorBase extends GroupInitializer[SpVisitorBlueprint] with TemplateDsl {

  override val program: String =
    "VISITOR INSTRUMENT PHASE I/II MAPPING BPS"

  override val seqConfigCompType: SPComponentType =
    VisitorInstrument.SP_TYPE

  implicit def pimpInst(obs: ISPObservation) = new {

    val ed: StaticObservationEditor[VisitorInstrument] =
      StaticObservationEditor[VisitorInstrument](obs, instrumentType)

    def setName(n: String): Either[String, Unit] =
      ed.updateInstrument(_.setName(n))

    def setWavelength(microns: Double): Either[String, Unit] =
      ed.updateInstrument(_.setWavelength(microns))

    def setPosAngle(degrees: Double): Either[String, Unit] =
      ed.updateInstrument(_.setPosAngleDegrees(degrees))

  }

  // HACK: override superclass initialize to hang onto db reference
  var db: Option[TemplateDb] = None

  override def initialize(db: TemplateDb): Maybe[ISPGroup] =
    try {
      this.db = Some(db)
      super.initialize(db)
    } finally {
      this.db = None
    }

  def attempt[A](a: => A) = tryFold(a) {
    e =>
      e.printStackTrace()
      e.getMessage
    }

  // DSL Setters
  def setName: Setter[String] =
    Setter[String](blueprint.name)(_.setName(_))

  private def lookupInst: Option[VisitorInst] =
    VisitorInst.findByName(blueprint.name)

  override def notes: List[String] =
    lookupInst.map(_.noteTitles).getOrElse(Nil)

  def setWavelength: Setter[Double] =
    Setter[Double](lookupInst.map(_.wavelength.toMicrons).getOrElse(0.0))(_.setWavelength(_))

  def setPosAngle: Setter[Double] =
    Setter[Double](lookupInst.map(_.positionAngle.toDegrees).getOrElse(0.0))(_.setPosAngle(_))
}
