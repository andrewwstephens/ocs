package edu.gemini.catalog.image

import java.io._
import java.net.URL
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.logging.Logger

import edu.gemini.spModel.core.{Angle, Coordinates, Declination, RightAscension, Site}
import nom.tam.fits.{Fits, Header}

import scala.util.matching.Regex
import scala.math._
import scala.reflect.ClassTag
import scalaz.Scalaz._
import scalaz._
import scalaz.concurrent.Task

/**
  * Angular size of an image. Used to store the real size of images instead to the requested size
  */
case class AngularSize(ra: Angle, dec: Angle) {
  def this(size: Angle) = this(size, size)

  def halfDec: Angle = ~(dec / 2)
  def halfRa: Angle = ~(ra / 2)
}

case object AngularSize {
  val zero = AngularSize(Angle.zero, Angle.zero)

  /** @group Typeclass Instances */
  implicit val equal: Equal[AngularSize] = Equal.equalA[AngularSize]

  /** @group Typeclass Instances */
  implicit val order: Order[AngularSize] =
    Order.orderBy(a => (a.ra, a.dec))
}

/**
  * Query to request an image for a catalog and coordinates
  */
case class ImageSearchQuery(catalog: ImageCatalog, coordinates: Coordinates, size: AngularSize, site: Option[Site]) {
  import ImageSearchQuery._

  def url: NonEmptyList[URL] = catalog.queryUrl(coordinates, site)

  def fileName(extension: String): String = s"img_${catalog.id.filePrefix}_${coordinates.toFilePart}_${size.toFilePart}.$extension"

  def isNearby(query: ImageSearchQuery): Boolean =
    catalog === query.catalog && isNearby(query.coordinates)

  def isNearby(c: Coordinates): Boolean =
    coordinates.angularDistance(c) <= maxDistance
}

object ImageSearchQuery {
  /** @group Typeclass Instances */
  implicit val equals: Equal[ImageSearchQuery] = Equal.equalA[ImageSearchQuery]

  /** Harcoded value of the max FoV for any Gemini instrument */
  val maxDistance: Angle = Angle.fromArcmin(4.5)

  implicit class DeclinationShow(val d: Declination) extends AnyVal {
    def toFilePart: String = Declination.formatDMS(d, "#", 2)
  }

  implicit class RightAscensionShow(val a: RightAscension) extends AnyVal {
    def toFilePart: String = a.toAngle.formatHMS.replace(":", "#")
  }

  implicit class CoordinatesShow(val c: Coordinates) extends AnyVal {
    def toFilePart: String = s"ra_${c.ra.toFilePart}_dec_${c.dec.toFilePart}"
  }

  implicit class AngularSizeShow(val s: AngularSize) extends AnyVal {
    def toFilePart: String = f"w_${s.ra.toArcsecs.toInt}_h_${s.dec.toArcsecs.toInt}"
  }
}

/**
  * Image in the file system
  */
case class ImageInFile(query: ImageSearchQuery, file: Path, fileSize: Long) {
  /**
    * Tests if the image contains the coordinates considering the catalog overlay
    */
  def contains(c: Coordinates): Boolean = contains(c, query.catalog.adjacentOverlap)

    /**
    * Tests if the image contains the coordinates parameter
    */
  def contains(c: Coordinates, ε: Angle): Boolean = {
    import ImageInFile.δ

    // Convert everything to radians, calculations in Angle-space don't work due to range overflow
    // Image size
    val εφ = (query.size.halfDec.toRadians - ε.toRadians).max(0)
    val ελ = (query.size.halfRa.toRadians  - ε.toRadians).max(0)

    // target coordinates
    val φ = c.dec.toDegrees.toRadians
    val λ = c.ra.toAngle.toRadians

    // image coordinates
    val φ0 = query.coordinates.dec.toDegrees.toRadians
    val λ0 = query.coordinates.ra.toAngle.toRadians

    // In principle dec can be 90 leading to a 0 on θ
    // In practice it is just very close to zero so it doesn't produce a zero
    // division but as a result essentially the whole sky matches
    // TODO: Perhaps the zenith case should be handled in particular
    val θ = cos(φ0)

    // Distance
    val Δφ = δ(φ, φ0)
    val Δλ = δ(λ, λ0)

    Δφ <= εφ && Δλ*θ <= ελ
  }
}

