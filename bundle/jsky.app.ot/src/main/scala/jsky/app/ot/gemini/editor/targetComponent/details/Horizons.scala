package jsky.app.ot.gemini.editor.targetComponent.details

import java.io.IOException
import java.lang.reflect.UndeclaredThrowableException
import java.util.Date

import edu.gemini.horizons.api.HorizonsQuery.ObjectType
import edu.gemini.horizons.api.{ OrbitalElements, EphemerisEntry, HorizonsReply }
import edu.gemini.spModel.target.system.CoordinateParam.Units
import edu.gemini.spModel.target.system.{ NamedTarget, ConicTarget }
import edu.gemini.spModel.target.system.ITarget.Tag
import jsky.app.ot.gemini.editor.horizons.HorizonsService
import jsky.util.gui.DialogUtil

import scala.annotation.tailrec
import scalaz.{ Tag => _, _ }, Scalaz._
import scalaz.concurrent.Task

/** Pure functional interface to Horizons. */
object Horizons {

  /** The type of failures produced by HorizonsIO programs. */
  sealed abstract class HorizonsFailure(val message: String)
  object HorizonsFailure {
    @tailrec
    def fromThrowable(t: Throwable): HorizonsFailure =
      t match {
        case e: UndeclaredThrowableException => fromThrowable(e.getUndeclaredThrowable)
        case e: IOException                  => IOFailure(e)
        case e                               => UnknownError(e)
      }
  }

  case object CancelOrError     extends HorizonsFailure("User canceled or there was a error, which was already reported to the user.")

  sealed abstract class HorizonsException(t: Throwable) extends HorizonsFailure(t.getMessage)
  case class UnknownError(e: Throwable) extends HorizonsException(e)
  case class IOFailure(e: IOException)  extends HorizonsException(e)

  case object InvalidQuery      extends HorizonsFailure("Invalid query.")
  case object MultipleResults   extends HorizonsFailure("Multiple results were returned.")

  case object EmptyName         extends HorizonsFailure("The target name must be non-empty.")
  case object NoOrbitalElements extends HorizonsFailure("Cannot resolve orbital elements for named targets.")
  case object NoService         extends HorizonsFailure("No local Horizons service is available.")
  case object NoResults         extends HorizonsFailure("No results were found.")
  case object NoMinorBody       extends HorizonsFailure("Can't resolve the given ID to any minor body")
  case object Spacecraft        extends HorizonsFailure("Horizons suggests this is a spacecraft. Sorry, but OT can't use spacecrafts")


  /** The type of programs that perform Horizons lookups. */
  type HorizonsIO[A] = EitherT[Task, HorizonsFailure, A]
  object HorizonsIO {
    def either[A](a: => HorizonsFailure \/ A): HorizonsIO[A] = EitherT(Task.delay(a))
    def delay[A](a: => A): HorizonsIO[A] = either(a.right)
  }

  /** Syntax for HorizonsIO */
  implicit class HorizonsIOOps[A](hio: HorizonsIO[A]) {
    def invokeAndWait: Unit = // TODO: swing thread, somehow
      hio.run.attemptRun.leftMap(HorizonsFailure.fromThrowable).join match {
        case  \/-(())                   => // success!
        case -\/(CancelOrError)         => // do nothing!
        case -\/(IOFailure(e))          => DialogUtil.error(e)
        case -\/(UnknownError(t))       => DialogUtil.error(t)
        case -\/(e @ (InvalidQuery |
                      MultipleResults)) => DialogUtil.error("Internal error: " + e)
        case -\/(e)                     => DialogUtil.error(e.message)
      }
  }

  /** Alias for underlying Ephemeris representation. */
  type Ephemeris = java.util.List[EphemerisEntry]
  object Ephemeris {
    val empty: Ephemeris = java.util.Collections.emptyList[EphemerisEntry]
  }

  /**
   * The most general form of lookupConicTarget; construct a program to look up and construct a
   * conic target, given the name to assign, the Horizons object id to search for, an object type
   * hint, and a cache directive.
   */
  def lookupConicTargetById(
    name:     String,
    hObjId:   String,
    hObjType: ObjectType,
    date:     Date,
    useCache: Boolean
  ): HorizonsIO[(ConicTarget, Ephemeris)] =
    for {
      _ <- validateName(hObjId)
      s <- getService
      r <- lookup(s, hObjId, hObjType, date, useCache)
      t <- extractConicTarget(r, name)
      e <- extractEphemeris(r)
    } yield (t, e)

  /**
   * Construct a program to look up and construct a new conic target, given a name and an object
   * type hint.
   */
  def lookupConicTargetByName(
    name: String,
    hObjTypeHint: ObjectType,
    date: Date
  ): HorizonsIO[(ConicTarget, Ephemeris)] =
    lookupConicTargetById(name, name, hObjTypeHint, date, false) // use name as id

  /**
   * Construct a program to look up and construct the requested solar object.
   */
  def lookupSolarObject(name: String, obj: NamedTarget.SolarObject, date: Date): HorizonsIO[(NamedTarget, Ephemeris)] =
    for {
      s <- getService
      r <- lookup(s, obj.getHorizonsId, obj.objectType, date, true)
      t <- extractNamedTarget(name, r, obj)
      e <- extractEphemeris(r)
    } yield (t, e)

