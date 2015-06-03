package edu.gemini.epics.acm;

/**
 * Exception generated when trying to start a command in an apply record that is
 * already executing another command.
 * 
 * @author jluhrs
 *
 */
public final class CaCommandInProgress extends Exception {

    CaCommandInProgress() {
    }

    CaCommandInProgress(String message) {
        super(message);
    }
}
