package edu.gemini.shared.util.immutable;

import java.io.Serializable;

/**
 * Default implementation of the Tuple2 interface.
 */
public final class Pair<T, U> implements Tuple2<T, U>, Serializable {

    private final T _1;
    private final U _2;

    public Pair(final T left, final U right) {
        _1 = left;
        _2 = right;
    }

    public T _1() {
        return _1;
    }

    public U _2() {
        return _2;
    }

    public Pair<U, T> swap() {
        return new Pair<>(_2, _1);
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Tuple2)) return false;

        final Tuple2<T, U> that = (Tuple2<T, U>) o;
        if (_1 == null) {
            if (that._1() != null) return false;
        } else {
            if (!_1.equals(that._1())) return false;
        }

        if (_2 == null) {
            if (that._2() != null) return false;
        } else {
            if (!_2.equals(that._2())) return false;
        }

        return true;
    }

    public int hashCode() {
        int res = 1;
        if (_1 != null) res = _1.hashCode();
        if (_2 != null) res = 37*res + _2.hashCode();
        return res;
    }

    public String mkString(final String prefix, final String sep, final String suffix) {
        return prefix + _1 + sep + _2 + suffix;
    }

    public String toString() {
        return mkString("(", ", ", ")");
    }
}
