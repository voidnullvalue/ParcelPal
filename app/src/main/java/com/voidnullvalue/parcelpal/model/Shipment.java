package com.voidnullvalue.parcelpal.model;

public final class Shipment {
    public long id;
    public String name = "";
    public String trackingNumber = "";
    public String carrierHint = "Auto-detect";
    public String normalizedStatus = "UNKNOWN";
    public String statusText = "Not checked";
    public String estimatedDelivery = "";
    public String sourceId = "";
    public String sourceName = "";
    public String lastError = "";
    public String lastAttemptLog = "";
    public long createdAt;
    public long updatedAt;
    public long lastCheckedAt;
    public boolean archived;

    public String displayName() {
        return name == null || name.trim().isEmpty() ? trackingNumber : name.trim();
    }
}
