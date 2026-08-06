package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.util.CarrierDetector;
import com.voidnullvalue.parcelpal.util.StatusNormalizer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Cainiao's global tracking response into the common tracking model.
 *
 * <p>Cainiao answers for consignments issued by other brands, including the {@code 1ST} numbers
 * used by AliExpress shipments, and usually reports the origin-side scans that the issuing brand
 * omits. A number it does not know produces a module with an empty {@code detailList} rather than
 * an error, so the absence of detail is treated as "no record" instead of a failure to parse.
 */
final class CainiaoJsonParser {
    private static final Pattern LEADING_PLACE = Pattern.compile("^\\s*\\[([^\\]]{1,80})\\]\\s*");
    private static final int MAX_EVENTS = 150;

    TrackingResult parse(String payload, String trackingNumber, String sourceId, String sourceName) throws IOException {
        final JSONObject root;
        try {
            root = new JSONObject(payload);
        } catch (Exception malformed) {
            throw new IOException("Cainiao returned invalid JSON", malformed);
        }

        JSONArray modules = root.optJSONArray("module");
        if (modules == null || modules.length() == 0) {
            throw new IOException("Cainiao returned no tracking record");
        }

        String requested = CarrierDetector.normalizeTrackingNumber(trackingNumber);
        JSONObject module = null;
        for (int index = 0; index < modules.length(); index++) {
            JSONObject candidate = modules.optJSONObject(index);
            if (candidate == null) continue;
            String returned = CarrierDetector.normalizeTrackingNumber(candidate.optString("mailNo"));
            if (returned.equals(requested)) {
                module = candidate;
                break;
            }
        }
        if (module == null) throw new IOException("Cainiao returned a different tracking record");

        JSONArray details = module.optJSONArray("detailList");
        if (details == null || details.length() == 0) {
            throw new IOException("Cainiao has no tracking record for this number");
        }

        TrackingResult result = new TrackingResult();
        result.sourceId = sourceId;
        result.sourceName = sourceName;
        result.carrierName = "Cainiao";
        result.estimatedDelivery = estimatedDelivery(module);

        for (int index = 0; index < details.length() && result.events.size() < MAX_EVENTS; index++) {
            JSONObject detail = details.optJSONObject(index);
            if (detail == null) continue;

            String standard = text(detail, "standerdDesc");
            String raw = text(detail, "desc");
            String description = standard.isEmpty() ? raw : standard;
            if (description.isEmpty()) continue;

            String location = "";
            Matcher place = LEADING_PLACE.matcher(description);
            if (place.find()) {
                location = place.group(1).trim();
                description = description.substring(place.end()).trim();
            }
            if (description.isEmpty()) description = raw.isEmpty() ? standard : raw;
            if (description.isEmpty()) continue;

            TrackingEvent event = new TrackingEvent();
            event.trackingNumber = requested;
            event.carrierName = "Cainiao";
            event.sourceId = sourceId;
            event.sourceName = sourceName;
            event.eventTime = detail.optLong("time", 0);
            event.location = location;
            event.description = description;
            event.rawStatus = statusOf(detail);
            event.eventKey = TrackingEvent.makeKey(
                    event.trackingNumber,
                    event.eventTime,
                    event.location,
                    event.description
            );
            result.events.add(event);
        }

        if (result.events.isEmpty()) throw new IOException("Cainiao returned no usable tracking events");
        result.events.sort(Comparator.comparingLong((TrackingEvent event) -> event.eventTime).reversed());

        TrackingEvent newest = result.events.get(0);
        result.statusText = newest.description;
        result.normalizedStatus = StatusNormalizer.normalize(newest.description);
        if ("UNKNOWN".equals(result.normalizedStatus)) {
            result.normalizedStatus = normalizeCainiaoStatus(module.optString("status"));
        }
        if ("UNKNOWN".equals(result.normalizedStatus)) {
            result.normalizedStatus = StatusNormalizer.normalize(text(module, "statusDesc"));
        }
        if ("UNKNOWN".equals(result.normalizedStatus)) {
            result.normalizedStatus = normalizeCainiaoStatus(newest.rawStatus);
        }

        addLinked(result, text(module, "originalMailNo"), requested);
        addLinked(result, text(module, "externalMailNo"), requested);
        JSONArray related = module.optJSONArray("mailNoList");
        if (related != null) {
            for (int index = 0; index < related.length(); index++) {
                addLinked(result, related.optString(index, ""), requested);
            }
        }

        if (!result.isUseful()) throw new IOException("Cainiao returned no usable tracking state");
        return result;
    }

    /** Maps Cainiao's overall shipment codes, which describe the whole journey rather than one scan. */
    private static String normalizeCainiaoStatus(String raw) {
        if (raw == null) return "UNKNOWN";
        return switch (raw.trim().toUpperCase(Locale.US)) {
            case "SIGNIN", "DELIVERED", "SIGNED" -> "DELIVERED";
            case "DELIVERING", "TRANSPORT", "TRANSPORTING", "DEPART", "ARRIVAL" -> "IN_TRANSIT";
            case "SC_INBOUND_SUCCESS", "SC_OUTBOUND_SUCCESS", "CW_INBOUND", "CW_OUTBOUND",
                 "GWMS_OUTBOUND", "CW_SIGN_IN_SUCCESS" -> "IN_TRANSIT";
            case "PICKEDUP", "PICKUP", "COLLECTED" -> "IN_TRANSIT";
            case "WAIT_ACCEPT", "ACCEPT", "CONSIGN", "GWMS_ACCEPT", "GWMS_PACKAGE" -> "PRE_TRANSIT";
            case "CLEARANCE", "CUSTOMS", "CC_HO_IMPORT", "CC_HO_EXPORT" -> "CUSTOMS";
            case "WAIT_SELF_PICKUP", "SELF_PICKUP" -> "READY_FOR_PICKUP";
            case "FAILED", "EXCEPTION", "REJECT", "RETURNING", "RETURNED" -> "EXCEPTION";
            default -> "UNKNOWN";
        };
    }

    private static String statusOf(JSONObject detail) {
        String action = text(detail, "actionCode");
        if (!action.isEmpty()) return action;
        JSONObject group = detail.optJSONObject("group");
        return group == null ? "" : text(group, "nodeCode");
    }

    private static String estimatedDelivery(JSONObject module) {
        String direct = text(module, "estimateArrivalTime");
        if (!direct.isEmpty()) return direct;
        JSONObject process = module.optJSONObject("processInfo");
        if (process == null) return "";
        String estimate = text(process, "estimateTime");
        if (!estimate.isEmpty()) return estimate;
        return text(process, "estimatedDeliveryTime");
    }

    private static void addLinked(TrackingResult result, String raw, String original) {
        String candidate = CarrierDetector.normalizeTrackingNumber(raw);
        if (candidate.isEmpty() || candidate.equals(original)) return;
        if (!CarrierDetector.plausible(candidate)) return;
        if (!result.linkedTrackingNumbers.contains(candidate)) result.linkedTrackingNumbers.add(candidate);
    }

    private static String text(JSONObject object, String key) {
        if (object == null || object.isNull(key)) return "";
        String value = object.optString(key, "").trim();
        return "null".equalsIgnoreCase(value) ? "" : value;
    }
}
