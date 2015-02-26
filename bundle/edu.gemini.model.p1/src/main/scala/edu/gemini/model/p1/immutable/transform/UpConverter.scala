package edu.gemini.model.p1.immutable.transform

import scala.util.matching.Regex
import xml.{Node => XMLNode, Text}

import scalaz._
import Scalaz._

import edu.gemini.model.p1.immutable.{SemesterOption, Proposal, Semester}
import edu.gemini.model.p1.immutable.transform.XMLConverter._
import scala.xml.transform.BasicTransformer

case class ConversionResult(transformed: Boolean, from: Semester, changes: Seq[String], root: XMLNode)

object UpConverter {
  type UpConversionResult = ValidationNel[String, ConversionResult]

  private val VersionToSemesterRegex = """(\d\d\d\d)\.(1|2)\.(\d*)""".r // Extract semester from the proposal version

  private def toSemester(version: String):Semester = version match {
    case "2013.2.1"                        => Semester(2013, SemesterOption.B)
    case "1.0.14"                          => Semester(2013, SemesterOption.A)
    case "1.0.0"                           => Semester(2012, SemesterOption.B)
    case VersionToSemesterRegex(s, "1", _) => Semester(s.toInt, SemesterOption.A)
    case VersionToSemesterRegex(s, "2", _) => Semester(s.toInt, SemesterOption.B)
    case _                                 => Semester.current
  }

  def upConvert(node: XMLNode):UpConversionResult = {
    def toConversionResult(result: StepResult) = {
      val originalVersion = (node \ "@schemaVersion").text
      ConversionResult(originalVersion != Proposal.currentSchemaVersion, toSemester(originalVersion), result.change, result.node.head)
    }
    convert(node).map(toConversionResult)
  }

  // Sequence of conversions for proposals from a given semester
  val from2015A:List[SemesterConverter] = List(SemesterConverterToCurrent, SemesterConverter2015ATo2015B, SemesterConverter2014BTo2015A, SemesterConverter2014BTo2014BSV, SemesterConverter2014ATo2014B, LastStepConverter(Semester(2015, SemesterOption.B)))
  val from2015AToKR:List[SemesterConverter] = List(SemesterConverterToCurrent, SemesterConverter2015ATo2015B, SemesterConverter2014BTo2015A, SemesterConverter2014BTo2014BSV, SemesterConverter2014ATo2014B, LastStepConverter(Semester(2015, SemesterOption.A)))
  val from2014BSV:List[SemesterConverter] = List(SemesterConverterToCurrent, SemesterConverter2015ATo2015B, SemesterConverter2014BTo2015A, SemesterConverter2014BTo2014BSV, SemesterConverter2014ATo2014B, LastStepConverter(Semester(2014, SemesterOption.B)))
  val from2014BToSV:List[SemesterConverter] = List(SemesterConverterToCurrent, SemesterConverter2015ATo2015B, SemesterConverter2014BTo2015A, SemesterConverter2014BTo2014BSV, SemesterConverter2014ATo2014B, LastStepConverter(Semester(2014, SemesterOption.B)))
  val from2014A:List[SemesterConverter] = List(SemesterConverterToCurrent, SemesterConverter2015ATo2015B, SemesterConverter2014BTo2015A, SemesterConverter2014BTo2014BSV, SemesterConverter2014ATo2014B, LastStepConverter(Semester(2014, SemesterOption.A)))
  val from2013B:List[SemesterConverter] = List(SemesterConverterToCurrent, SemesterConverter2015ATo2015B, SemesterConverter2014BTo2015A, SemesterConverter2014BTo2014BSV, SemesterConverter2013BTo2014A, SemesterConverter2014ATo2014B, LastStepConverter(Semester(2013, SemesterOption.B)))
  val from2013A:List[SemesterConverter] = List(SemesterConverterToCurrent, SemesterConverter2015ATo2015B, SemesterConverter2014BTo2015A, SemesterConverter2014BTo2014BSV, SemesterConverter2013ATo2013B, SemesterConverter2013BTo2014A, SemesterConverter2014ATo2014B, LastStepConverter(Semester(2013, SemesterOption.A)))
  val from2012B:List[SemesterConverter] = List(SemesterConverterToCurrent, SemesterConverter2015ATo2015B, SemesterConverter2014BTo2015A, SemesterConverter2014BTo2014BSV, SemesterConverter2012BTo2013A, SemesterConverter2013ATo2013B, SemesterConverter2013BTo2014A, SemesterConverter2014ATo2014B, LastStepConverter(Semester(2012, SemesterOption.B)))

