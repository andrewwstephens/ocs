package edu.gemini.itc.operation;

import edu.gemini.itc.base.DefaultArraySpectrum;
import edu.gemini.itc.base.ITCConstants;
import edu.gemini.itc.base.TransmissionElement;
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality;

/**
 * The CloudTransmissionVisitor is designed to adjust the SED for
 * clouds in the atmosphere.
 */
public final class CloudTransmissionVisitor {
    private static final String FILENAME = "cloud_trans";
    private CloudTransmissionVisitor() {
    }

    /**
     * Constructs transmission visitor for clouds.
     */
    public static TransmissionElement create(final SPSiteQuality.CloudCover cc, double exactcc) {

        if (cc == SPSiteQuality.CloudCover.EXACT) {

            if (exactcc < 0.0) throw new IllegalArgumentException("Exact Cloud Cover must be >= zero.");
            System.out.println("Using EXACT Cloud Cover = " + exactcc);

            final double[][] data = new double[2][2];
            data[0][0] = 100.0; // x = wavelength; should this be the real wavelength range?
            data[0][1] = 30000.0;
            data[1][0] = Math.pow(10, exactcc/-2.5); // y = transmission
            data[1][1] = data[1][0];
            final TransmissionElement te;
            te = new TransmissionElement(new DefaultArraySpectrum(data));
            System.out.println("Transmission = " + data[1][0]);
            return te;

        } else {

            return new TransmissionElement(ITCConstants.TRANSMISSION_LIB + "/" + FILENAME +
                    "_" + cc.sequenceValue() + ITCConstants.DATA_SUFFIX);
        }
    }
}
