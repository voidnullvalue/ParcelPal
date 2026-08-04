package com.voidnullvalue.parcelpal.model;

public final class TrackingLeg {
    public long id;
    public long shipmentId;
    public String trackingNumber = "";
    public String carrierHint = "Auto-detect";
    public String normalizedStatus = "UNKNOWN";
    public String statusText = "Not checked";
    public String estimatedDelivery = "";
    public String sourceId = "";
    public String sourceName = "";
    public String lastError = "";
    public long discoveredAt;
    public long lastCheckedAt;
}
