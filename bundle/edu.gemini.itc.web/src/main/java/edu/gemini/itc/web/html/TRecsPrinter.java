package edu.gemini.itc.web.html;

import edu.gemini.itc.base.ImagingResult;
import edu.gemini.itc.base.SpectroscopyResult;
import edu.gemini.itc.shared.*;
import edu.gemini.itc.trecs.TRecs;
import edu.gemini.itc.trecs.TRecsParameters;
import edu.gemini.itc.trecs.TRecsRecipe;
import scala.Tuple2;

import java.io.PrintWriter;
import java.util.UUID;

/**
 * Helper class for printing TRecs calculation results to an output stream.
 */
public final class TRecsPrinter extends PrinterBase {

    private final TRecsRecipe recipe;
    private final PlottingDetails pdp;
    private final boolean isImaging;

    public TRecsPrinter(final Parameters p, final TRecsParameters ip, final PlottingDetails pdp, final PrintWriter out) {
        super(out);
        this.recipe    = new TRecsRecipe(p.source(), p.observation(), p.conditions(), ip, p.telescope());
        this.pdp       = pdp;
        this.isImaging = p.observation().getMethod().isImaging();
    }

    public void writeOutput() {
        if (isImaging) {
            final ImagingResult result = recipe.calculateImaging();
            writeImagingOutput(result);
        } else {
            final Tuple2<ItcSpectroscopyResult, SpectroscopyResult> r = recipe.calculateSpectroscopy();
            final UUID id = cache(r._1());
            writeSpectroscopyOutput(id, r._2());
        }
    }

    private void writeSpectroscopyOutput(final UUID id, final SpectroscopyResult result) {

        final TRecs instrument = (TRecs) result.instrument();

        _println("");

        // This object is used to format numerical strings.
        final FormatStringWriter device = new FormatStringWriter();
        device.setPrecision(2); // Two decimal places
        device.clear();

        if (!result.observation().isAutoAperture()) {
            _println("software aperture extent along slit = " + device.toString(result.observation().getApertureDiameter()) + " arcsec");
        } else {
            switch (result.source().getProfileType()) {
                case UNIFORM:
                    _println("software aperture extent along slit = " + device.toString(1 / instrument.getFPMask()) + " arcsec");
                    break;
                case POINT:
                    _println("software aperture extent along slit = " + device.toString(1.4 * result.iqCalc().getImageQuality()) + " arcsec");
                    break;
            }
        }

        if (!result.source().isUniform()) {
            _println("fraction of source flux in aperture = " + device.toString(result.st().getSlitThroughput()));
        }

        _println("derived image size(FWHM) for a point source = " + device.toString(result.iqCalc().getImageQuality()) + "arcsec\n");

        _println("Sky subtraction aperture = "
                + result.observation().getSkyApertureDiameter()
                + " times the software aperture.");

        _println("");

        final double exp_time = result.observation().getExposureTime();
        final int number_exposures = result.observation().getNumExposures();
        final double frac_with_source = result.observation().getSourceFraction();

        _println("Requested total integration time = "
                + device.toString(exp_time * number_exposures)
                + " secs, of which "
                + device.toString(exp_time * number_exposures
                * frac_with_source) + " secs is on source.");

        _print("<HR align=left SIZE=3>");

        _println("<p style=\"page-break-inside: never\">");


        _printImageLink(id, SignalChart.instance(), pdp);
        _println("");

        _printFileLink(id,  SignalData.instance());
        _printFileLink(id,  BackgroundData.instance());

        _printImageLink(id, S2NChart.instance(), pdp);
        _println("");

        _printFileLink(id,  SingleS2NData.instance());
        _printFileLink(id,  FinalS2NData.instance());

        _println("");
        device.setPrecision(2); // TWO decimal places
        device.clear();

        _print("<HR align=left SIZE=3>");
        _println("<b>Input Parameters:</b>");
        _println("Instrument: " + instrument.getName() + "\n");
        _println(HtmlPrinter.printParameterSummary(result.source()));
        _println(trecsToString(instrument, result.parameters()));
        _println(HtmlPrinter.printParameterSummary(result.telescope()));
        _println(HtmlPrinter.printParameterSummary(result.conditions()));
        _println(HtmlPrinter.printParameterSummary(result.observation()));
        _println(HtmlPrinter.printParameterSummary(pdp));

    }

    private void writeImagingOutput(final ImagingResult result) {

        final TRecs instrument = (TRecs) result.instrument();

        _println("");

        // This object is used to format numerical strings.
        FormatStringWriter device = new FormatStringWriter();
        device.setPrecision(2); // Two decimal places
        device.clear();


        _print(CalculatablePrinter.getTextResult(result.sfCalc(), device));
        _println(CalculatablePrinter.getTextResult(result.iqCalc(), device));
        _println("Sky subtraction aperture = "
                + result.observation().getSkyApertureDiameter()
                + " times the software aperture.\n");

        _println(CalculatablePrinter.getTextResult(result.is2nCalc(), result.observation(), device));
        device.setPrecision(0); // NO decimal places
        device.clear();

        _println("");
        _println("The peak pixel signal + background is "
                + device.toString(result.peakPixelCount()) + ". ");

        if (result.peakPixelCount() > (instrument.getWellDepth()))
            _println("Warning: peak pixel may be saturating the imaging deep well setting of "
                    + instrument.getWellDepth());

        _println("");
        device.setPrecision(2); // TWO decimal places
        device.clear();

        _print("<HR align=left SIZE=3>");
        _println("<b>Input Parameters:</b>");
        _println("Instrument: " + instrument.getName() + "\n");
        _println(HtmlPrinter.printParameterSummary(result.source()));
        _println(trecsToString(instrument, result.parameters()));
        _println(HtmlPrinter.printParameterSummary(result.telescope()));
        _println(HtmlPrinter.printParameterSummary(result.conditions()));
        _println(HtmlPrinter.printParameterSummary(result.observation()));
    }

    private String trecsToString(final TRecs instrument, final Parameters p) {

        String s = "Instrument configuration: \n";
        s += HtmlPrinter.opticalComponentsToString(instrument);

        if (!instrument.getFocalPlaneMask().equals(TRecsParameters.NO_SLIT))
            s += "<LI> Focal Plane Mask: " + instrument.getFocalPlaneMask() + "\n";
        s += "\n";
        if (p.observation().getMethod().isSpectroscopy())
            s += "<L1> Central Wavelength: " + instrument.getCentralWavelength() + " nm" + "\n";
        s += "Spatial Binning: 1\n";
        if (p.observation().getMethod().isSpectroscopy())
            s += "Spectral Binning: 1\n";
        s += "Pixel Size in Spatial Direction: " + instrument.getPixelSize() + "arcsec\n";
        if (p.observation().getMethod().isSpectroscopy())
            s += "Pixel Size in Spectral Direction: " + instrument.getGratingDispersion_nmppix() + "nm\n";
        return s;
    }

}