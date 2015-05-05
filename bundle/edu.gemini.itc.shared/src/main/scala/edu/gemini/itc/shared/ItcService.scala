package edu.gemini.itc.shared

import edu.gemini.spModel.core.Peer
import edu.gemini.util.trpc.client.TrpcClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.swing.Color
import scalaz.{Failure, Success, Validation}

/** The data structures here are an attempt to unify the results produced by the different instrument recipes.
  * Results are either a few simple numbers in case of imaging or a set charts made up by data series with (x,y)
  * value pairs for spectroscopy.
  *
  * For spectroscopy the data series are also used to produce some text data files which can be downloaded from
  * the result pages of the web application. In theory the text files can be produced from the data series, but
  * unfortunately there is some special handling involved for some of the instruments and therefore those files
  * are created by the recipes and then added to the result data as strings. Maybe this can be unified.
  */
sealed trait ItcResult extends Serializable

// === IMAGING RESULTS

final case class ImgData(singleSNRatio: Double, totalSNRatio: Double, peakPixelFlux: Double)

final case class ItcImagingResult(source: SourceDefinition, ccds: Seq[ImgData]) extends ItcResult {
  def ccd(i: Int) = ccds(i % ccds.length)
}

// === SPECTROSCOPY RESULTS

// There are two different types of charts
sealed trait SpcChartType
case object SignalChart    extends SpcChartType { val instance = this } // signal and background over wavelength [nm]
case object S2NChart       extends SpcChartType { val instance = this } // single and final S2N over wavelength [nm]

// There are four different data sets
sealed trait SpcDataType
case object SignalData     extends SpcDataType { val instance = this }  // signal over wavelength [nm]
case object BackgroundData extends SpcDataType { val instance = this }  // background over wavelength [nm]
case object SingleS2NData  extends SpcDataType { val instance = this }  // single S2N over wavelength [nm]
case object FinalS2NData   extends SpcDataType { val instance = this }  // final S2N over wavelength [nm]

/** Text data files representing a spectroscopy data set (e.g. signal or background) */
final case class SpcDataFile(dataType: SpcDataType, file: String)

/** Series of (x,y) data points used to create charts and text data files. */
final case class SpcSeriesData(dataType: SpcDataType, title: String, color: Color, data: Array[Array[Double]]) {
  def x(i: Int): Double      = xValues(i)
  def y(i: Int): Double      = yValues(i)
  def xValues: Array[Double] = data(0)
  def yValues: Array[Double] = data(1)
}

/** Charts are made up of a set of data series which are all plotted in the same XY-plot. */
final case class SpcChartData(chartType: SpcChartType, title: String, xAxisLabel: String, yAxisLabel: String, series: Seq[SpcSeriesData]) {
  // JFreeChart requires a unique name for each series
  require(series.map(_.title).distinct.size == series.size, "titles of series are not unique")

  /** Gets a data series by type and index. Note that for a given type there can be more than one result.
    * This method will fail if the result you're looking for does not exist.
    */
  def series(t: SpcDataType, i: Int = 0): SpcSeriesData     = series.filter(_.dataType == t)(i)
}

/** The result of a spectroscpy ITC calculation is a set of charts and text files.
  * Individual charts and data series can be referenced by their types and an index. For most instruments there
  * is only one chart and data series of each type, however for NIFS for example there will be several charts
  * of each type in case of multiple IFU elements. */
final case class ItcSpectroscopyResult(source: SourceDefinition, charts: Seq[SpcChartData], files: Seq[SpcDataFile]) extends ItcResult {

  /** Gets a text file for a data series by type and index.
    * This method will fail if the result you're looking for does not exist.
    */
  def file(t: SpcDataType, i: Int = 0): SpcDataFile         = files.filter(_.dataType == t)(i)

  /** Gets chart data by type and index.
    * This method will fail if the result you're looking for does not exist.
    */
  def chart(t: SpcChartType, i: Int = 0): SpcChartData      = charts.filter(_.chartType == t)(i)

  /** Gets a data series by chart type, data type and index.
    * This method will fail if the result you're looking for does not exist.
    */
  def series(ct: SpcChartType, dt: SpcDataType): SpcSeriesData = chart(ct).series(dt)
}

object ItcResult {

  import edu.gemini.itc.shared.ItcService._

  /** Creates an ITC result in case of an error. */
  def forException(e: Throwable): Result = Failure(List(e.getMessage))

  /** Creates an ITC result with a single problem/error message. */
  def forMessage(msg: String): Result = Failure(List(msg))

  /** Creates an ITC result with a list of problem/error messages. */
  def forMessages(messages: List[String]): Result = Failure(messages)

  /** Creates an ITC result for a result. */
  def forResult(result: ItcResult): Result = Success(result)

}

/**
 * Service interface for ITC calculations.
 */
trait ItcService {

  import edu.gemini.itc.shared.ItcService._

  def calculate(source: SourceDefinition, obs: ObservationDetails, cond: ObservingConditions, tele: TelescopeDetails, ins: InstrumentDetails): Result

}

object ItcService {

  type Result = Validation[List[String], ItcResult]

  /** Performs an ITC call on the given host. */
  def calculate(peer: Peer, source: SourceDefinition, obs: ObservationDetails, cond: ObservingConditions, tele: TelescopeDetails, ins: InstrumentDetails): Future[Result] =
    TrpcClient(peer).withoutKeys future { r =>
      r[ItcService].calculate(source, obs, cond, tele, ins)
    }

}