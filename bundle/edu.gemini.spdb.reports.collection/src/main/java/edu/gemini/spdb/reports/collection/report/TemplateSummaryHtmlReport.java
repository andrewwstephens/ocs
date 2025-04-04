package edu.gemini.spdb.reports.collection.report;

import edu.gemini.spModel.core.SPProgramID;
import edu.gemini.spModel.core.Semester;
import edu.gemini.spdb.reports.IColumn;
import edu.gemini.spdb.reports.IRow;
import edu.gemini.spdb.reports.ISort;
import edu.gemini.spdb.reports.collection.table.TemplateSummaryTable.Columns;
import edu.gemini.spdb.reports.collection.util.ReportUtils;
import edu.gemini.spdb.reports.util.SimpleSort;

import java.util.Optional;

public class TemplateSummaryHtmlReport extends
		AbstractTemplateSummaryReport {

	private static final ISort[] GROUPS = new ISort[] {
//		new SimpleSort(Columns.DATE, ISort.Order.DESC),
	};

    private static final IColumn[] COLUMNS = new IColumn[]{
            Columns.PROGRAM_ID,
            Columns.BAND,
            Columns.PROG_TOO_STATUS,
            Columns.TEMPLATE_ID,
            Columns.TARGET_NAME,
            Columns.RA,
            Columns.DEC,
            Columns.COND_CONSTRAINTS,
            Columns.INST_CONFIG,
            Columns.PHASE1_TIME,
    };


    @Override
	protected String getTemplateName() {
		return "TemplateSummaryHtmlReport.vm";
	}

	@Override
	protected String getFileExtension() {
		return "html";
	}

	@Override
	protected ISort[] getGroups() {
		return GROUPS;
	}

	@Override
	protected IColumn[] getOutputColumns() {
		return COLUMNS;
	}

	@Override
	protected Optional<Semester> getSemester(IRow row) {
		return ReportUtils.getSemester((SPProgramID)row.getValue(0));
	}

}
