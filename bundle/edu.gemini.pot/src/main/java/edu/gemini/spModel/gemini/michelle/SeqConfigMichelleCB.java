package edu.gemini.spModel.gemini.michelle;

import edu.gemini.pot.sp.ISPSeqComponent;

import edu.gemini.spModel.obscomp.InstConstants;
import edu.gemini.spModel.seqcomp.SeqConfigNames;
import edu.gemini.spModel.config.HelperSeqCompCB;
import edu.gemini.spModel.data.config.StringParameter;
import edu.gemini.spModel.data.config.IConfig;

import java.util.Map;

/**
 * A configuration builder for the Michelle iterator.
 */
public final class SeqConfigMichelleCB extends HelperSeqCompCB {

    /**
     * Constructor for creating this seq comp CB.
     */
    public SeqConfigMichelleCB(ISPSeqComponent seqComp) {
        super(seqComp);
    }

    public Object clone() {
        return super.clone();
    }

    /**
     * This thisApplyNext overrides the HelperSeqCompCB
     * so that the integration time, exposure time and ncoadds can
     * be inserting in the observe system.
     */
    protected void thisApplyNext(IConfig config, IConfig fullPrev) {
        super.thisApplyNext(config, fullPrev);

        config.putParameter(SeqConfigNames.INSTRUMENT_CONFIG_NAME,
                            StringParameter.getInstance(InstConstants.INSTRUMENT_NAME_PROP,
                                                        InstMichelle.INSTRUMENT_NAME_PROP));

        InstMichelle.WAVELENGTH_INJECTOR.inject(config, fullPrev);
    }


    public void thisReset(Map<String, Object> options) {
        super.thisReset(options);
    }
}
