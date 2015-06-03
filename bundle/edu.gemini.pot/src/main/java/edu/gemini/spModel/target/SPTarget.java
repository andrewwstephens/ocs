// Copyright 1997-2000
// Association for Universities for Research in Astronomy, Inc.,
// Observatory Control System, Gemini Telescopes Project.
// See the file COPYRIGHT for complete details.
//
// $Id: SPTarget.java 45443 2012-05-23 20:26:52Z abrighton $
//
package edu.gemini.spModel.target;

import edu.gemini.spModel.pio.ParamSet;
import edu.gemini.spModel.pio.PioFactory;
import edu.gemini.spModel.target.system.*;
import edu.gemini.spModel.target.system.CoordinateParam.Units;
import edu.gemini.spModel.target.system.CoordinateTypes.*;

/** A mutable cell containing an ITarget. */
public final class SPTarget extends WatchablePos {

    private ITarget _target;

    /** SPTarget with default empty target. */
    public SPTarget() {
        this(new HmsDegTarget()); // why not
    }

    /** SPTarget with given target. */
    public SPTarget(final ITarget target) {
        _target = target;
    }

    /** SPTarget with the given RA/Dec in degrees. */
    public SPTarget(final double raDeg, final double degDec) {
        this();
        _target.getRa().setAs(raDeg, Units.DEGREES);
        _target.getDec().setAs(degDec, Units.DEGREES);
    }

    /** Return the contained target. */
    public ITarget getTarget() {
        return _target;
    }

    /** Replace the contained target and notify listeners. */
    public void setTarget(final ITarget target) {
        _target = target;
        _notifyOfUpdate();
    }

    /**
     * Replace the contained target with a new, empty target of the specified type, or do nothing
     * if the contained target is of the specified type.
     */
    public void setTargetType(final ITarget.Tag tag) {
        if (tag != _target.getTag())
            setTarget(ITarget.forTag(tag));
    }

    /** Return a paramset describing this SPTarget. */
    public ParamSet getParamSet(final PioFactory factory) {
        return SPTargetPio.getParamSet(this, factory);
    }

    /** Re-initialize this SPTarget from the given paramset */
    public void setParamSet(final ParamSet paramSet) {
        SPTargetPio.setParamSet(paramSet, this);
    }

    /** Construct a new SPTarget from the given paramset */
    public static SPTarget fromParamSet(final ParamSet pset) {
        return SPTargetPio.fromParamSet(pset);
    }

    /** Clone this SPTarget. */
    public SPTarget clone() {
        return new SPTarget(_target.clone());
    }


    ///
    /// END OF PUBLIC API ... EVERYTHING FROM HERE DOWN GOES AWAY
    ///

    /** Get the PM radial velocity in km/s if the contained target is sidereal, otherwise zero. */
    public double getTrackingRadialVelocity() {
        double res = 0.0;
        if (_target instanceof HmsDegTarget) {
            final HmsDegTarget t = (HmsDegTarget)_target;
            res = t.getRV().getValue();
        }
        return res;
    }

    /** Set the PM radial velocity in km/s if the contained target is sidereal, otherwise throw. */
    public void setTrackingRadialVelocity(final double newValue) {
        if (_target instanceof HmsDegTarget) {
            final HmsDegTarget t = (HmsDegTarget)_target;
            final RV rv = new RV(newValue);
            t.setRV(rv);
            _notifyOfUpdate();
        } else {
            throw new IllegalArgumentException();
        }
    }

    // I'm making this public so I can call it from an editor when I make
    // a change to the contained target, rather than publishing all the
    // target members through this idiotic class. All of this crap needs
    // to be rewritten.
    /** @deprecated */
    public void notifyOfGenericUpdate() {
    	super._notifyOfUpdate();
    }

}
