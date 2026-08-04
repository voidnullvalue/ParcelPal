package com.voidnullvalue.parcelpal.model;

public final class TrackingTarget {
    public final String trackingNumber;
    public final String carrierHint;

    public TrackingTarget(String trackingNumber, String carrierHint) {
        this.trackingNumber = trackingNumber == null ? "" : trackingNumber;
        this.carrierHint = carrierHint == null || carrierHint.trim().isEmpty() ? "Auto-detect" : carrierHint;
    }
}
