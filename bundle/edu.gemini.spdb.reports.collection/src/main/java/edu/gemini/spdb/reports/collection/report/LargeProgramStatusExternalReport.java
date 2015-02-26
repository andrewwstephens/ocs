package edu.gemini.spdb.reports.collection.report;

import edu.gemini.spModel.core.ProgramType;
import edu.gemini.spdb.reports.IFilter;

public final class LargeProgramStatusExternalReport extends AbstractQueueProgramStatusExternalReport {

    public LargeProgramStatusExternalReport() {
        super("LargeProgramStatusExternalReport.vm");
    }

    @Override
    protected IFilter getFilter() {
        return new ProgramTypeFilter(ProgramType.LargeProgram$.MODULE$);
    }

    String getFileName(final String site, final String semester) {
        return "largeProgSchedQueue_" + site + "_" + semester + ".html";
    }
}
