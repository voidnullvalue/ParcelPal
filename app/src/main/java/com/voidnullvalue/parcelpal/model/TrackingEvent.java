package com.voidnullvalue.parcelpal.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class TrackingEvent {
    public long id;
    public long shipmentId;
    public String trackingNumber = "";
    public String carrierName = "";
    public String sourceId = "";
    public String sourceName = "";
    public long eventTime;
    public String location = "";
    public String description = "";
    public String rawStatus = "";
    public String eventKey = "";

    public static String makeKey(String trackingNumber, long eventTime, String location, String description) {
        String material = safe(trackingNumber) + "\u001f" + eventTime + "\u001f" + safe(location) + "\u001f" + safe(description);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(material.hashCode());
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