object ImageInFile {
  /** @group Typeclass Instances */
  implicit val equals: Equal[ImageInFile] = Equal.equalA[ImageInFile]
  val IdsRegex: String = CatalogId.all.map(f => s"${f.filePrefix}").mkString("|")
  val FileRegex: Regex = s"""img_($IdsRegex)_ra_([^_]+)_dec_([^_]+)_w_(\\d*)_h_(\\d*)\\.fits.*""".r

  val π = Pi

  /**
    * Calculates the minimal distance between two angles in radians
    */
  def δ(α: Double, β: Double): Double =
    min(2*π - abs(α - β), abs(α - β))

  /**
    * Decode a file name into an image entry
    */
  def entryFromFile(file: File): Option[ImageInFile] = file.getName match {
    case FileRegex(c, raStr, decStr, w, h) =>
      for {
        catalog <- ImageCatalog.byId(c)
        ra      <- Angle.parseHMS(raStr.replace("#", ":")).map(RightAscension.fromAngle).toOption
        dec     <- Angle.parseDMS(decStr.replace("#", ":")).toOption.map(_.toDegrees).flatMap(Declination.fromDegrees)
        width   <- Angle.fromArcsecs(w.toInt).some
        height  <- Angle.fromArcsecs(h.toInt).some
      } yield ImageInFile(ImageSearchQuery(catalog, Coordinates(ra, dec), AngularSize(width, height), none), file.toPath, file.length())
    case _                                => None
  }
}

/**
  * Downloads images from a remote server and stores them in the file system
  */
object ImageCatalogClient {
  val Log: Logger = Logger.getLogger(this.getClass.getName)