  /**
   * Converts one version of a proposal node to the next
   *
   * @param node Root node of the proposal
   * @return The result of the operation containing either an error or a success that contains a list of change description and a converted XML
   */
  def convert(node: XMLNode):Result = node match {
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@schemaVersion").text == Proposal.currentSchemaVersion =>
      StepResult(Nil, node).successNel[String]
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@schemaVersion").text == "2015.1.1"                    =>
      from2015A.concatenate.convert(node)
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@schemaVersion").text == "2015.1.2"                    =>
      from2015AToKR.concatenate.convert(node)
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@schemaVersion").text == "2014.2.2"                    =>
      from2014BSV.concatenate.convert(node)
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@schemaVersion").text == "2014.2.1"                    =>
      from2014BToSV.concatenate.convert(node)
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@schemaVersion").text == "2014.1.2"                    =>
      from2014A.concatenate.convert(node)
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@schemaVersion").text == "2014.1.1"                    =>
      from2014A.concatenate.convert(node)
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@schemaVersion").text == "2013.2.1"                    =>
      from2013B.concatenate.convert(node)
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@schemaVersion").text == "1.0.14"                      =>
      from2013A.concatenate.convert(node)
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@schemaVersion").text == "1.0.0"                       =>
      from2012B.concatenate.convert(node)
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@schemaVersion").isEmpty                               =>
      "I don't know how to handle a proposal without version".failNel
    case p @ <proposal>{ns @ _*}</proposal>                                                                 =>
      val version = (p \\ "@schemaVersion").text
      s"I don't know how to handle a proposal with version $version".failNel
    case _                                                                                                  =>
      "Unknown xml file format".failNel
  }
}

/**
 * Each semester needs to define its own SemesterConverter to account for the P1 model changes of that semester ONLY
 *
 * This way a proposal will be converted from one semester to the next from its original semester to the current one
 *
 */
trait SemesterConverter {
  val transformers: List[TransformFunction]
  private def compact(s:StepResult) = StepResult(s.change.distinct, s.node)
  def convert(node: XMLNode):Result = XMLConverter.transform(node, transformers:_*).map(compact)

  def removeBlueprint(instrument:String, name:String):TransformFunction = {
    case p @ <blueprints>{ns @ _*}</blueprints> if (p \\ instrument).nonEmpty =>
      // Remove nodes that match the instrument but preserve the rest
      val filtered = ns.filter{n => (n \\ instrument).isEmpty}
      StepResult(s"The original proposal contained $name observations. The instrument is not available and those resources have been removed.", <blueprints>{filtered}</blueprints>).successNel
  }
}

object SemesterConverter {

  // definition of a ZeroConverter
  private case object ZeroSemesterConverter extends SemesterConverter {
    override val transformers: List[TransformFunction] = Nil
  }

  // Monoid conversions
  implicit val zeroVersionConverter:Monoid[SemesterConverter] = Monoid.instance[SemesterConverter]((a, b) => new SemesterConverter { override val transformers = a.transformers |+| b.transformers}, ZeroSemesterConverter)
}

/**
 * This converter is to include any possible last message to the conversion log without
 */
case class LastStepConverter(semester: Semester) extends SemesterConverter {
  val notifyToUseOlderPIT:TransformFunction = {
    case n =>
      StepResult(s"Please use the PIT from semester ${semester.display} to view the unmodified proposal", n).successNel
  }
  override val transformers = List(notifyToUseOlderPIT)
}

/**
 * This converter will upgrade to 2015B
 */
