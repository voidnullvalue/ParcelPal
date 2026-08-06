package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;
import com.voidnullvalue.parcelpal.util.StatusNormalizer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UspsDomParser implements BrowserExtractionParser {
    private static final Pattern TIMESTAMP = Pattern.compile(
            "(?i)(January|February|March|April|May|June|July|August|September|October|November|December)" +
                    "\\s+\\d{1,2},\\s+\\d{4}(?:,\\s+\\d{1,2}:\\d{2}\\s*[ap]m)?");
    private static final Pattern STATE_CODE = Pattern.compile(
            "\\b(AL|AK|AZ|AR|CA|CO|CT|DE|FL|GA|HI|ID|IL|IN|IA|KS|KY|LA|ME|MD|MA|MI|MN|MS|MO|MT|NE|NV|NH|NJ|NM|NY|NC|ND|OH|OK|OR|PA|RI|SC|SD|TN|TX|UT|VT|VA|WA|WV|WI|WY|DC|PR|VI|GU|AS|MP)\\b");
    private static final DateTimeFormatter DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMMM d, uuuu, h:mm a")
            .toFormatter(Locale.US);
    private static final DateTimeFormatter DATE_ONLY = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMMM d, uuuu")
            .toFormatter(Locale.US);

    @Override
    public TrackingResult parse(String json, TrackingTarget target, String sourceId, String sourceName) throws IOException {
        final JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException error) {
            throw new IOException("USPS browser returned invalid extraction data", error);
        }

        if (root.optBoolean("challenge", false)) {
            throw new IOException("USPS presented an interactive verification challenge");
        }
        if (!root.optBoolean("ready", false)) {
            throw new IOException("USPS tracking status did not finish loading");
        }

        TrackingResult result = new TrackingResult();
        result.sourceId = sourceId;
        result.sourceName = sourceName;
        result.carrierName = "USPS";
        result.estimatedDelivery = estimatedDelivery(root.optJSONArray("banners"));

        addStepEvents(result, root.optJSONArray("steps"), target.trackingNumber, sourceName);
        if (result.events.isEmpty()) {
            addDetailEvents(result, root.optJSONArray("details"), target.trackingNumber, sourceName);
        }

        String status = clean(root.optString("status", ""));
        if (status.isEmpty() && !result.events.isEmpty()) status = result.events.get(0).description;
        if (!status.isEmpty() && result.events.isEmpty()) {
            result.events.add(event(target.trackingNumber, sourceName, status, "", 0));
        }

        result.events.sort(Comparator.comparingLong((TrackingEvent item) -> item.eventTime).reversed());
        result.statusText = status.isEmpty() ? "Unknown" : status;
        result.normalizedStatus = StatusNormalizer.normalize(result.statusText);
        if ("UNKNOWN".equals(result.normalizedStatus)) {
            for (TrackingEvent item : result.events) {
                String normalized = StatusNormalizer.normalize(item.description);
                if (!"UNKNOWN".equals(normalized)) {
                    result.normalizedStatus = normalized;
                    result.statusText = item.description;
                    break;
                }
            }
        }

        if (!result.isUseful()) throw new IOException("USPS returned no usable tracking status");
        return result;
    }

    private static void addStepEvents(TrackingResult result, JSONArray steps, String trackingNumber, String sourceName) {
        if (steps == null) return;
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < steps.length() && result.events.size() < 150; i++) {
            String block = cleanBlock(steps.optString(i, ""));
            if (block.isEmpty()) continue;
            List<String> lines = lines(block);
            if (lines.isEmpty()) continue;

            String status = lines.get(0);
            String timestampText = "";
            String location = "";
            List<String> descriptionLines = new ArrayList<>();
            for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
                String line = lines.get(lineIndex);
                Matcher timestamp = TIMESTAMP.matcher(line);
                if (timestamp.find()) {
                    timestampText = timestamp.group();
                    String remainder = clean(line.substring(0, timestamp.start()) + " " + line.substring(timestamp.end()));
                    if (!remainder.isEmpty()) descriptionLines.add(remainder);
                } else if (location.isEmpty() && isLocation(line)) {
                    location = line;
                } else if (!line.equalsIgnoreCase(status)) {
                    descriptionLines.add(line);
                }
            }

            String description = status;
            String detail = clean(String.join(" ", descriptionLines));
            if (!detail.isEmpty() && !detail.equalsIgnoreCase(status)) description += ": " + detail;
            long eventTime = parseTime(timestampText);
            TrackingEvent event = event(trackingNumber, sourceName, description, location, eventTime);
            if (seen.add(event.eventKey)) result.events.add(event);
        }
    }

    private static boolean isLocation(String line) {
        if (!line.equals(line.toUpperCase(Locale.US))) return false;
        String upper = line.toUpperCase(Locale.US);
        return STATE_CODE.matcher(upper).find() || upper.contains("DISTRIBUTION CENTER") ||
                upper.contains("POST OFFICE") || upper.contains("PROCESSING CENTER") ||
                upper.contains("USPS FACILITY");
    }

    private static void addDetailEvents(TrackingResult result, JSONArray details, String trackingNumber, String sourceName) {
        if (details == null) return;
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < details.length() && result.events.size() < 150; i++) {
            String description = cleanBlock(details.optString(i, "")).replace('\n', ' ');
            description = clean(description);
            if (description.isEmpty()) continue;
            TrackingEvent event = event(trackingNumber, sourceName, description, "", 0);
            if (seen.add(event.eventKey)) result.events.add(event);
        }
    }

    private static TrackingEvent event(String trackingNumber, String sourceName, String description,
                                       String location, long eventTime) {
        TrackingEvent event = new TrackingEvent();
        event.trackingNumber = trackingNumber;
        event.carrierName = "USPS";
        event.sourceName = sourceName;
        event.eventTime = eventTime;
        event.location = clean(location);
        event.description = clean(description);
        event.rawStatus = event.description;
        event.eventKey = TrackingEvent.makeKey(
                event.trackingNumber, event.eventTime, event.location, event.description);
        return event;
    }

    private static String estimatedDelivery(JSONArray banners) {
        if (banners == null) return "";
        String fallback = "";
        for (int i = 0; i < banners.length(); i++) {
            String value = cleanBlock(banners.optString(i, "")).replace('\n', ' ');
            value = clean(value);
            if (value.isEmpty()) continue;
            if (fallback.isEmpty()) fallback = value;
            String lower = value.toLowerCase(Locale.US);
            if (lower.contains("expected delivery") || lower.contains("estimated delivery") ||
                    lower.contains("arriving") || lower.contains("delivery by")) {
                return limit(value, 500);
            }
        }
        return limit(fallback, 500);
    }

    private static long parseTime(String raw) {
        String value = clean(raw);
        if (value.isEmpty()) return 0;
        try {
            return LocalDateTime.parse(value, DATE_TIME)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value, DATE_ONLY)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return 0;
        }
    }

    private static List<String> lines(String block) {
        List<String> result = new ArrayList<>();
        for (String raw : block.split("\\n")) {
            String line = clean(raw);
            if (!line.isEmpty()) result.add(line);
        }
        return result;
    }

    private static String cleanBlock(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}
