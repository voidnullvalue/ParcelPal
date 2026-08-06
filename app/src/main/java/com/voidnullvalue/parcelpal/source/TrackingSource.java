package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;

import java.io.IOException;
import java.util.Set;

public interface TrackingSource {
    String id();
    String displayName();
    String kind();
    Set<String> allowedHosts();
    /** Higher scores win when two sources disagree about the current status. */
    int trust();
    boolean supports(TrackingTarget target);
    TrackingResult fetch(TrackingTarget target) throws IOException;
}