  /**
   * Constuct a program that extracts a named target from the given reply, with the supplied solar
   * object.
   */
  def extractNamedTarget(name: String, r: HorizonsReply, obj: NamedTarget.SolarObject): Horizons.HorizonsIO[NamedTarget] =
    HorizonsIO.either {

      // Should have the exact ID and obj
      if (r.hasObjectIdAndType &&
          r.getObjectId   == obj.expectId &&
          r.getObjectType == obj.objectType &&
          r.hasEphemeris) {

        val e = r.getEphemeris.get(0)
        val nt = new NamedTarget

        nt.setSolarObject(obj)
        nt.setName(name)
        nt.getRa .setAs(e.getCoordinates.getRaDeg,  Units.DEGREES)
        nt.getDec.setAs(e.getCoordinates.getDecDeg, Units.DEGREES)
        nt.setDateForPosition(e.getDate)
        nt.right

      } else {

        // We did not get back what we asked for
        UnknownError(new Exception("hmm " + r)).left

      }

    }

  /**
   * Constuct a program that extracts a conic target from the given reply, with the supplied name.
   */
  def extractConicTarget(r: HorizonsReply, name: String): Horizons.HorizonsIO[ConicTarget] =
    HorizonsIO.either {

      // Initialize a conic target from the name and reply in scope
      def init(ct: ConicTarget): Unit = {

        // Set the identifying information
        ct.setName(name)
        if (r.hasObjectIdAndType) {
          ct.setHorizonsObjectId(r.getObjectId)
          ct.setHorizonsObjectTypeOrdinal(r.getObjectType.ordinal())
        }

        // Set the orbital elements, if any
        if (r.hasOrbitalElements) {
          import OrbitalElements.Name._
          val es = r.getOrbitalElements

          // hm ok the es.getValue things can be null .. check
          ct.getAQ         .setOrZero(es.getValue(A))
          ct.getEpoch      .setOrZero(es.getValue(EPOCH))
          ct.getEpochOfPeri.setOrZero(es.getValue(TP))
          ct.getANode      .setOrZero(es.getValue(OM))
          ct.getPerihelion .setOrZero(es.getValue(W))
          ct.getInclination.setOrZero(es.getValue(IN))
          ct.getLM         .setOrZero(es.getValue(MA))
          ct.setE(es.getValue(EC)) // just a raw double for some reason
        }

        // Set the date/time and coordinates to correspond with the first ephemeris element, if any
        if (r.hasEphemeris) {
          val e = r.getEphemeris.get(0)
          ct.getRa .setAs(e.getCoordinates.getRaDeg,  Units.DEGREES)
          ct.getDec.setAs(e.getCoordinates.getDecDeg, Units.DEGREES)
          ct.setDateForPosition(e.getDate)
        }
      }

      // Construct and initialize a conic target of the appropriate type
      r.getObjectType match {
        case ObjectType.COMET      => (new ConicTarget(Tag.JPL_MINOR_BODY)   <| init).right
        case ObjectType.MINOR_BODY => (new ConicTarget(Tag.MPC_MINOR_PLANET) <| init).right
        case _                     => NoMinorBody.left
      }

    }

  /** Construct a program that extracts an ephemeris (possibly empty) from the given reply. */
  def extractEphemeris(r: HorizonsReply): HorizonsIO[Ephemeris] =
    HorizonsIO.delay(if (r.hasEphemeris) r.getEphemeris else Ephemeris.empty)

  /** Construct a program that ensures the given name is valid (non-empty). */
  def validateName(name: String): HorizonsIO[Unit] =
    HorizonsIO.either(name.isEmpty either EmptyName or (()))

  /** A program that retrieves the Horizons service, if available. */
  val getService: HorizonsIO[HorizonsService] =
    HorizonsIO.either(Option(HorizonsService.getInstance) \/> NoService)

  /** Returns previous results if they match the requested objectId, type, and date. */
  def getCachedResult(
    service: HorizonsService,
    hObjId: String,
    hObjType: ObjectType,
    date: Date
  ): Option[HorizonsReply] =
    Option(service.getLastResult).filter { r =>
      // just assume anything in the reply can be null
      Option(r.getObjectId).exists(_.toString === hObjId) &&
      (r.getObjectType == hObjType)                       &&
      r.hasEphemeris                                      &&
      Option(r.getEphemeris).filter(_.size() > 0).exists(_.get(0).getDate == date)
    }

  /**
   * Construct a program to look up a target on the provided service and return the Horizons reply.
   */
  def lookup(
    service: HorizonsService,
    hObjId: String,
    hObjType: ObjectType,
    date: Date,
    useCache: Boolean
  ): HorizonsIO[HorizonsReply] =
    HorizonsIO.either {

      // Use the cached result if possible
      getCachedResult(service, hObjId, hObjType, date).map(_.right).filter(_ => useCache).getOrElse {

        // New query
        service.setInitialDate(date)
        service.setObjectId(hObjId)
        service.setObjectType(hObjType)

        // Here is the blocking call. If we get null back then it means the user cancelled or there
        // was an error, which will have been reported to the user already. This call also
        // internally handles multiple answer disambiguation. This should probably all get up into
        // this code but we'll leave it for now.
        Option(service.execute)
          .fold[HorizonsFailure \/ HorizonsReply](CancelOrError.left) { reply =>
          import HorizonsReply.ReplyType._

          reply.getReplyType match {

            // Some error conditions
            case null            => CancelOrError  .left
            case NO_RESULTS      => NoResults      .left
            case SPACECRAFT      => Spacecraft     .left
            case INVALID_QUERY   => InvalidQuery   .left
            case MUTLIPLE_ANSWER => MultipleResults.left

            // Usable results!
            case otherwise => reply.right

          }
        }

      }

    }

}


