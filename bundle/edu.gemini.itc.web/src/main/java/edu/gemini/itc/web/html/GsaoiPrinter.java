package edu.gemini.itc.web.html;

import edu.gemini.itc.base.ImagingResult;
import edu.gemini.itc.gems.Gems;
import edu.gemini.itc.gsaoi.Camera;
import edu.gemini.itc.gsaoi.Gsaoi;
import edu.gemini.itc.gsaoi.GsaoiRecipe;
import edu.gemini.itc.shared.GsaoiParameters;
import edu.gemini.itc.shared.ItcWarning;
import edu.gemini.itc.shared.Parameters;
import scala.collection.JavaConversions;

import java.io.PrintWriter;

/**
 * Helper class for printing GSAOI calculation results to an output stream.
 */
public final class GsaoiPrinter extends PrinterBase {

    private final GsaoiRecipe recipe;

    public GsaoiPrinter(final Parameters p, final GsaoiParameters ip, final PrintWriter out) {
        super(out);
        recipe = new GsaoiRecipe(p.source(), p.observation(), p.conditions(), ip, p.telescope());
    }

    /**
     * Performes recipe calculation and writes results to a cached PrintWriter or to System.out.
     */
    public void writeOutput() {
        final ImagingResult result = recipe.calculateImaging();
        writeImagingOutput(result);
    }

    private void writeImagingOutput(final ImagingResult result) {

        final Gsaoi instrument = (Gsaoi) result.instrument();

        _println("");

        final FormatStringWriter device = new FormatStringWriter();
        device.setPrecision(2); // Two decimal places
        device.clear();

        _println((HtmlPrinter.printSummary((Gems) result.aoSystem().get())));

        _print(CalculatablePrinter.getTextResult(result.sfCalc(), device, false));
        _println("derived image halo size (FWHM) for a point source = "
                + device.toString(result.iqCalc().getImageQuality()) + " arcsec.\n");
        _println(CalculatablePrinter.getTextResult(result.is2nCalc(), result.observation(), device));
        _println(CalculatablePrinter.getBackgroundLimitResult(result.is2nCalc()));
        device.setPrecision(0); // NO decimal places
        device.clear();

        _println("");
        _println("The peak pixel signal + background is " + device.toString(result.peakPixelCount()));

        // REL-1353
        final int peak_pixel_percent = (int) (100 * result.peakPixelCount() / Gsaoi.WELL_DEPTH);
        _println("This is " + peak_pixel_percent + "% of the full well depth of " + Gsaoi.WELL_DEPTH + " electrons");
        for (final ItcWarning warning : JavaConversions.asJavaList(result.warnings())) {
            _println(warning.msg());
        }

        _println("");
        device.setPrecision(2); // TWO decimal places
        device.clear();

        _print("<HR align=left SIZE=3>");

        _println("<b>Input Parameters:</b>");
        _println("Instrument: " + instrument.getName() + "\n");
        _println(HtmlPrinter.printParameterSummary(result.source()));
        _println(gsaoiToString(instrument));
        _println(printTeleParametersSummary(result));
        _println(HtmlPrinter.printParameterSummary((Gems) result.aoSystem().get()));
        _println(HtmlPrinter.printParameterSummary(result.conditions()));
        _println(HtmlPrinter.printParameterSummary(result.observation()));

    }

    private String printTeleParametersSummary(final ImagingResult result) {
        StringBuffer sb = new StringBuffer();
        sb.append("Telescope configuration: \n");
        sb.append("<LI>" + result.telescope().getMirrorCoating().displayValue() + " mirror coating.\n");
        sb.append("<LI>wavefront sensor: gems\n");
        return sb.toString();
    }

    private String gsaoiToString(final Gsaoi instrument) {
        String s = "Instrument configuration: \n";
        s += "Optical Components: <BR>";
        for (Object o : instrument.getComponents()) {
            if (!(o instanceof Camera)) {
                s += "<LI>" + o.toString() + "<BR>";
            }
        }
        s += "<BR>";
        s += "Pixel Size: " + instrument.getPixelSize() + "<BR>";

        return s;
    }

}