  /**
    * Downloads the requested image to file in the cache
    */
  def downloadImageToFile(cacheDir: Path, url: URL, query: ImageSearchQuery): Task[ImageInFile] = {

    case class ConnectionDescriptor(contentType: Option[String], contentEncoding: Option[String], stream: InputStream) {
      def extension: String = (contentEncoding, contentType) match {
          case (Some("x-gzip"), _)                                                              => "fits.gz"
          // REL-2776 At some places on the sky DSS returns an error, the HTTP error code is ok but the body contains no image
          case (None, Some(s)) if s.contains("text/html") && url.getPath.contains("dss_search") => throw new RuntimeException("Image not found at image server")
          case (None, Some(s)) if s.endsWith("fits")                                            => "fits"
          case _                                                                                => "tmp"
        }

      def compressed: Boolean = contentEncoding === "x-gzip".some
    }

    def createTmpFile: Task[File] = Task.delay {
      File.createTempFile(".img", ".fits", cacheDir.toFile)
    }

    def openConnection: Task[ConnectionDescriptor] = Task.delay {
      Log.info(s"Downloading image at $url")
      val connection = url.openConnection()
      connection.setReadTimeout(90 * 1000) // Montage can take a while
      ConnectionDescriptor(Option(connection.getContentType), Option(connection.getContentEncoding), connection.getInputStream)
    }

    /**
      * Downloads the url and writes it to a file
      */
    def writeToTempFile(stream: InputStream, file: File): Task[Unit] = Task.delay {
      Files.copy(stream, file.toPath, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
      * Attempts to calculate the center and size of the downloaded image from the actual header
      */
    def parseHeader(descriptor: ConnectionDescriptor, tmpFile: File): Task[ImageSearchQuery] = {
      def parse(fin: FileInputStream): Task[ImageSearchQuery] = Task.delay {
        // This means we do a second read on the file. It could be done in one go but this approach is simpler
        // This is a one-time cost for each image after the download. Note that only the header is read
        FitsHeadersParser.parseFitsGeometry(fin, descriptor.compressed).fold(_ => query, g =>
          g.bifoldLeft(query)
          { (q, c) => c.fold(q)(c => q.copy(coordinates = c)) }
          { (q, s) => s.fold(q)(s => q.copy(size = s )) }
        )
      }

      for {
        in <- Task.delay(new FileInputStream(tmpFile))
        q  <- parse(in).onFinish(_ => Task.delay(in.close()))
      } yield q
    }

    def moveToFinalFile(extension: String, tmpFile: File, query: ImageSearchQuery): Task[ImageInFile] = Task.delay {
      val destFileName = cacheDir.resolve(query.fileName(extension))
      val destFile = destFileName.toFile
      // If the destination file is present don't overwrite
      if (!destFile.exists()) {
        val finalFile = Files.move(tmpFile.toPath, cacheDir.resolve(destFileName), StandardCopyOption.ATOMIC_MOVE)
        ImageInFile(query, finalFile, finalFile.toFile.length())
      } else {
        tmpFile.delete()
        ImageInFile(query, destFileName, destFile.length())
      }
    }

    def deleteIfInError(f: File): Option[Throwable] => Task[Unit] = {
      case Some(_) => Task.delay(f.delete)
      case None    => Task.now(())
    }

    for {
      _           <- ImageCacheOnDisk.mkCacheDir(cacheDir)
      tempFile    <- createTmpFile
      desc        <- openConnection
      _           <- writeToTempFile(desc.stream, tempFile).onFinish(deleteIfInError(tempFile))
      queryHeader <- parseHeader(desc, tempFile).onFinish(deleteIfInError(tempFile))
      file        <- moveToFinalFile(desc.extension, tempFile, queryHeader).onFinish(deleteIfInError(tempFile))
    } yield file
  }
}

object FitsHeadersParser {
  val RaHeader      = "CRVAL1"
  val DecHeader     = "CRVAL2"
  val RaAxisPixels  = "NAXIS1"
  val DecAxisPixels = "NAXIS2"
  val RaPixelSize   = "CDELT1"
  val DecPixelSize  = "CDELT2"

  def headerValue[A](header: Header, key: String)(implicit clazz: ClassTag[A]): Option[A] =
    clazz match {
      case ClassTag.Int    if header.containsKey(key) => clazz.unapply(header.getIntValue(key))
      case ClassTag.Double if header.containsKey(key) => clazz.unapply(header.getDoubleValue(key))
      case _                                          => none
    }

  /**
    * Attempts to read a fits file header and extract the center coordinates and dimensions
    */
  def parseFitsGeometry(fin: FileInputStream, compressed: Boolean): Throwable \/ (Option[Coordinates], Option[AngularSize]) = {
    \/.fromTryCatchNonFatal {
      val fits = new Fits(fin, compressed)
      val basicHDU = fits.getHDU(0).getHeader
      val coord =
        for {
          raD  <- headerValue[Double](basicHDU, RaHeader)
          decD <- headerValue[Double](basicHDU, DecHeader)
          ra   <- RightAscension.fromAngle(Angle.fromDegrees(raD)).some
          dec  <- Declination.fromDegrees(decD)
        } yield Coordinates(ra, dec)

      val size =
        for {
          raPix   <- headerValue[Int](basicHDU, RaAxisPixels)
          decPix  <- headerValue[Int](basicHDU, DecAxisPixels)
          raSize  <- headerValue[Double](basicHDU, RaPixelSize)
          decSize <- headerValue[Double](basicHDU, DecPixelSize)
        } yield AngularSize(Angle.fromDegrees(raPix * abs(raSize)), Angle.fromDegrees(decPix * abs(decSize)))

      (coord, size.orElse(AngularSize(ImageCatalog.DefaultImageSize, ImageCatalog.DefaultImageSize).some))
    }
  }
}
