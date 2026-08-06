package com.voidnullvalue.parcelpal.model;

import com.voidnullvalue.parcelpal.util.CarrierDetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TrackingTarget {
    public final String trackingNumber;
    /** The primary carrier name shown to the user. */
    public final String carrierHint;
    /** Every carrier this number may belong to, most likely first. Never empty. */
    public final List<String> carrierCandidates;

    /**
     * Builds a target for one carrier hint. A blank or {@code Auto-detect} hint expands into every
     * carrier whose number format matches; an explicit hint is honored exactly.
     */
    public TrackingTarget(String trackingNumber, String carrierHint) {
        this(trackingNumber, carrierHint, null);
    }

    public TrackingTarget(String trackingNumber, String carrierHint, List<String> carrierCandidates) {
        this.trackingNumber = trackingNumber == null ? "" : trackingNumber;
        String hint = carrierHint == null || carrierHint.trim().isEmpty()
                ? CarrierDetector.AUTO_DETECT : carrierHint.trim();

        List<String> candidates = new ArrayList<>();
        if (carrierCandidates != null) {
            for (String candidate : carrierCandidates) {
                if (candidate != null && !candidate.trim().isEmpty() && !candidates.contains(candidate.trim())) {
                    candidates.add(candidate.trim());
                }
            }
        }
        if (candidates.isEmpty()) {
            if (CarrierDetector.AUTO_DETECT.equalsIgnoreCase(hint)) {
                candidates.addAll(CarrierDetector.detectAll(this.trackingNumber));
            } else {
                candidates.add(hint);
            }
        }
        if (candidates.isEmpty()) candidates.add(CarrierDetector.AUTO_DETECT);

        this.carrierCandidates = Collections.unmodifiableList(candidates);
        this.carrierHint = CarrierDetector.AUTO_DETECT.equalsIgnoreCase(hint) ? candidates.get(0) : hint;
    }
}
