// Copyright 2002
// Association for Universities for Research in Astronomy, Inc.
// Observatory Control System, Gemini Telescopes Project.
// See the file LICENSE for complete details.
//
// $Id: SessionManagement.java,v 1.1.1.1 2007/01/08 18:01:25 gillies Exp $
//
package edu.gemini.wdba.session;

import edu.gemini.spModel.core.SPBadIDException;
import edu.gemini.pot.sp.SPObservationID;
import edu.gemini.spModel.event.ExecEvent;
import edu.gemini.wdba.glue.api.WdbaContext;
import edu.gemini.wdba.xmlrpc.ServiceException;
import edu.gemini.wdba.shared.QueuedObservation;
import edu.gemini.wdba.shared.Helpers;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Implementation of the OCS Session functionality.
 * An implementation of the ISessionXmlRpc interface.
 *
 * @author K.Gillies
 */
public final class SessionManagement {
    private static final Logger LOG = Logger.getLogger(SessionManagement.class.getName());

    // VERSION for this glue
    private static final String _SESSION_PREFIX = "session-";

    private int _sessionCount = 0;
    private final WdbaContext _ctx;
    private final Map<String, Session> _sessions = new HashMap<>();
    private final Consumer<ExecEvent> _eventConsumer;

    /**
     * Returns the <code>SessionManagement</code> object.
     *
     * @return The singleton SessionManagement object.
     */
    public SessionManagement(WdbaContext ctx, Consumer<ExecEvent> eventConsumer) {
        _ctx           = ctx;
        _eventConsumer = eventConsumer;
    }

    /**
     * Factory to create a new session.
     *
     * @return the id of the new session, which must be used in the other
     *         methods.
     */
    public synchronized String createSession() {
        return createSession(_SESSION_PREFIX + _sessionCount++);
    }

    /**
     * Factory to create a new session with a given name.
     *
     * @param sessionId the name that should be used to create the session.  The name should be unique.
     * @return the id of the new session, which must be used in the other
     *         methods.
     */
    public synchronized String createSession(String sessionId) {
        return lookupOrCreateSession(sessionId).getSessionID();
    }

    /**
     * Remove a session and all its contents.
     *
     * @param sessionId is the id of the session to be used to remove.
     * @return true if the operation is successful, else false
     *         methods.
     */
    public boolean removeSession(String sessionId) {
        if (sessionId == null) throw new NullPointerException();
        // First try to look it up and return if it doesn't exist

        Session session;
        synchronized (_sessions) {
            session = _sessions.get(sessionId);
            // Session isn't there so return success
            if (session == null) return true;

            session = _sessions.remove(sessionId);
        }
        return session != null;
    }

    /**
     * Remove all sessions and contents.
     */
    public void removeAllSessions() {
        _sessions.clear();
        // Note that this means the next new session will be 0 again - useful for testing
        _sessionCount = 0;
    }

    /**
     * Returns the number of sessions.
     *
     * @return the number of sessions currently being handled by the SessionManager.
     */
    public int getSessionCount() {
        return _sessions.size();
    }

    /**
     * Private method to look up the Session by sessionId.
     */
    private synchronized Session lookupOrCreateSession(String sessionId) {
        // First try to look it up
        Session session = _sessions.get(sessionId);
        if (session == null) {
            session = new Session(sessionId, _ctx.getWdbaDatabaseAccessService(), _eventConsumer);
            _sessions.put(sessionId, session);
        }
        // Else, it is the correct session
        return session;
    }

    /**
     * Add a specific observation id to a session
     *
     * @param sessionId     is the id of the session to be used to add the observation
     * @param observationId is the observation to add
     * @return true if the operation is successful, else false
     */
    public boolean addObservation(String sessionId, String observationId) {
        if (sessionId == null) throw new NullPointerException();
        Session session = lookupOrCreateSession(sessionId);
        return session.addObservation(observationId);
    }

    /**
     * Remove a specific observation id from a session
     *
     * @param sessionId     is the id of the session to be used to remove the observation
     * @param observationId is the observation to remove
     * @return true if the operation is successful, else false
     */
    public boolean removeObservation(String sessionId, String observationId) {
        if (sessionId == null) throw new NullPointerException();
        Session session = lookupOrCreateSession(sessionId);
        return session.removeObservation(observationId);
    }

    /**
     * Returns an array of Strings that is the observation ids that are
     * currently in the session pool.
     * <p/>
     * @param sessionId is the id of the session that should be listed
     * @return an array of observations.  If no observations are resent an
     *         empty array is returned never null.
     */
    public String[] getObservations(String sessionId) throws ServiceException {
        if (sessionId == null) throw new NullPointerException();
        Session session = lookupOrCreateSession(sessionId);
        return session.getObservations();
    }

