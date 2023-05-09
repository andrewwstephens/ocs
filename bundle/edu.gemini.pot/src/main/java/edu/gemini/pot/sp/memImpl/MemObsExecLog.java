package edu.gemini.pot.sp.memImpl;

import edu.gemini.pot.sp.*;
import edu.gemini.shared.util.immutable.ImOption;
import edu.gemini.shared.util.immutable.Option;
import edu.gemini.spModel.event.ObsExecEvent;
import edu.gemini.spModel.obslog.ObsExecLog;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.gemini.pot.sp.SPComponentType.OBS_EXEC_LOG;

public final class MemObsExecLog extends MemProgramNodeBase implements ISPObsExecLog {
    private static final Logger LOG = Logger.getLogger(MemObsExecLog.class.getName());

    private final MemProgram program;

    MemObsExecLog(MemProgram prog, SPNodeKey key) {
        super(prog.getDocumentData(), key);
        program = prog;
    }

    MemObsExecLog(MemProgram prog, ISPObsExecLog log, boolean preserveKeys) {
        super(prog.getDocumentData(), log, preserveKeys);
        program = prog;
    }

    @Override public SPComponentType getType() { return OBS_EXEC_LOG; }

    @Override public void accept(ISPProgramVisitor visitor) {
        visitor.visitObsExecLog(this);
    }

    @Override public MemProgram getProgram() { return program; }

    // Start REL-3986 ------ logging to debug an issue with missing events
    private static List<ObsExecEvent> getVisitEvents(String name, Object obj) {
        return (DATA_OBJECT_KEY.equals(name) && (obj instanceof ObsExecLog)) ?
                ((ObsExecLog) obj).getVisitEvents()                          :
                Collections.emptyList();
    }

    private Option<String> getObsId() {
        return getContextObservationId().map(SPObservationID::stringValue);
    }

    @Override public PropagationId putClientData(String name, Object obj) {
        getProgramWriteLock();

        final Level logLevel = Level.FINE;

        try {
            final Option<String>        oid = getObsId();
            final StringBuilder         buf = new StringBuilder();
            final List<ObsExecEvent> before = getVisitEvents(name, getDataObject());
            final List<ObsExecEvent>  after = getVisitEvents(name, obj);
            final boolean         logEvents = LOG.isLoggable(logLevel) && !before.equals(after);

            if (logEvents) {
                buf.append(String.format("Updating ObsExecRecord for %s (%s)\n", oid.getOrElse("<unknown>"), getDocumentData().getLifespanId()));
                buf.append(String.format("Before....: %s\n", getDocumentData().versionVector(this.getNodeKey())));
                buf.append(ObsExecLog.formatEvents(before));
            }

            final PropagationId propid = super.putClientData(name, obj);

            if (logEvents) {
                buf.append(String.format("After.....: %s\n", getDocumentData().versionVector(this.getNodeKey())));
                buf.append(ObsExecLog.formatEvents(after));

                LOG.log(logLevel, buf.toString(), new Throwable());
            }

            return propid;
        } finally {
            returnProgramWriteLock();
        }
    }

    // End REL-3986

}
