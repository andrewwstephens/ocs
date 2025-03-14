package edu.gemini.wdba.tcc;

import edu.gemini.shared.util.immutable.ImList;
import edu.gemini.shared.util.immutable.ImOption;
import edu.gemini.shared.util.immutable.Option;
import edu.gemini.spModel.target.SPSkyObject;
import edu.gemini.spModel.target.SPTarget;
import edu.gemini.spModel.target.env.GuideProbeTargets;
import edu.gemini.spModel.target.env.TargetEnvironment;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The {@link ParamSet} implementation for a group of targets.
 */
public final class TargetGroupConfig extends ParamSet {
    public static final String TYPE_VALUE="targetgroup";

    public static TargetGroupConfig createBaseGroup(final TargetEnvironment env) {
        final SPSkyObject base = env.getSlewPositionObjectFromAsterism();
        final ImList<SPSkyObject> userTargets    = env.getUserTargets().map(u -> u.target);
        final ImList<SPSkyObject> targets        = userTargets.cons(base);
        return new TargetGroupConfig(TccNames.BASE, targets, ImOption.apply(env.getSlewPositionObjectFromAsterism()));
    }

    public static TargetGroupConfig createGuideGroup(final GuideProbeTargets gt) {
        final String tag = TargetConfig.getTag(gt.getGuider());
        final ImList<SPSkyObject> targets = gt.getTargets().map(o -> (SPSkyObject)o);
        final Option<SPSkyObject> primaryOpt = gt.getPrimary().map(t -> (SPSkyObject) t);
        return new TargetGroupConfig(tag, targets, primaryOpt);
    }

    private TargetGroupConfig(final String name, final ImList<SPSkyObject> targets, final Option<SPSkyObject> primaryTarget) {
        super(name);

        addAttribute(TYPE, TYPE_VALUE);

        primaryTarget.map(SPSkyObject::getName)
                .filter(n -> !"".equals(n))
                .foreach(n -> putParameter(TccNames.PRIMARY, n));

        final List<String> targetNames = targets.toList().stream().map(SPSkyObject::getName).collect(Collectors.toList());
        putParameter(TccNames.TARGETS, targetNames);
}
}
