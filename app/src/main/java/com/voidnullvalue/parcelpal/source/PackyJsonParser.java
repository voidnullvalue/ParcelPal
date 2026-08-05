package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.util.CarrierDetector;
import com.voidnullvalue.parcelpal.util.StatusNormalizer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;

final class PackyJsonParser {
    TrackingResult parse(String payload, String trackingNumber, String sourceId, String sourceName) throws IOException {
        try {
            JSONObject root = new JSONObject(payload);
            String requested = CarrierDetector.normalizeTrackingNumber(trackingNumber);
            String returned = CarrierDetector.normalizeTrackingNumber(root.optString("trackCode"));
            if (returned.isEmpty()) throw new IOException(message(root, "Packy returned no tracking record"));
            if (!requested.equals(returned)) throw new IOException("Packy returned a different tracking record");

            JSONArray rawEvents = root.optJSONArray("events");
            if (rawEvents == null || rawEvents.length() == 0) {
                throw new IOException(message(root, "Packy returned no tracking events"));
            }

            TrackingResult result = new TrackingResult();
            result.sourceId = sourceId;
            result.sourceName = sourceName;
            result.carrierName = "1ST";

            for (int index = 0; index < rawEvents.length(); index++) {
                JSONObject raw = rawEvents.optJSONObject(index);
                if (raw == null) continue;
                String description = raw.optString("description").trim();
                if (description.isEmpty()) continue;

                JSONObject carrier = raw.optJSONObject("carrier");
                String carrierName = carrier == null ? "" : carrier.optString("name").trim();
                if (!carrierName.isEmpty()) result.carrierName = carrierName;

                TrackingEvent event = new TrackingEvent();
                event.trackingNumber = requested;
                event.carrierName = carrierName.isEmpty() ? result.carrierName : carrierName;
                event.sourceName = sourceName;
                event.eventTime = parseTime(raw.optString("eventDate"));
                event.location = raw.isNull("place") ? "" : raw.optString("place").trim();
                event.description = description;
                event.rawStatus = raw.optString("status").trim();
                event.eventKey = TrackingEvent.makeKey(
                        event.trackingNumber,
                        event.eventTime,
                        event.location,
                        event.description
                );
                result.events.add(event);
            }

            if (result.events.isEmpty()) throw new IOException("Packy returned no usable tracking events");
            result.events.sort(Comparator.comparingLong((TrackingEvent event) -> event.eventTime).reversed());

            TrackingEvent newest = result.events.get(0);
            result.statusText = newest.description;
            result.normalizedStatus = StatusNormalizer.normalize(newest.description);
            if ("UNKNOWN".equals(result.normalizedStatus)) {
                result.normalizedStatus = normalizePackyStatus(root.optString("status"));
            }
            if ("UNKNOWN".equals(result.normalizedStatus)) {
                result.normalizedStatus = normalizePackyStatus(newest.rawStatus);
            }
            if (!result.isUseful()) throw new IOException("Packy returned no usable tracking state");
            return result;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Packy returned malformed tracking data", e);
        }
    }

    private static String normalizePackyStatus(String raw) {
        if (raw == null) return "UNKNOWN";
        return switch (raw.trim().toLowerCase()) {
            case "delivered" -> "DELIVERED";
            case "out_for_delivery", "out-for-delivery" -> "OUT_FOR_DELIVERY";
            case "transit", "in_transit", "in-transit" -> "IN_TRANSIT";
            case "exception", "failed", "returned" -> "EXCEPTION";
            case "pending", "pre_transit", "pre-transit", "info_received" -> "PRE_TRANSIT";
            default -> "UNKNOWN";
        };
    }

    private static long parseTime(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        String candidate = value.trim();
        try {
            return Instant.parse(candidate).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(candidate).toInstant().toEpochMilli();
            } catch (DateTimeParseException alsoIgnored) {
                return 0;
            }
        }
    }

    private static String message(JSONObject root, String fallback) {
        String message = root.optString("message").trim();
        if (message.isEmpty()) message = root.optString("error").trim();
        return message.isEmpty() ? fallback : message;
    }
}
