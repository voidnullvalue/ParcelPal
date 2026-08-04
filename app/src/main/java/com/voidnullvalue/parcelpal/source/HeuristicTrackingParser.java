package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.util.CarrierDetector;
import com.voidnullvalue.parcelpal.util.StatusNormalizer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HeuristicTrackingParser {
    private static final Pattern JSON_STATUS = Pattern.compile("(?i)\\\"(?:status|description|message|details|checkpoint_status|substatus|state|activity|event)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"]){1,350})\\\"");
    private static final Pattern JSON_LOCATION = Pattern.compile("(?i)\\\"(?:location|city|place|address|facility)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"]){0,220})\\\"");
    private static final Pattern JSON_TIME = Pattern.compile("(?i)\\\"(?:time|date|datetime|event_time|checkpoint_time|created_at|timestamp|eventDateTime)\\\"\\s*:\\s*\\\"([^\\\"]{4,90})\\\"");
    private static final Pattern EXPLICIT_LINKED_JSON = Pattern.compile(
            "(?i)\\\"(?:(?:final|last)[_-]?mile[_-]?tracking(?:[_-]?number)?|(?:local|alternate|linked|next|new|delivery|postal)[_-]?tracking(?:[_-]?number)?)\\\"\\s*:\\s*\\\"([^\\\"]{1,120})\\\"");
    private static final Pattern VISIBLE_LINK_CONTEXT = Pattern.compile(
            "(?i)(?:(?:final|last)[ -]?mile|local|alternate|linked|next|new|delivery|postal|USPS|UPS|FedEx|DHL)\\s+(?:carrier\\s+)?tracking(?:\\s+number)?\\s*[:#-]?\\s*(.{1,100})");
    private static final Pattern CONTEXTUAL_TRACKING_VALUE = Pattern.compile(
            "(?i)\\b(?=[A-Z0-9]{7,50}\\b)(?=[A-Z0-9]*[0-9])[A-Z0-9]+\\b");
    private static final Pattern ETA_PATTERN = Pattern.compile("(?i)(?:estimated|expected|scheduled|delivery date|arriving|deliver(?:y|ed)? by)\\s*[:\\-]?\\s*([A-Z][a-z]{2,8}\\s+\\d{1,2}(?:,\\s*\\d{4})?|\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{2,4})");
    private static final Pattern DATE_PREFIX = Pattern.compile("(?i)^(?:\\w{3,9}\\s+\\d{1,2},?\\s+\\d{4}|\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})(?:\\s+.{0,18})?");
    private static final Pattern ISO_IN_TEXT = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}(?:[T ]\\d{2}:\\d{2}(?::\\d{2})?(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?)?\\b");

    public TrackingResult parse(String payload, String trackingNumber, String sourceId, String sourceName) {
        TrackingResult result = new TrackingResult();
        result.sourceId = sourceId;
        result.sourceName = sourceName;
        if (payload == null || payload.trim().isEmpty()) return result;

        String compactTracking = CarrierDetector.normalizeTrackingNumber(trackingNumber);
        String normalizedPayload = payload.toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
        if (!normalizedPayload.contains(compactTracking)) return result;

        Document doc = Jsoup.parse(payload);
        result.carrierName = inferCarrier(doc, trackingNumber);
        result.estimatedDelivery = extractEstimatedDelivery(doc.text());

        List<TrackingEvent> events = new ArrayList<>();
        for (Element script : doc.select("script")) {
            String data = script.data();
            if (data == null || data.trim().isEmpty()) data = script.html();
            if (data != null && data.toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "").contains(compactTracking)) {
                extractJsonLikeEvents(data, trackingNumber, result.carrierName, sourceName, events);
            }
        }
        extractVisibleEvents(doc, trackingNumber, result.carrierName, sourceName, events);
        deduplicate(events);
        events.sort(Comparator.comparingLong((TrackingEvent event) -> event.eventTime).reversed());
        if (events.size() > 150) events = new ArrayList<>(events.subList(0, 150));
        result.events.addAll(events);

        if (!events.isEmpty()) {
            TrackingEvent newest = chooseCurrent(events);
            result.statusText = newest.description;
            result.normalizedStatus = StatusNormalizer.normalize(newest.description);
        } else {
            String visibleStatus = findVisibleStatus(doc);
            result.statusText = visibleStatus;
            result.normalizedStatus = StatusNormalizer.normalize(visibleStatus);
        }

        result.linkedTrackingNumbers.addAll(findLinkedTrackingNumbers(doc, payload, compactTracking));
        return result;
    }

    private static void extractJsonLikeEvents(String data, String trackingNumber, String carrier, String source, List<TrackingEvent> out) {
        Matcher statusMatcher = JSON_STATUS.matcher(data);
        while (statusMatcher.find() && out.size() < 250) {
            String description = clean(unescape(statusMatcher.group(1)));
            if (!looksLikeTrackingStatus(description)) continue;
            int start = Math.max(0, statusMatcher.start() - 800);
            int end = Math.min(data.length(), statusMatcher.end() + 800);
            String context = data.substring(start, end);
            String location = clean(unescape(firstGroup(JSON_LOCATION, context)));
            String time = unescape(firstGroup(JSON_TIME, context));
            addEvent(out, trackingNumber, carrier, source, description, location, parseTime(time));
        }
    }

    private static void extractVisibleEvents(Document doc, String trackingNumber, String carrier, String source, List<TrackingEvent> out) {
        for (Element element : doc.select("li, tr, article, [class*=event], [class*=checkpoint], [class*=history], [class*=timeline], [class*=tracking], [data-status]")) {
            String text = clean(element.text());
            if (text.length() < 6 || text.length() > 600 || !looksLikeTrackingStatus(text)) continue;
            long time = parseTime(text);
            String description = removeLeadingDate(text);
            if (description.length() < 4) description = text;
            String location = extractLocation(element);
            addEvent(out, trackingNumber, carrier, source, description, location, time);
            if (out.size() >= 250) return;
        }
    }

    private static void addEvent(List<TrackingEvent> out, String trackingNumber, String carrier, String source,
                                 String description, String location, long time) {
        TrackingEvent event = new TrackingEvent();
        event.trackingNumber = trackingNumber;
        event.carrierName = carrier;
        event.sourceName = source;
        event.description = description;
        event.rawStatus = description;
        event.location = location;
        event.eventTime = time;
        event.eventKey = TrackingEvent.makeKey(trackingNumber, time, location, description);
        out.add(event);
    }

    private static TrackingEvent chooseCurrent(List<TrackingEvent> events) {
        for (TrackingEvent event : events) if (event.eventTime > 0) return event;
        return events.get(0);
    }

    private static String findVisibleStatus(Document doc) {
        for (Element element : doc.select("[class*=status], [data-status], [aria-label*=status], h1, h2, h3")) {
            String text = clean(element.text());
            if (text.length() <= 250 && looksLikeTrackingStatus(text)) return text;
        }
        return "Unknown";
    }

    private static String inferCarrier(Document doc, String trackingNumber) {
        String detected = CarrierDetector.detect(trackingNumber);
        if (!"Auto-detect".equals(detected)) return detected;
        String haystack = clean(doc.title() + " " + doc.select("meta[name=description]").attr("content")).toLowerCase(Locale.US);
        String[] carriers = {"USPS", "UPS", "FedEx", "DHL", "OnTrac", "LaserShip", "Canada Post", "Royal Mail", "Australia Post", "YunExpress", "Cainiao", "4PX", "UniUni"};
        for (String carrier : carriers) if (haystack.contains(carrier.toLowerCase(Locale.US))) return carrier;
        return "Auto-detect";
    }

    private static Set<String> findLinkedTrackingNumbers(Document doc, String payload, String original) {
        Set<String> linked = new LinkedHashSet<>();

        Matcher jsonMatcher = EXPLICIT_LINKED_JSON.matcher(payload);
        while (jsonMatcher.find() && linked.size() < 6) {
            addContextualTrackingValues(jsonMatcher.group(1), original, linked);
        }

        for (Element element : doc.select("body *")) {
            String ownText = clean(element.ownText());
            if (ownText.isEmpty() || ownText.length() > 250) continue;
            Matcher visibleMatcher = VISIBLE_LINK_CONTEXT.matcher(ownText);
            while (visibleMatcher.find() && linked.size() < 6) {
                addContextualTrackingValues(visibleMatcher.group(1), original, linked);
            }
            if (linked.size() >= 6) break;
        }
        return linked;
    }

    private static void addContextualTrackingValues(String value, String original, Set<String> linked) {
        if (value == null || value.trim().isEmpty()) return;
        Matcher candidateMatcher = CONTEXTUAL_TRACKING_VALUE.matcher(value.toUpperCase(Locale.US));
        while (candidateMatcher.find() && linked.size() < 6) {
            String candidate = CarrierDetector.normalizeTrackingNumber(candidateMatcher.group());
            if (candidate.equals(original) || !CarrierDetector.plausible(candidate)) continue;
            linked.add(candidate);
        }
    }

    private static String extractEstimatedDelivery(String text) {
        Matcher matcher = ETA_PATTERN.matcher(clean(text));
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String extractLocation(Element element) {
        for (Element child : element.select("[class*=location], [class*=place], [class*=city], [data-location]")) {
            String text = clean(child.text());
            if (!text.isEmpty() && text.length() <= 180) return text;
        }
        return "";
    }

    private static boolean looksLikeTrackingStatus(String text) {
        if (text == null) return false;
        String s = clean(text).toLowerCase(Locale.US);
        if (s.isEmpty() || s.contains("privacy policy") || s.contains("cookie") || s.contains("track any package") ||
                s.contains("how to track") || s.contains("download the app")) return false;
        if (isGenericTrackingLabel(s)) return false;
        if (!"UNKNOWN".equals(StatusNormalizer.normalize(s))) return true;
        return s.contains("tendered to") || s.contains("en route") || s.contains("on the way") ||
                s.contains("forwarded to") || s.contains("received at") || s.contains("loaded for transport") ||
                s.contains("awaiting carrier pickup") || s.contains("manifested") || s.contains("departing") ||
                s.contains("released from customs");
    }

    private static boolean isGenericTrackingLabel(String value) {
        String s = value.replaceAll("[.:\\-]+$", "").trim();
        return s.equals("delivery time") || s.equals("estimated delivery") || s.equals("expected delivery") ||
                s.equals("delivery date") || s.equals("shipment tracking") || s.equals("tracking details") ||
                s.equals("tracking information") || s.equals("package status") || s.equals("shipment status") ||
                s.equals("delivery status") || s.equals("status");
    }

    private static long parseTime(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        String candidate = value.trim();
        Matcher embedded = ISO_IN_TEXT.matcher(candidate);
        if (embedded.find()) candidate = embedded.group();
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
            try { return LocalDateTime.parse(candidate, formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
            catch (DateTimeParseException ignored) {}
        }
        DateTimeFormatter[] dates = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
                DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
        };
        for (DateTimeFormatter formatter : dates) {
            try { return LocalDate.parse(candidate, formatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); }
            catch (DateTimeParseException ignored) {}
        }
        return 0;
    }

    private static String removeLeadingDate(String text) {
        Matcher matcher = DATE_PREFIX.matcher(text);
        return matcher.find() ? clean(text.substring(matcher.end())) : text;
    }

    private static void deduplicate(List<TrackingEvent> events) {
        Map<String, TrackingEvent> seen = new LinkedHashMap<>();
        for (TrackingEvent event : events) {
            if (event.description == null || event.description.trim().isEmpty()) continue;
            seen.putIfAbsent(event.eventKey, event);
        }
        events.clear();
        events.addAll(seen.values());
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String unescape(String value) {
        if (value == null) return "";
        return value.replace("\\n", " ").replace("\\r", " ").replace("\\t", " ")
                .replace("\\\"", "\"").replace("\\/", "/").replace("\\\\", "\\");
    }

    private static String clean(String value) {
        return value == null ? "" : Jsoup.parse(value).text().replaceAll("\\s+", " ").trim();
    }
}
