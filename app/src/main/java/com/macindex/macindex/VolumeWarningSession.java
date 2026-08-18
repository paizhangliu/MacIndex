package com.macindex.macindex;

/** Owns the one-per-process high-volume warning gate. */
final class VolumeWarningSession {

    private boolean armed = true;

    boolean isArmed() {
        return armed;
    }

    void disarm() {
        armed = false;
    }
}
