package jsky.image;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;

/**
 * Defines a number of standard  color intensity tables.
 * The data was originally taken from ESO Midas files.
 *
 * @version $Revision: 4414 $
 * @author Allan Brighton
 */

public class ImageColorITTs {

    /** The default itt (the rest are loaded as resources). */
    private static float[] ramp = {
        0.00000f,
        0.00392f,
        0.00784f,
        0.01176f,
        0.01569f,
        0.01961f,
        0.02353f,
        0.02745f,
        0.03137f,
        0.03529f,
        0.03922f,
        0.04314f,
        0.04706f,
        0.05098f,
        0.05490f,
        0.05882f,
        0.06275f,
        0.06667f,
        0.07059f,
        0.07451f,
        0.07843f,
        0.08235f,
        0.08627f,
        0.09020f,
        0.09412f,
        0.09804f,
        0.10196f,
        0.10588f,
        0.10980f,
        0.11373f,
        0.11765f,
        0.12157f,
        0.12549f,
        0.12941f,
        0.13333f,
        0.13725f,
        0.14118f,
        0.14510f,
        0.14902f,
        0.15294f,
        0.15686f,
        0.16078f,
        0.16471f,
        0.16863f,
        0.17255f,
        0.17647f,
        0.18039f,
        0.18431f,
        0.18824f,
        0.19216f,
        0.19608f,
        0.20000f,
        0.20392f,
        0.20784f,
        0.21176f,
        0.21569f,
        0.21961f,
        0.22353f,
        0.22745f,
        0.23137f,
        0.23529f,
        0.23922f,
        0.24314f,
        0.24706f,
        0.25098f,
        0.25490f,
        0.25882f,
        0.26275f,
        0.26667f,
        0.27059f,
        0.27451f,
        0.27843f,
        0.28235f,
        0.28627f,
        0.29020f,
        0.29412f,
        0.29804f,
        0.30196f,
        0.30588f,
        0.30980f,
        0.31373f,
        0.31765f,
        0.32157f,
        0.32549f,
        0.32941f,
        0.33333f,
        0.33725f,
        0.34118f,
        0.34510f,
        0.34902f,
        0.35294f,
        0.35686f,
        0.36078f,
        0.36471f,
        0.36863f,
        0.37255f,
        0.37647f,
        0.38039f,
        0.38431f,
        0.38824f,
        0.39216f,
        0.39608f,
        0.40000f,
        0.40392f,
        0.40784f,
        0.41176f,
        0.41569f,
        0.41961f,
        0.42353f,
        0.42745f,
        0.43137f,
        0.43529f,
        0.43922f,
        0.44314f,
        0.44706f,
        0.45098f,
        0.45490f,
        0.45882f,
        0.46275f,
        0.46667f,
        0.47059f,
        0.47451f,
        0.47843f,
        0.48235f,
        0.48627f,
        0.49020f,
        0.49412f,
        0.49804f,
        0.50196f,
        0.50588f,
        0.50980f,
        0.51373f,
        0.51765f,
        0.52157f,
        0.52549f,
        0.52941f,
        0.53333f,
        0.53725f,
        0.54118f,
        0.54510f,
        0.54902f,
        0.55294f,
        0.55686f,
        0.56078f,
        0.56471f,
        0.56863f,
        0.57255f,
        0.57647f,
        0.58039f,
        0.58431f,
        0.58824f,
        0.59608f,
        0.60000f,
        0.59608f,
        0.60392f,
        0.60784f,
        0.61176f,
        0.61569f,
        0.61961f,
        0.62353f,
        0.62745f,
        0.63137f,
        0.63529f,
        0.63922f,
        0.64314f,
        0.64706f,
        0.65098f,
        0.65490f,
        0.65882f,
        0.66275f,
        0.66667f,
        0.67059f,
        0.67451f,
        0.67843f,
        0.68235f,
        0.68627f,
        0.69020f,
        0.69412f,
        0.69804f,
        0.70196f,
        0.70588f,
        0.70980f,
        0.71373f,
        0.71765f,
        0.72157f,
        0.72549f,
        0.72941f,
        0.73333f,
        0.73725f,
        0.74118f,
        0.74510f,
        0.74902f,
        0.75294f,
        0.75686f,
        0.76078f,
        0.76471f,
        0.76863f,
        0.77255f,
        0.77647f,
        0.78039f,
        0.78431f,
        0.78824f,
        0.79216f,
        0.79608f,
        0.80000f,
        0.80392f,
        0.80784f,
        0.81176f,
        0.81569f,
        0.81961f,
        0.82353f,
        0.82745f,
        0.83137f,
        0.83529f,
        0.83922f,
        0.84314f,
        0.84706f,
        0.85098f,
        0.85490f,
        0.85882f,
        0.86275f,
        0.86667f,
        0.87059f,
        0.87451f,
        0.87843f,
        0.88235f,
        0.88627f,
        0.89020f,
        0.89412f,
        0.89804f,
        0.90196f,
        0.90588f,
        0.90980f,
        0.91373f,
        0.91765f,
        0.92157f,
        0.92549f,
        0.92941f,
        0.93333f,
        0.93725f,
        0.94118f,
        0.94510f,
        0.94902f,
        0.95294f,
        0.95686f,
        0.96078f,
        0.96471f,
        0.96863f,
        0.97255f,
        0.97647f,
        0.98039f,
        0.98431f,
        0.98824f,
        0.99216f,
        0.99608f,
        1.00000f,
    };