case object SemesterConverter2015ATo2015B extends SemesterConverter {
  val gracesFiberTransformer: TransformFunction = {
    case p @ <graces>{ns @ _*}</graces> if (p \\ "fiberMode").nonEmpty =>
      val fiberMode = (p \\ "fiberMode").text
      val gracesRegex = "(Graces )?(.*)".r
      def transformFiberMode(name: String) = name match {
        case gracesRegex(_, "2 fibers (target + sky, R~30000)") => "2 fibers (target+sky, R~40k)"
        case gracesRegex(_, "1 fiber (target, R~50000)")        => "1 fiber (target only, R~67.5k)"
        case _                                                  => name
      }

      object GracesFiberMode extends BasicTransformer {
        override def transform(n: xml.Node): xml.NodeSeq = n match {
          case <fiberMode>{name}</fiberMode> => <fiberMode>{transformFiberMode(name.text)}</fiberMode>
          case <name>{name}</name>           => <name>Graces {transformFiberMode(name.text)}</name>
          case elem: xml.Elem                => elem.copy(child = elem.child.flatMap(transform _))
          case _                             => n
        }
      }

      StepResult(s"Graces Fiber mode $fiberMode updated to ${transformFiberMode(fiberMode)}.", <graces>{GracesFiberMode.transform(ns)}</graces>).successNel
  }

  val transformers = List(gracesFiberTransformer)
}

/**
 * This converter is to convert to 2015A model
 */
case object SemesterConverter2014BTo2015A extends SemesterConverter {
  val gsSubmission:TransformFunction = {
    case <ngo>{ns @ _*}</ngo> if (ns \\ "partner").text == "gs" => StepResult("Gemini Staff is no longer a valid partner and this time request has been removed", Nil).successNel
  }
  val texesRemoved = removeBlueprint("texes", "Texes")
  override val transformers = List(gsSubmission, texesRemoved)
}
/**
 * This converter is to current, as a minimum you need to convert a proposal to be the current version and semester
 */
case object SemesterConverterToSV extends SemesterConverter {
  val current = Semester.current
  val schemaVersionTransformToCurrent:TransformFunction = {
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@tacCategory").nonEmpty =>
      StepResult(s"Updated schema version to ${Proposal.currentSchemaVersion}", <proposal tacCategory={(p \ "@tacCategory").text} schemaVersion={Proposal.currentSchemaVersion}>{ns}</proposal>).successNel
    case <proposal>{ns @ _*}</proposal>                                      =>
      StepResult(s"Updated schema version to ${Proposal.currentSchemaVersion}", <proposal schemaVersion={Proposal.currentSchemaVersion}>{ns}</proposal>).successNel
  }

  override val transformers = List(schemaVersionTransformToCurrent)
}

/**
 * This converter is to current, as a minimum you need to convert a proposal to be the current version and semester
 */
case object SemesterConverterToCurrent extends SemesterConverter {
  val current = Semester.current
  val semesterTransformToCurrent:TransformFunction = {
    case s @ <semester/> =>
      StepResult(s"Updated semester to ${current.display}", <semester year={current.year.toString} half={current.half.toString}/>).successNel
  }
  val schemaVersionTransformToCurrent:TransformFunction = {
    case p @ <proposal>{ns @ _*}</proposal> if (p \ "@tacCategory").nonEmpty =>
      StepResult(s"Updated schema version to ${Proposal.currentSchemaVersion}", <proposal tacCategory={(p \ "@tacCategory").text} schemaVersion={Proposal.currentSchemaVersion}>{ns}</proposal>).successNel
    case <proposal>{ns @ _*}</proposal>                                      =>
      StepResult(s"Updated schema version to ${Proposal.currentSchemaVersion}", <proposal schemaVersion={Proposal.currentSchemaVersion}>{ns}</proposal>).successNel
  }

  override val transformers = List(schemaVersionTransformToCurrent, semesterTransformToCurrent)
}

case object SemesterConverter2014BTo2014BSV extends SemesterConverter {
  val addAltairToGmosN: TransformFunction = {
    case p @ <gmosN>{ns @ _*}</gmosN> if (p \\ "altair").isEmpty =>
      val noAltair = <altair><none/></altair>

      val regularName    = """GMOS-N (IFU|Imaging|LongSlit|MOS) (.*)""".r
      val nodShuffleName = """GMOS-N (LongSlit|MOS) N\+S (.*)""".r

      def transformName(name: String) = name match {
        case nodShuffleName(gmosType, rest) => s"GMOS-N $gmosType N+S None $rest"
        case regularName(gmosType, rest)    => s"GMOS-N $gmosType None $rest"
        case _                              => name
      }
      object GmosNNoAltair extends BasicTransformer {
        override def transform(n: xml.Node): xml.NodeSeq = n match {
          case <name>{name}</name>                           => noAltair +: <name>{transformName(name.text)}</name>
          case elem: xml.Elem                                => elem.copy(child = elem.child.flatMap(transform _))
          case _                                             => n
        }
      }

      StepResult("GMOS-N observations without an Altair setting have had Adaptive Optics set to None.", <gmosN>{GmosNNoAltair.transform(ns)}</gmosN>).successNel
  }
  
  val transformers = List(addAltairToGmosN)
}

