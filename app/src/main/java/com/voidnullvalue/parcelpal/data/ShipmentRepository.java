package com.voidnullvalue.parcelpal.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.voidnullvalue.parcelpal.model.Shipment;
import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.model.TrackingLeg;
import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;
import com.voidnullvalue.parcelpal.source.SourceRegistry;
import com.voidnullvalue.parcelpal.source.TrackingSource;
import com.voidnullvalue.parcelpal.util.CarrierDetector;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ShipmentRepository {
    private static final int MAX_LEGS_PER_REFRESH = 6;

    private final DatabaseHelper database;
    private final SourceRegistry sourceRegistry;

    public ShipmentRepository(Context context) {
        Context app = context.getApplicationContext();
        database = new DatabaseHelper(app);
        sourceRegistry = new SourceRegistry(app, new SourcePreferences(app));
    }

    public long addShipment(String name, String trackingNumber, String carrierHint) {
        String normalizedNumber = CarrierDetector.normalizeTrackingNumber(trackingNumber);
        if (!CarrierDetector.plausible(normalizedNumber)) throw new IllegalArgumentException("Enter a plausible tracking number");
        String carrier = normalizedCarrier(normalizedNumber, carrierHint);
        Shipment existing = database.findShipmentByTracking(normalizedNumber);
        if (existing != null) {
            if (name != null && !name.trim().isEmpty()) database.updateShipmentName(existing.id, name);
            database.updateShipmentCarrier(existing.id, carrier);
            database.setArchived(existing.id, false);
            return existing.id;
        }
        return database.insertShipment(name, normalizedNumber, carrier);
    }

    public List<Shipment> listShipments(boolean archived) { return database.listShipments(archived); }
    public List<Shipment> listActiveShipments() { return database.listShipments(false); }
    public Shipment getShipment(long id) { return database.getShipment(id); }
    public List<TrackingEvent> listEvents(long shipmentId) { return database.listEvents(shipmentId); }
    public List<TrackingLeg> listLegs(long shipmentId) { return database.listLegs(shipmentId); }
    public void deleteShipment(long id) { database.deleteShipment(id); }
    public void renameShipment(long id, String name) { database.updateShipmentName(id, name); }
    public void changeCarrier(long id, String carrier) { database.updateShipmentCarrier(id, carrier); }
    public void setArchived(long id, boolean archived) { database.setArchived(id, archived); }
    public JSONObject exportJson() throws JSONException { return database.exportJson(); }
    public DatabaseHelper.ImportSummary importJson(JSONObject json) throws JSONException { return database.importJson(json); }

    public RefreshOutcome refresh(long shipmentId) {
        Shipment shipment = database.getShipment(shipmentId);
        if (shipment == null) return RefreshOutcome.failure("Package no longer exists");

        purgeLegacyFalsePositives(shipmentId);
        shipment = database.getShipment(shipmentId);
        if (shipment == null) return RefreshOutcome.failure("Package no longer exists");

        String previousStatus = shipment.statusText;
        String previousNormalized = shipment.normalizedStatus;
        List<String> attempts = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<TargetSuccess> successes = new ArrayList<>();

        String rootCarrier = normalizedCarrier(shipment.trackingNumber, shipment.carrierHint);
        if (isAutoCarrier(shipment.carrierHint) && !CarrierDetector.AUTO_DETECT.equalsIgnoreCase(rootCarrier)) {
            database.updateShipmentCarrier(shipmentId, rootCarrier);
        }
        TrackingTarget rootTarget = new TrackingTarget(shipment.trackingNumber, rootCarrier);
        TargetFetch rootFetch = fetchTarget(rootTarget, attempts, errors);
        if (rootFetch.result != null) {
            TrackingResult result = rootFetch.result;
            database.replaceEventsForTracking(shipmentId, rootTarget.trackingNumber,
                    safeCarrier(result.carrierName, rootTarget.carrierHint), result.sourceName, result.events);
            database.upsertDiscoveredLegs(shipmentId, shipment.trackingNumber, result.linkedTrackingNumbers);
            successes.add(new TargetSuccess(rootTarget.trackingNumber, result));
        }

        List<TrackingLeg> legs = database.listLegs(shipmentId);
        int processed = 0;
        for (TrackingLeg leg : legs) {
            if (processed++ >= MAX_LEGS_PER_REFRESH) break;
            TrackingTarget target = new TrackingTarget(leg.trackingNumber,
                    normalizedCarrier(leg.trackingNumber, leg.carrierHint));
            TargetFetch fetch = fetchTarget(target, attempts, errors);
            if (fetch.result == null) {
                database.updateLegError(leg.id, fetch.error);
                continue;
            }
            TrackingResult result = fetch.result;
            database.updateLegResult(leg.id, result.normalizedStatus, result.statusText,
                    result.estimatedDelivery, result.sourceId, result.sourceName, "");
            database.replaceEventsForTracking(shipmentId, leg.trackingNumber,
                    safeCarrier(result.carrierName, target.carrierHint), result.sourceName, result.events);
            successes.add(new TargetSuccess(leg.trackingNumber, result));
        }

        if (successes.isEmpty()) {
            String error = errors.isEmpty() ? "No enabled source returned usable tracking data" : String.join("\n", errors);
            database.updateShipmentError(shipmentId, error, String.join("\n", attempts));
            return RefreshOutcome.failure(error);
        }

        TargetSuccess current = chooseCurrent(successes);
        TrackingResult result = current.result;
        String sourceName = result.sourceName;
        if (!current.trackingNumber.equals(shipment.trackingNumber)) sourceName += " · linked leg";
        database.updateShipmentResult(shipmentId, result.normalizedStatus, result.statusText,
                result.estimatedDelivery, result.sourceId, sourceName,
                errors.isEmpty() ? "" : String.join("\n", errors), String.join("\n", attempts));

        boolean changed = !safe(previousStatus).equals(safe(result.statusText)) ||
                !safe(previousNormalized).equals(safe(result.normalizedStatus));
        return RefreshOutcome.success(result, changed, errors);
    }

    private void purgeLegacyFalsePositives(long shipmentId) {
        SQLiteDatabase db = database.getWritableDatabase();
        List<String> staleLegs = new ArrayList<>();
        db.beginTransaction();
        try {
            try (Cursor cursor = db.query("tracking_legs", new String[]{"tracking_number"},
                    "shipment_id=? AND normalized_status='UNKNOWN'",
                    new String[]{Long.toString(shipmentId)}, null, null, null)) {
                while (cursor.moveToNext()) staleLegs.add(cursor.getString(0));
            }

            for (String trackingNumber : staleLegs) {
                db.delete("tracking_events", "shipment_id=? AND tracking_number=?",
                        new String[]{Long.toString(shipmentId), trackingNumber});
            }
            db.delete("tracking_legs", "shipment_id=? AND normalized_status='UNKNOWN'",
                    new String[]{Long.toString(shipmentId)});

            String genericLabels = "'delivery time','estimated delivery','expected delivery','delivery date'," +
                    "'shipment tracking','tracking details','tracking information','package status'," +
                    "'shipment status','delivery status','status'";
            db.execSQL("DELETE FROM tracking_events WHERE shipment_id=? AND lower(trim(description)) IN (" + genericLabels + ")",
                    new Object[]{shipmentId});
            db.execSQL("UPDATE shipments SET normalized_status='UNKNOWN', status_text='Unknown', " +
                            "estimated_delivery='', source_id='', source_name='' " +
                            "WHERE id=? AND lower(trim(status_text)) IN (" + genericLabels + ")",
                    new Object[]{shipmentId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private TargetFetch fetchTarget(TrackingTarget target, List<String> attempts, List<String> errors) {
        List<TrackingSource> sources = sourceRegistry.sourcesFor(target);
        if (sources.isEmpty()) {
            String message = "No enabled source supports " + target.carrierHint;
            errors.add(message);
            return TargetFetch.failure(message);
        }
        List<String> targetErrors = new ArrayList<>();
        for (TrackingSource source : sources) {
            String hosts = String.join(", ", source.allowedHosts());
            try {
                TrackingResult result = source.fetch(target);
                attempts.add(source.displayName() + " [" + hosts + "] -> success");
                return TargetFetch.success(result);
            } catch (IOException | RuntimeException e) {
                String message = safeMessage(e);
                attempts.add(source.displayName() + " [" + hosts + "] -> failed: " + message);
                targetErrors.add(source.displayName() + ": " + message);
            }
        }
        String error = target.trackingNumber + ": " + String.join("; ", targetErrors);
        errors.add(error);
        return TargetFetch.failure(error);
    }

    private static TargetSuccess chooseCurrent(List<TargetSuccess> successes) {
        TargetSuccess chosen = successes.get(0);
        long newest = chosen.result.newestEventTime();
        for (int i = 1; i < successes.size(); i++) {
            TargetSuccess candidate = successes.get(i);
            long candidateTime = candidate.result.newestEventTime();
            if (candidateTime > newest || candidateTime == newest) {
                chosen = candidate;
                newest = candidateTime;
            }
        }
        return chosen;
    }

    private static String normalizedCarrier(String trackingNumber, String requested) {
        if (isAutoCarrier(requested)) return CarrierDetector.detect(trackingNumber);
        return requested.trim();
    }

    private static boolean isAutoCarrier(String carrier) {
        return carrier == null || carrier.trim().isEmpty() ||
                CarrierDetector.AUTO_DETECT.equalsIgnoreCase(carrier.trim());
    }

    private static String safeCarrier(String parsed, String fallback) {
        return parsed == null || parsed.trim().isEmpty() || "Auto-detect".equalsIgnoreCase(parsed) ? fallback : parsed;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static final class TargetFetch {
        final TrackingResult result;
        final String error;
        private TargetFetch(TrackingResult result, String error) { this.result = result; this.error = error; }
        static TargetFetch success(TrackingResult result) { return new TargetFetch(result, ""); }
        static TargetFetch failure(String error) { return new TargetFetch(null, error); }
    }

    private static final class TargetSuccess {
        final String trackingNumber;
        final TrackingResult result;
        TargetSuccess(String trackingNumber, TrackingResult result) {
            this.trackingNumber = trackingNumber;
            this.result = result;
        }
    }

    public static final class RefreshOutcome {
        public final boolean success;
        public final TrackingResult result;
        public final String error;
        public final boolean statusChanged;
        public final List<String> warnings;

        private RefreshOutcome(boolean success, TrackingResult result, String error, boolean statusChanged, List<String> warnings) {
            this.success = success;
            this.result = result;
            this.error = error;
            this.statusChanged = statusChanged;
            this.warnings = warnings;
        }

        public static RefreshOutcome success(TrackingResult result, boolean statusChanged, List<String> warnings) {
            return new RefreshOutcome(true, result, "", statusChanged, new ArrayList<>(warnings));
        }

        public static RefreshOutcome failure(String error) {
            return new RefreshOutcome(false, null, error, false, new ArrayList<>());
        }
    }
}