    /** The names of the ITTs */
    private static String[] ittNames = {
        "Equal",
        "Exponential",
        "Gamma",
        "Jigsaw",
        "Lasritt",
        "Log",
        "Negative",
        "NegativeLog",
        "Null",
        "Ramp",
        "Stairs"
    };

    /** Holds the lookup table data. */
    private static float[][] itts = new float[ittNames.length][];

    /** return the numbr of intensity tables */
    public static int getNumITTs() {
        return ittNames.length;
    }

    /** return the name of the ith intensity table */
    public static String getITTName(int i) {
        return ittNames[i];
    }

    /** return an array of intensity table names */
    public static String[] getITTNames() {
        return ittNames;
    }

    /** return the named lookup table */
    static float[] getITT(String name) {
        if (name.equals("Ramp"))
            return ramp;
        for (int i = 0; i < ittNames.length; i++) {
            if (name.equals(ittNames[i])) {
                return getITT(i);
            }
        }
        throw new RuntimeException("Unknown ITT color lookup table: " + name);
    }

    /** Return the ith intensity table */
    private static float[] getITT(int i) {
        if (itts[i] == null) {
            try {
                itts[i] = loadITT(ittNames[i]);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return itts[i];
    }

    /** Load the named intensity table from a resource file (colormaps/<name>.iasc). */
    private static float[] loadITT(String name) throws IOException {
        String filename = "colormaps/" + name + ".iasc";
        BufferedReader in;
        try {
            // NB This may not work properly launching from IDEA
            in = new BufferedReader(new InputStreamReader(ImageColorITTs.class.getClassLoader().getResourceAsStream(filename)));
        } catch (Exception e) {
            throw new RuntimeException("Can't open colormap file: " + filename);
        }
        StreamTokenizer st = new StreamTokenizer(in);
        st.parseNumbers();
        float[] itt = new float[256];
        int tok;
        while ((tok = st.nextToken()) != StreamTokenizer.TT_EOF) {
            if (tok != StreamTokenizer.TT_NUMBER) {
                in.close();
                throw new RuntimeException("Error in colormap file: " + filename + " at line " + st.lineno());
            }
            itt[st.lineno() - 1] = (float) st.nval;
        }
        in.close();
        return itt;
    }
}

