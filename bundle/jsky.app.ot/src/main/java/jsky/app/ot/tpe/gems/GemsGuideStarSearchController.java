package jsky.app.ot.tpe.gems;

import edu.gemini.ags.gems.GemsUtils4Java;
import edu.gemini.skycalc.Angle;
import edu.gemini.shared.skyobject.Magnitude;
import edu.gemini.shared.skyobject.SkyObject;
import edu.gemini.shared.util.immutable.Some;
import edu.gemini.spModel.core.MagnitudeBand;
import edu.gemini.spModel.core.Target;
import edu.gemini.spModel.gems.GemsTipTiltMode;
import edu.gemini.ags.gems.GemsCatalogSearchResults;
import edu.gemini.ags.gems.GemsGuideStarSearchOptions;
import edu.gemini.ags.gems.GemsGuideStars;
import edu.gemini.spModel.obs.context.ObsContext;
import jsky.app.ot.tpe.GemsGuideStarWorker;
import jsky.app.ot.tpe.TpeImageWidget;
import jsky.app.ot.tpe.TpeManager;
import jsky.coords.WorldCoords;
import jsky.util.gui.DialogUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OT-111: Controller for GemsGuideStarSearchDialog
 */
class GemsGuideStarSearchController {
    private GemsGuideStarSearchModel _model;
    private GemsGuideStarWorker _worker;
    private GemsGuideStarSearchDialog _dialog;

    /**
     * Constructor
     * @param model the overall GUI model
     * @param worker does the background tasks like query, analyze
     * @param dialog the main dialog
     */
    public GemsGuideStarSearchController(GemsGuideStarSearchModel model, GemsGuideStarWorker worker,
                                         GemsGuideStarSearchDialog dialog) {
        _model = model;
        _worker = worker;
        _dialog = dialog;
    }

    /**
     * Searches for guide star candidates and saves the results in the model
     */
    public void query() throws Exception {
        TpeImageWidget tpe = TpeManager.create().getImageWidget();
        WorldCoords basePos = tpe.getBasePos();
        ObsContext obsContext = _worker.getObsContext(basePos.getRaDeg(), basePos.getDecDeg());
        Set<Angle> posAngles = getPosAngles(obsContext);

        MagnitudeBand band = _model.getBand().getBand();
        final String catName;
        if (_model.getCatalog() == GemsGuideStarSearchOptions.CatalogChoice.USER_CATALOG) {
            catName = _model.getUserCatalogFileName();
        } else {
            catName = _model.getCatalog().catalogName();
        }
        GemsTipTiltMode tipTiltMode = _model.getAnalyseChoice().getGemsTipTiltMode();
        List<GemsCatalogSearchResults> results;
        try {
            results = _worker.search(catName, catName, tipTiltMode, obsContext, posAngles,
                    new Some<MagnitudeBand>(band));
        } catch(Exception e) {
            DialogUtil.error(_dialog, e);
            results = new ArrayList<GemsCatalogSearchResults>();
            _dialog.setState(GemsGuideStarSearchDialog.State.PRE_QUERY);
        }

        if (_model.isReviewCanditatesBeforeSearch()) {
            _model.setGemsCatalogSearchResults(filterQueryResults(obsContext, posAngles, results));
        } else {
            _model.setGemsCatalogSearchResults(results);
            _model.setGemsGuideStars(_worker.findAllGuideStars(obsContext, posAngles, results));
        }
    }

    // Returns a copy of results with any query results removed that can not actually be used in the
    // given context, with the given position angles and modes.
    // (This is done again later in the call to analyze() , but can be called here to filter the list
    // of candidate stars that the user sees, if that option is on.)
    private List<GemsCatalogSearchResults> filterQueryResults(final ObsContext obsContext, final Set<Angle> posAngles,
                                                  List<GemsCatalogSearchResults> results) {

        // XXX TODO
        return results;
    }

    private Set<Angle> getPosAngles(ObsContext obsContext) {
        Set<Angle> posAngles = new HashSet<Angle>();
        posAngles.add(obsContext.getPositionAngle());
        if (_model.isAllowPosAngleAdjustments()) {
            posAngles.add(new Angle(0., Angle.Unit.DEGREES));
            posAngles.add(new Angle(90., Angle.Unit.DEGREES));
            posAngles.add(new Angle(180., Angle.Unit.DEGREES));
            posAngles.add(new Angle(270., Angle.Unit.DEGREES));
        }
        return posAngles;
    }

    /**
     * Analyzes the search results and saves a list of possible guide star configurations to the model.
     * @param excludeCandidates list of SkyObjects to exclude
     */
    // Called from the TPE
    public void analyze(List<Target.SiderealTarget> excludeCandidates) throws Exception {
        TpeImageWidget tpe = TpeManager.create().getImageWidget();
        WorldCoords basePos = tpe.getBasePos();
        ObsContext obsContext = _worker.getObsContext(basePos.getRaDeg(), basePos.getDecDeg());
        Set<Angle> posAngles = getPosAngles(obsContext);
        _model.setGemsGuideStars(_worker.findAllGuideStars(obsContext, posAngles,
                filter(excludeCandidates, _model.getGemsCatalogSearchResults())));
    }

    // Returns a list of the given gemsCatalogSearchResults, with any SkyObjects removed that are not
    // in the candidates list.
    private List<GemsCatalogSearchResults> filter(List<Target.SiderealTarget> excludeCandidates,
                                                  List<GemsCatalogSearchResults> gemsCatalogSearchResults) {
        List<GemsCatalogSearchResults> results = new ArrayList<>();
        for (GemsCatalogSearchResults in : gemsCatalogSearchResults) {
            List<Target.SiderealTarget> siderealTargets = new ArrayList<>(in.results().size());
            siderealTargets.addAll(in.resultsAsJava());
            siderealTargets = removeAll(siderealTargets, excludeCandidates);
            if (!siderealTargets.isEmpty()) {
                GemsCatalogSearchResults out = new GemsCatalogSearchResults(siderealTargets, in.criterion());
                results.add(out);
            }
        }
        return results;
    }

    // Removes all the objects in the skyObjects list that are also in the excludeCandidates list by comparing names
    private List<Target.SiderealTarget> removeAll(List<Target.SiderealTarget> skyObjects, List<Target.SiderealTarget> excludeCandidates) {
        List<Target.SiderealTarget> result = new ArrayList<>();
        for(Target.SiderealTarget siderealTarget : skyObjects) {
            if (!contains(excludeCandidates, siderealTarget)) {
                result.add(siderealTarget);
            }
        }
        return result;
    }

    // Returns true if a SkyObject with the same name is in the list
    private boolean contains(List<Target.SiderealTarget> targets, Target.SiderealTarget target) {
        String name = target.name();
        if (name != null) {
            for (Target.SiderealTarget s : targets) {
                if (name.equals(s.name())) return true;
            }
        }
        return false;
    }

    /**
     * Adds the given asterisms as guide groups to the current observation's target list.
     * @param selectedAsterisms information used to create the guide groups
     * @param primaryIndex if more than one item in list, the index of the primary guide group
     */
    public void add(List<GemsGuideStars> selectedAsterisms, int primaryIndex) {
        _worker.applyResults(selectedAsterisms, primaryIndex);
    }
}