case object SemesterConverter2014ATo2014B extends SemesterConverter {
  val transformers = Nil
}

/**
 * Converter from 2013B to 2014A
 */
case object SemesterConverter2013BTo2014A extends SemesterConverter {
  val trecsRemoved = removeBlueprint("trecs", "T-ReCS")
  val michelleRemoved = removeBlueprint("michelle", "Michelle")
  val niciRemoved = removeBlueprint("nici", "NICI")
  val (oldSlit, newSlit) = ("0.25 arcsec slit", "0.5 arcsec slit")
  val updateGmosNSlit: TransformFunction = {
     case p @ <gmosN>{ns @ _*}</gmosN> if (p \ "longslit" \ "fpu").text == oldSlit =>
       object SlitTransformer extends BasicTransformer {
         val nameRegEx = s"(.*)$oldSlit(.*)"

         override def transform(n: xml.Node): xml.NodeSeq = n match {
           case <name>{name}</name> if name.text.matches(nameRegEx) => <name>{name.text.replace(oldSlit, newSlit)}</name>
           case <fpu>{_}</fpu>                                      => <fpu>{newSlit}</fpu>
           case elem: xml.Elem                                      => elem.copy(child=elem.child.flatMap(transform _))
           case _                                                   => n
         }
       }
       // Convert the 0.25 longslit to 0.5
       StepResult("The unavailable GMOS-N 0.25\" slit has been converted to the 0.5\" slit.", <gmosN>{SlitTransformer.transform(ns)}</gmosN>).successNel
    }
  val addGnirsCentralWavelength: TransformFunction = {
    case p @ <gnirs>{ns @ _*}</gnirs> if (p \ "spectroscopy").nonEmpty && (p \\ "centralWavelength").isEmpty =>
      val centralWavelengthDefault = <centralWavelength>&lt; 2.5um</centralWavelength>
      object CentralWavelengthTransformer extends BasicTransformer {
        override def transform(n: xml.Node): xml.NodeSeq = n match {
          case <name>{name}</name>                           => <name>{name + " < 2.5um"}</name>
          case s @ <spectroscopy>{nodes @ _*}</spectroscopy> => <spectroscopy id={s.attribute("id")}>{transform(nodes) ++ centralWavelengthDefault}</spectroscopy>
          case elem: xml.Elem                                => elem.copy(child=elem.child.flatMap(transform _))
          case _                                             => n
        }
      }

      StepResult("GNIRS observation doesn't have a central wavelength range, assigning to '< 2.5um'", <gnirs>{CentralWavelengthTransformer.transform(ns)}</gnirs>).successNel
  }
  val removeF2NarrowBandFilter: TransformFunction = {
    case p @ <flamingos2>{ns @ _*}</flamingos2> if (p \ "imaging").nonEmpty =>
      val narrowBandFilterRegex = """F10\d\d \(1.0\d\d um\)"""
      val replacementFilter     = "Y (1.020 um)"
      val defaultFilter         = <filter>{replacementFilter}</filter>

      object NarrowbandFilterTransformer extends BasicTransformer {
        override def transform(n: xml.Node): xml.NodeSeq = n match {
          case <name>{name}</name>                                                     => <name>{name.text.replaceAll(narrowBandFilterRegex, replacementFilter)}</name>
          case <filter>{filter}</filter> if filter.text.matches(narrowBandFilterRegex) => defaultFilter
          case elem: xml.Elem                                                          => elem.copy(child=elem.child.flatMap(transform _))
          case _                                                                       => n
        }
      }
      StepResult("The unavailable Flamingos2 filters F1056 (1.056 um)/F1063 (1.063 um) have been converted to Y (1.020 um).", <flamingos2>{NarrowbandFilterTransformer.transform(ns)}</flamingos2>).successNel
  }
  def f23KYFilterCondition(p: xml.Node):Boolean = ((p \ "longslit").nonEmpty || (p \ "mos").nonEmpty) && (p \\ "disperser").text == "R3K" && (p \\ "filter").text == "Y (1.020 um)"
  val replaceF2R3KYFilter: TransformFunction = {
    case p @ <flamingos2>{ns @ _*}</flamingos2> if f23KYFilterCondition(p) =>
      val yFilter           = """Y \(1.020 um\)"""
      val replacementFilter = "J-lo (1.122 um)"
      val defaultFilter     = <filter>{replacementFilter}</filter>

      object F23KYFilterTransformer extends BasicTransformer {
        override def transform(n: xml.Node): xml.NodeSeq = n match {
          case <name>{name}</name>                                       => <name>{name.text.replaceAll(yFilter, replacementFilter)}</name>
          case <filter>{filter}</filter> if filter.text.matches(yFilter) => defaultFilter
          case elem: xml.Elem                                            => elem.copy(child=elem.child.flatMap(transform _))
          case _                                                         => n
        }
      }
      StepResult("The unavailable Flamingos2 configuration R3K + Y has been converted to R3K + J-lo.", <flamingos2>{F23KYFilterTransformer.transform(ns)}</flamingos2>).successNel
  }
  object GMOSAddFpuTransformer extends BasicTransformer {
    val slitName   = "1.0 arcsec slit"
    val fpuDefault = <fpu>{slitName}</fpu>
    override def transform(n: xml.Node): xml.NodeSeq = n match {
      case <name>{name}</name>         => <name>{name.text.replaceAll("MOS", s"MOS $slitName")}</name>
      case s @ <mos>{nodes @ _*}</mos> => <mos id={s.attribute("id")}>{transform(nodes) ++ fpuDefault}</mos>
      case elem: xml.Elem              => elem.copy(child=elem.child.flatMap(transform _))
      case _                           => n
    }
  }
  def gmosMOSSpectroscopyCondition(p: xml.Node):Boolean = (p \ "regime").text == "optical" && (p \ "mos").nonEmpty
  val addFpuToGmosMOSSpectroscopy: TransformFunction = {
    case p @ <gmosS>{ns @ _*}</gmosS> if gmosMOSSpectroscopyCondition(p) =>
      StepResult("A default 1\" slit has been added to the GMOS-S MOS observation.", <gmosS>{GMOSAddFpuTransformer.transform(ns)}</gmosS>).successNel
    case p @ <gmosN>{ns @ _*}</gmosN> if gmosMOSSpectroscopyCondition(p) =>
      StepResult("A default 1\" slit has been added to the GMOS-N MOS observation.", <gmosN>{GMOSAddFpuTransformer.transform(ns)}</gmosN>).successNel
  }
  override val transformers = List(trecsRemoved, michelleRemoved, niciRemoved, updateGmosNSlit, addGnirsCentralWavelength, removeF2NarrowBandFilter, replaceF2R3KYFilter, addFpuToGmosMOSSpectroscopy)
}

