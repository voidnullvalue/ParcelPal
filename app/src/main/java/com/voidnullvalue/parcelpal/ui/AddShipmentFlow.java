package com.voidnullvalue.parcelpal.ui;

/**
 * Coordinates one add-package submission so a canceled or duplicated request cannot reopen
 * package details after the user has navigated away.
 */
public final class AddShipmentFlow {
    private boolean submitting;
    private boolean canceled;
    private boolean completed;

    public synchronized boolean beginSubmit() {
        if (submitting || completed) return false;
        submitting = true;
        return true;
    }

    public synchronized void cancelNavigation() {
        canceled = true;
    }

    public synchronized void failSubmit() {
        submitting = false;
    }

    public synchronized boolean completeSubmit() {
        if (!submitting || completed) return false;
        submitting = false;
        completed = true;
        return !canceled;
    }
}