    /**
     * Returns a list of <tt>Maps</tt> containing observationIDs and observation titles.
     * The attribute names in the map are "id" and "title".
     * Note that this call requires that the database be contacted to verify the existence of the observations.
     *
     * @param sessionId The observation information returned will come from the session with this name
     * @return an ordered <tt>List</tt> of <tt>Map</tt>s with two members each.
     * @throws ServiceException if a problem is found --
     */
    public List<Map<String, String>> getObservationsAndTitles(String sessionId) throws ServiceException {
        if (sessionId == null) throw new NullPointerException("sessionId");
        Session session = lookupOrCreateSession(sessionId);
        List<QueuedObservation> qobsList = session.getObservationsAndTitles();

        // Now convert them to the thing XML-RPC needs
        return Helpers.toListOfMaps(qobsList);
    }

    /**
     * Removes all the observations in a session, leaving an empty session.
     */
    public void removeAllObservations(String sessionId) {
        if (sessionId == null) throw new NullPointerException();
        Session session = lookupOrCreateSession(sessionId);
        session.removeAllObservations();
    }


    /**
     * --------------------- Time monitoring methods ----------------------
     */

    private SPObservationID _toObservationID(String observationID) throws ServiceException {
        SPObservationID spObservationID;
        try {
            spObservationID = new SPObservationID(observationID);
        } catch (SPBadIDException ex) {
            throw new ServiceException("Bad observation ID: " + observationID);
        }
        return spObservationID;
    }

    private void _checkDatabase() throws ServiceException {
        if (_ctx.getWdbaDatabaseAccessService() == null) {
            String message = "Database unavailable: try again later.";
            LOG.severe(message);
            throw new ServiceException(message);
        }
    }

    /**
     * Sender indicates that an observation has started.
     */
    public boolean observationStart(String sessionID, String observationID) throws ServiceException {
        return observationStart(sessionID, observationID, Instant.now());
    }

    public boolean observationStart(String sessionID, String observationID, Instant when) throws ServiceException {
        assert (sessionID != null) && (observationID != null) : "an observationStart arg is null";
        _checkDatabase();
        Session session = lookupOrCreateSession(sessionID);
        SPObservationID spObservationID = _toObservationID(observationID);
        SessionEvent sse = new SessionEvent(this, spObservationID, EventMsg.OBSERVATION_START, when);
        session.doEventMsg(sse);
        return true;
    }

    /**
     * Sending TCC indicates that an ongoing observation has ended and entered
     * the idle state.
     */
    public boolean observationEnd(String sessionID, String observationID) throws ServiceException {
        return observationEnd(sessionID, observationID, Instant.now());
    }

    public boolean observationEnd(String sessionID, String observationID, Instant when) throws ServiceException {
        assert (sessionID != null) && (observationID != null) : "an observationEnd arg is null";
        _checkDatabase();
        Session session = lookupOrCreateSession(sessionID);
        SPObservationID spObservationID = _toObservationID(observationID);
        SessionEvent sse = new SessionEvent(this, spObservationID, EventMsg.OBSERVATION_END, when);
        session.doEventMsg(sse);
        return true;
    }

    /**
     * The sending application indicates that the sequence has started.
     */
    public boolean sequenceStart(String sessionID, String observationID, String firstFileName) throws ServiceException {
        return sequenceStart(sessionID, observationID, Instant.now(), firstFileName);
    }

    public boolean sequenceStart(String sessionID, String observationID, Instant when, String firstFileName) throws ServiceException {
        assert (sessionID != null) && (observationID != null) && (firstFileName != null) : "a sequenceStart arg is null";
        _checkDatabase();
        Session session = lookupOrCreateSession(sessionID);
        SPObservationID spObservationID = _toObservationID(observationID);
        SessionEvent sse = new SequenceStartEvent(this, spObservationID, when, firstFileName);
        session.doEventMsg(sse);
        return true;
    }

    /**
     * Indicates the given observation has completed its sequence.
     */
    public boolean sequenceEnd(String sessionID, String observationID) throws ServiceException {
        return sequenceEnd(sessionID, observationID, Instant.now());
    }

    public boolean sequenceEnd(String sessionID, String observationID, Instant when) throws ServiceException {
        assert (sessionID != null) && (observationID != null) : "a sequenceEnd arg is null";
        _checkDatabase();
        Session session = lookupOrCreateSession(sessionID);
        SPObservationID spObservationID = _toObservationID(observationID);
        SessionEvent sse = new SessionEvent(this, spObservationID, EventMsg.SEQUENCE_END, when);
        session.doEventMsg(sse);
        return true;
    }

    /**
     * The sending application indciates that the session is now idle.
     */
    public boolean setIdleCause(String sessionID, String category, String comment) throws ServiceException {
        return setIdleCause(sessionID, Instant.now(), category, comment);
    }

