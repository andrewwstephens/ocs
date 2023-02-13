package edu.gemini.itc.operation;

import edu.gemini.itc.base.Instrument;
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
        this.exposure_time = 1.0;  // obs.exposureTime();
        this.coadds = 1;           // obs.calculationMethod().coaddsOrElse(1);
        this.read_noise = instrument.getReadNoise();
        this.req_s2n = ((ImagingExp) obs.calculationMethod()).sigma();
    }

    public void calculate() {

        Log.fine("Calculating with initial guess for exptime...");
        super.calculate();

        Log.fine("singleSNRatio = " + singleSNRatio());

        // final double req_source_exposures = (req_s2n / signal) * (req_s2n / signal) *
        //        (signal + noiseFactor * sourceless_noise * sourceless_noise) / coadds;




        Log.fine("Estimated exposure time = " + req_source_exposures);

        int_req_source_exposures =
                new Double(Math.ceil(req_source_exposures)).intValue();

        req_number_exposures =
                int_req_source_exposures / frac_with_source;

    }

    @Override public double numberSourceExposures() {
        return int_req_source_exposures;
    }

    public double reqNumberExposures() {
        return req_number_exposures;
    }

}
