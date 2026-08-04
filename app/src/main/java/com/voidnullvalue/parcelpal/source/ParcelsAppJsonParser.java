package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;
import com.voidnullvalue.parcelpal.util.CarrierDetector;
import com.voidnullvalue.parcelpal.util.StatusNormalizer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ParcelsAppJsonParser {
    TrackingResult parse(String json, TrackingTarget target, String sourceId, String sourceName) throws IOException {
        final JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException e) {
            throw new IOException("ParcelsApp returned invalid JSON", e);
        }

        String error = root.optString("error", "").trim();
        if (!error.isEmpty()) throw new ResponseException(error, errorMessage(error));

        TrackingResult result = new TrackingResult();
        result.sourceId = sourceId;
        result.sourceName = sourceName;

        Map<Integer, Carrier> services = readServices(root.optJSONArray("services"));
        result.carrierName = readCarrier(root, services, target.carrierHint);
        result.estimatedDelivery = readEstimatedDelivery(root);

        JSONArray states = root.optJSONArray("states");
        if (states != null) {
            for (int i = 0; i < states.length(); i++) {
                JSONObject state = states.optJSONObject(i);
                if (state == null) continue;
                String description = firstText(state, "state", "status", "description", "message", "details");
                if (description.isEmpty()) continue;

                TrackingEvent event = new TrackingEvent();
                event.trackingNumber = target.trackingNumber;
                event.sourceName = sourceName;
                event.description = description;
                event.rawStatus = description;
                event.location = firstText(state, "location", "place", "city", "facility");
                event.eventTime = parseTime(firstText(state, "date", "time", "datetime", "timestamp"));
                event.carrierName = carrierForState(state, services, result.carrierName);
                event.eventKey = TrackingEvent.makeKey(
                        event.trackingNumber,
                        event.eventTime,
                        event.location,
                        event.description
                );
                result.events.add(event);
            }
        }

        result.events.sort(Comparator.comparingLong((TrackingEvent event) -> event.eventTime).reversed());
        if (result.events.size() > 150) {
            List<TrackingEvent> limited = new ArrayList<>(result.events.subList(0, 150));
            result.events.clear();
            result.events.addAll(limited);
        }

        TrackingEvent current = currentEvent(result.events);
        if (current != null) {
            result.statusText = current.description;
            result.normalizedStatus = StatusNormalizer.normalize(current.description);
        } else {
            String status = firstText(root, "status", "state", "description");
            result.statusText = status.isEmpty() ? "Unknown" : status;
            result.normalizedStatus = StatusNormalizer.normalize(status);
        }

        addLinked(result, root.optString("correctId", ""), target.trackingNumber);
        addExternalTracking(result, root.optJSONArray("externalTracking"), target.trackingNumber);
        addExternalTracking(result, root.optJSONArray("external_tracking"), target.trackingNumber);

        return result;
    }

    private static Map<Integer, Carrier> readServices(JSONArray services) {
        Map<Integer, Carrier> result = new LinkedHashMap<>();
        if (services == null) return result;
        for (int i = 0; i < services.length(); i++) {
            JSONObject service = services.optJSONObject(i);
            if (service == null) continue;
            result.put(i, new Carrier(
                    service.optString("slug", "").trim(),
                    service.optString("name", "").trim()
            ));
        }
        return result;
    }

    private static String readCarrier(JSONObject root, Map<Integer, Carrier> services, String fallback) {
        JSONObject detectedCarrier = root.optJSONObject("detectedCarrier");
        if (detectedCarrier != null) {
            String name = detectedCarrier.optString("name", "").trim();
            if (!name.isEmpty()) return name;
            String slug = detectedCarrier.optString("slug", "").trim();
            if (!slug.isEmpty()) return humanizeSlug(slug);
        }

        JSONArray detected = root.optJSONArray("detected");
        if (detected != null) {
            for (int i = 0; i < detected.length(); i++) {
                Carrier carrier = services.get(detected.optInt(i, -1));
                if (carrier != null) return carrier.displayName();
            }
        }

        return fallback == null || fallback.trim().isEmpty() ? "Auto-detect" : fallback.trim();
    }

    private static String carrierForState(JSONObject state, Map<Integer, Carrier> services, String fallback) {
        Object rawCarrier = state.opt("carrier");
        if (rawCarrier instanceof Number) {
            Carrier carrier = services.get(((Number) rawCarrier).intValue());
            if (carrier != null) return carrier.displayName();
        } else if (rawCarrier instanceof String) {
            String text = ((String) rawCarrier).trim();
            if (!text.isEmpty()) return text;
        }
        String slug = state.optString("slug", "").trim();
        return slug.isEmpty() ? fallback : humanizeSlug(slug);
    }

    private static String readEstimatedDelivery(JSONObject root) {
        String direct = firstText(root,
                "estimatedDelivery", "estimated_delivery", "deliveryDate", "delivery_date", "eta");
        if (!direct.isEmpty()) return direct;

        JSONArray attributes = root.optJSONArray("attributes");
        if (attributes == null) return "";
        for (int i = 0; i < attributes.length(); i++) {
            JSONObject attribute = attributes.optJSONObject(i);
            if (attribute == null) continue;
            String label = firstText(attribute, "title", "label", "l").toLowerCase(Locale.US);
            if (!label.contains("estimate") && !label.contains("deliver")) continue;
            String value = firstText(attribute, "value", "val");
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static void addExternalTracking(TrackingResult result, JSONArray external, String original) {
        if (external == null) return;
        for (int i = 0; i < external.length(); i++) {
            Object entry = external.opt(i);
            if (entry instanceof JSONObject) {
                JSONObject item = (JSONObject) entry;
                addLinked(result, firstText(item, "trackingId", "tracking_number", "trackingNumber"), original);
            } else if (entry instanceof String) {
                addLinked(result, (String) entry, original);
            }
        }
    }

    private static void addLinked(TrackingResult result, String raw, String original) {
        String candidate = CarrierDetector.normalizeTrackingNumber(raw);
        String root = CarrierDetector.normalizeTrackingNumber(original);
        if (candidate.equals(root) || !CarrierDetector.plausible(candidate)) return;
        if (!result.linkedTrackingNumbers.contains(candidate)) result.linkedTrackingNumbers.add(candidate);
    }

    private static TrackingEvent currentEvent(List<TrackingEvent> events) {
        if (events.isEmpty()) return null;
        for (TrackingEvent event : events) if (event.eventTime > 0) return event;
        return events.get(0);
    }

    private static String firstText(JSONObject object, String... keys) {
        for (String key : keys) {
            Object value = object.opt(key);
            if (value == null || value == JSONObject.NULL) continue;
            String text = String.valueOf(value).trim();
            if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) return text;
        }
        return "";
    }

    private static long parseTime(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        String candidate = value.trim();
        try { return Instant.parse(candidate).toEpochMilli(); } catch (DateTimeParseException ignored) {}
        try { return OffsetDateTime.parse(candidate).toInstant().toEpochMilli(); } catch (DateTimeParseException ignored) {}

        DateTimeFormatter[] dateTimes = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a", Locale.US),
                DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.US),
                DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US),
                DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a", Locale.US)
        };
        for (DateTimeFormatter formatter : dateTimes) {
            try {
                return LocalDateTime.parse(candidate, formatter)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {}
        }

        DateTimeFormatter[] dates = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
                DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
        };
        for (DateTimeFormatter formatter : dates) {
            try {
                return LocalDate.parse(candidate, formatter)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {}
        }
        return 0;
    }

    private static String humanizeSlug(String slug) {
        String[] parts = slug.replace('_', '-').split("-");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) result.append(part.substring(1));
        }
        return result.toString();
    }

    private static String errorMessage(String code) {
        return switch (code.toUpperCase(Locale.US)) {
            case "NO_TRACKER" -> "ParcelsApp could not select a carrier for this number";
            case "NO_DATA" -> "ParcelsApp found the carrier but returned no tracking events";
            case "INVALID_TRACKING_NUMBER" -> "ParcelsApp rejected the tracking number";
            case "CAPTCHA" -> "ParcelsApp requires interactive browser verification";
            case "RELOAD" -> "ParcelsApp rejected the web session; retrying may create a fresh session";
            case "IP_BLOCKED" -> "ParcelsApp blocked this network address";
            case "BUSY", "DOWN", "MAINTENANCE" -> "ParcelsApp tracking service is temporarily unavailable";
            case "PARSER" -> "ParcelsApp could not parse the carrier response";
            default -> "ParcelsApp returned error " + code;
        };
    }

    static final class ResponseException extends IOException {
        final String code;

        ResponseException(String code, String message) {
            super(message);
            this.code = code == null ? "" : code.trim().toUpperCase(Locale.US);
        }
    }

    private static final class Carrier {
        final String slug;
        final String name;

        Carrier(String slug, String name) {
            this.slug = slug;
            this.name = name;
        }

        String displayName() {
            return name.isEmpty() ? humanizeSlug(slug) : name;
        }
    }
}
