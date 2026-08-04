package com.voidnullvalue.parcelpal.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TrackingResult {
    public String sourceId = "";
    public String sourceName = "";
    public String carrierName = "";
    public String normalizedStatus = "UNKNOWN";
    public String statusText = "Unknown";
    public String estimatedDelivery = "";
    public final List<TrackingEvent> events = new ArrayList<>();
    public final List<String> linkedTrackingNumbers = new ArrayList<>();

    public boolean isUseful() {
        if (!events.isEmpty()) return true;
        String status = statusText == null ? "" : statusText.trim();
        return !status.isEmpty() && !"Unknown".equalsIgnoreCase(status) && !"Not found".equalsIgnoreCase(status);
    }

    public long newestEventTime() {
        long newest = 0;
        for (TrackingEvent event : events) newest = Math.max(newest, event.eventTime);
        return newest;
    }

    public List<TrackingEvent> immutableEvents() {
        return Collections.unmodifiableList(events);
    }
}
