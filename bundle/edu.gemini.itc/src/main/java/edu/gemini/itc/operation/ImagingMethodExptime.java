package edu.gemini.itc.operation;

import edu.gemini.itc.base.Instrument;
import edu.gemini.itc.operation.PeakPixelFlux;
import edu.gemini.itc.shared.ImagingExp;
import edu.gemini.itc.shared.ObservationDetails;
import java.util.logging.Logger;

public final class ImagingMethodExptime extends ImagingS2NCalculation {

    private final double frac_with_source;
    private final double req_s2n;
    private int int_req_source_exposures;
    private double req_number_exposures;
    private double req_exptime;
    private static final Logger Log = Logger.getLogger(ImagingMethodExptime.class.getName());

    public ImagingMethodExptime(final ObservationDetails obs,
                                final Instrument instrument,
                                final SourceFraction srcFrac,
                                final double sed_integral,
                                final double sky_integral) {
        super(obs, instrument, srcFrac, sed_integral, sky_integral);
        this.frac_with_source = obs.sourceFraction();
        this.exposure_time = obs.exposureTime();  // defined to be 60s in ITCgmos.html
        this.coadds = 1;   // obs.calculationMethod().coaddsOrElse(1);
        this.read_noise = instrument.getReadNoise();
        this.pixel_size = instrument.getPixelSize();
        this.req_s2n = ((ImagingExp) obs.calculationMethod()).sigma();
    }

    public void calculate() {

        // ? Could we make it so that this calculation is ONLY done for the middle CCD ?

        Log.fine("Calculating with initial guess for exptime...");
        super.calculate();

        Log.fine("singleSNRatio = " + singleSNRatio());

        // Calculate the exposure time that should give the desired S/N

        // In general, S/N ~ sqrt(t), so t ~ (S/N)^2
        final double req_exptime = exposure_time * (req_s2n / singleSNRatio()) * (req_s2n / singleSNRatio());
        Log.fine("Estimated exposure time = " + req_exptime);
        // well, that didn't work very well...

        Log.fine("frac_with_source = " + frac_with_source);  // fraction of images which include the source
        Log.fine("source_fraction = " + source_fraction);  // fraction of source in aperture?

        // Try again:
        final double vs = sed_integral * source_fraction + secondary_integral * secondary_source_fraction;
        final double vb = sky_integral * pixel_size * pixel_size * Npix;
        final double vd = dark_current * Npix;
        final double a = vs * vs;
        final double b = -1.0 * req_s2n * req_s2n * (vs + vb + vd);
        final double c = req_s2n * req_s2n * var_readout;
        final double discriminant = b*b - 4.0 * a * c;
        final double new_exptime_plus = (-1.0 * b + Math.sqrt(b*b - 4.0 * a * c)) / (2.0 * a);
        final double new_exptime_minus = (-1.0 * b - Math.sqrt(b*b - 4.0 * a * c)) / (2.0 * a);
        Log.fine("sed = " + sed_integral);
        Log.fine("sky = " + sky_integral);
        Log.fine("pixsize = " + pixel_size);
        Log.fine("readnoise = " + read_noise);
        Log.fine("Npix = " + Npix);
        Log.fine("snr = " + req_s2n);
        Log.fine("vs = " + vs);
        Log.fine("vb = " + vb);
        Log.fine("vd = " + vd);
        Log.fine("vr = " + var_readout);
        Log.fine("a = " + a);
        Log.fine("b = " + b);
        Log.fine("c = " + c);
        Log.fine("discriminant = " + discriminant);
        Log.fine("Calculated exposure time (+) = " + new_exptime_plus);
        Log.fine("Calculated exposure time (-) = " + new_exptime_minus);
        if (discriminant > 0) {
            exposure_time = new_exptime_plus;
        } else {
            exposure_time = 1.0;
        }


        final double peak_flux = PeakPixelFlux.calculate(pixel_size, dark_current, _sdParameters, exposure_time, source_fraction, Npix, im_qual, sed_integral, sky_integral);
        Log.fine("Peak Pixel Flux = " + peak_flux);


        // final double req_source_exposures = (req_s2n / signal) * (req_s2n / signal) *
        //        (signal + noiseFactor * sourceless_noise * sourceless_noise) / coadds;

        // Calculate the exposure time that will give the max allowed counts (half-well?)

        // Take the smaller of the two?

        // The minimum exposure time is 1s for GMOS  (can I query the minimum for each instrument?)

        // For instruments that can coadd - calculate the number of coadds required:
        // numcoadds =
        // if we need 90s
        // and the longest allowed is 60s
        // 90/60 = 1.5 -> take 2
        // 90/2 = 45s each

        Log.fine(String.format("Calculating S/N with Exptime = %.3f", exposure_time));
        super.calculate();
        Log.fine("singleSNRatio = " + singleSNRatio());

    }

    @Override public double numberSourceExposures() {
        return 1;
    }

    public double exposureTime() {
        return exposure_time;
    }

}