    public boolean setIdleCause(String sessionID, Instant when, String category, String comment) throws ServiceException {
        // Note that comment may be null
        assert (sessionID != null) && (category != null) : "a sequenceEnd arg is null";
        _checkDatabase();
        Session session = lookupOrCreateSession(sessionID);
        SessionEvent sse = new IdleCauseEvent(this, when, category, comment);
        session.doEventMsg(sse);
        return true;
    }

    /**
     * The sender has aborted the sequence or observation for some reason which may be
     * null causing no error.
     */
    public boolean observationAbort(String sessionID, String observationID, String reason) throws ServiceException {
        return observationAbort(sessionID, observationID, Instant.now(), reason);
    }

    public boolean observationAbort(String sessionID, String observationID, Instant when, String reason) throws ServiceException {
        assert (sessionID != null) && (observationID != null) : "a sequenceEnd arg is null";
        _checkDatabase();
        Session session = lookupOrCreateSession(sessionID);
        SPObservationID spObservationID = _toObservationID(observationID);
        SessionEvent sse = new ObservationAbortEvent(this, spObservationID, when, reason);
        session.doEventMsg(sse);
        return true;
    }

    /**
     * The sender has paused the sequence or observation
     */
    public boolean observationPause(String sessionID, String observationID, String reason) throws ServiceException {
        return observationPause(sessionID, observationID, Instant.now(), reason);
    }

    public boolean observationPause(String sessionID, String observationID, Instant when, String reason) throws ServiceException {
        // Note that reason may be null
        assert (sessionID != null) && (observationID != null) : "a sequenceEnd arg is null";
        _checkDatabase();
        Session session = lookupOrCreateSession(sessionID);
        SPObservationID spObservationID = _toObservationID(observationID);
        SessionEvent sse = new ObservationPauseEvent(this, spObservationID, when, reason);
        session.doEventMsg(sse);
        return true;
    }

    /**
     * The sender has continued the sequence or observation
     */
    public boolean observationContinue(String sessionID, String observationID) throws ServiceException {
        return observationContinue(sessionID, observationID, Instant.now());
    }

    public boolean observationContinue(String sessionID, String observationID, Instant when) throws ServiceException {
        assert (sessionID != null) && (observationID != null) : "a sequenceEnd arg is null";
        _checkDatabase();
        Session session = lookupOrCreateSession(sessionID);
        SPObservationID spObservationID = _toObservationID(observationID);
        SessionEvent sse = new SessionEvent(this, spObservationID, EventMsg.OBSERVATION_CONTINUE, when);
        session.doEventMsg(sse);
        return true;
    }

    /**
     * The sender has stopped the sequence or observation for some reason which may be
     * null causing no error.
     */
    public boolean observationStop(String sessionID, String observationID, String reason) throws ServiceException {
        return observationStop(sessionID, observationID, Instant.now(), reason);
    }

    public boolean observationStop(String sessionID, String observationID, Instant when, String reason) throws ServiceException {
        // Note that reason may be null
        assert (sessionID != null) && (observationID != null) : "a sequenceEnd arg is null";
        _checkDatabase();
        Session session = lookupOrCreateSession(sessionID);
        SPObservationID spObservationID = _toObservationID(observationID);
        SessionEvent sse = new ObservationStopEvent(this, spObservationID, when, reason);
        session.doEventMsg(sse);
        return true;
    }

    /**
     * A message that indicates the given observation has started the
     * given dataset.
     */
    public boolean datasetStart(String sessionID, String observationID, String datasetID, String fileName) throws ServiceException {
        return datasetStart(sessionID, observationID, Instant.now(), datasetID, fileName);
    }

    public boolean datasetStart(String sessionID, String observationID, Instant when, String datasetID, String fileName) throws ServiceException {
        assert (sessionID != null) && (observationID != null) && (datasetID != null) && (fileName != null) : "a datasetStart arg is null";
        Session session = lookupOrCreateSession(sessionID);
        _checkDatabase();
        SPObservationID spObservationID = _toObservationID(observationID);
        SessionEvent sse = new DatasetStartEvent(this, spObservationID, when, datasetID, fileName);
        session.doEventMsg(sse);
        return true;
    }

    /**
     * A message that indicates the given observation has completed the
     * given dataset.
     */
    public boolean datasetComplete(String sessionID, String observationID, String datasetID, String fileName) throws ServiceException {
        return datasetComplete(sessionID, observationID, Instant.now(), datasetID, fileName);
    }

    public boolean datasetComplete(String sessionID, String observationID, Instant when, String datasetID, String fileName) throws ServiceException {
        assert (sessionID != null) && (observationID != null) && (datasetID != null) && (fileName != null) : "a datasetComplete arg is null";
        _checkDatabase();
        Session session = lookupOrCreateSession(sessionID);
        SPObservationID spObservationID = _toObservationID(observationID);
        SessionEvent sse = new DatasetCompleteEvent(this, spObservationID, when, datasetID, fileName);
        session.doEventMsg(sse);
        return true;
    }
}
