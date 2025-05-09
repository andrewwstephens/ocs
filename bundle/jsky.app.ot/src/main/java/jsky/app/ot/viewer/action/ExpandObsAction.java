package jsky.app.ot.viewer.action;

import edu.gemini.pot.sp.ISPObservation;
import jsky.app.ot.viewer.SPViewer;
import jsky.util.gui.BusyWin;
import jsky.util.gui.DialogUtil;

import java.awt.event.ActionEvent;

/**
* Created with IntelliJ IDEA.
* User: rnorris
* Date: 1/17/13
* Time: 2:29 PM
* To change this template use File | Settings | File Templates.
*/
public final class ExpandObsAction extends AbstractViewerAction {

    public ExpandObsAction(SPViewer viewer) {
        super(viewer, "Expand Current Observation");
    }

    public void actionPerformed(ActionEvent evt) {
        try {
            ISPObservation obs = getContextNode(ISPObservation.class);
            if (obs != null) {
                BusyWin.showBusy();
                viewer.getTree().expandAll(obs);
            }
        } catch (Exception e) {
            DialogUtil.error(e);
        }
    }

    @Override
    public boolean computeEnabledState() {
        return getContextNode(ISPObservation.class) != null;
    }
}