/**
 * Converter from 2013A to 2013B
 *
 * On 2013B there no breaking changes in the model
 */
case object SemesterConverter2013ATo2013B extends SemesterConverter {
  override val transformers = Nil
}

/**
 * Converter from 2012B to 2013A
 *
 * See the changelog
 */
case object SemesterConverter2012BTo2013A extends SemesterConverter {

  val band3OptionChosen:TransformFunction = {
    case m @ <meta>{ns @ _*}</meta> if (m \ "@band3optionChosen").isEmpty => StepResult("Band3 Option is missing, set as false", <meta band3optionChosen="false">{ns}</meta>).successNel
  }
  val herbigHaroObjects:TransformFunction = {
    case Text("Herbig-Haro stars") => StepResult("Keyword 'Herbig-Haro stars' renamed to 'Herbig-Haro objects'", Text("Herbig-Haro objects")).successNel
  }
  val ukSubmission:TransformFunction = {
    case <ngo>{ns @ _*}</ngo> if (ns \\ "partner").text == "uk" => StepResult("NGO acceptance from the United Kingdom has been removed", Nil).successNel
  }
  val ukNgoAuthority:TransformFunction = {
    case <ngoauthority>uk</ngoauthority> => StepResult("The United Kingdom was marked as ITAC NGO authority and has been removed", Nil).successNel
  }
  override val transformers = List(band3OptionChosen, herbigHaroObjects, ukSubmission, ukNgoAuthority)
}