package com.voidnullvalue.parcelpal.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.voidnullvalue.parcelpal.model.Shipment;
import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.model.TrackingLeg;
import com.voidnullvalue.parcelpal.util.CarrierDetector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class DatabaseBackup {
    private DatabaseBackup() {}

    static JSONObject exportJson(DatabaseHelper helper) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", "parcelpal-backup");
        root.put("version", 1);
        root.put("exportedAt", System.currentTimeMillis());
        JSONArray packages = new JSONArray();
        for (Shipment shipment : helper.listAllShipments()) {
            JSONObject item = shipmentToJson(shipment);
            JSONArray legs = new JSONArray();
            for (TrackingLeg leg : helper.listLegs(shipment.id)) legs.put(legToJson(leg));
            JSONArray events = new JSONArray();
            for (TrackingEvent event : helper.listEvents(shipment.id)) events.put(eventToJson(event));
            item.put("legs", legs);
            item.put("events", events);
            packages.put(item);
        }
        root.put("packages", packages);
        return root;
    }

    static DatabaseHelper.ImportSummary importJson(DatabaseHelper helper, JSONObject root) throws JSONException {
        if (!"parcelpal-backup".equals(root.optString("format"))) throw new JSONException("Not a ParcelPal backup");
        JSONArray packages = root.getJSONArray("packages");
        int inserted = 0;
        int updated = 0;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < packages.length(); i++) {
                JSONObject item = packages.getJSONObject(i);
                String tracking = CarrierDetector.normalizeTrackingNumber(item.getString("trackingNumber"));
                if (!CarrierDetector.plausible(tracking)) continue;
                Long id = findShipmentId(db, tracking);
                ContentValues shipmentValues = shipmentValuesFromJson(item, tracking);
                if (id == null) {
                    id = db.insertOrThrow("shipments", null, shipmentValues);
                    inserted++;
                } else {
                    db.update("shipments", shipmentValues, "id=?", ids(id));
                    updated++;
                }
                db.delete("tracking_legs", "shipment_id=?", ids(id));
                db.delete("tracking_events", "shipment_id=?", ids(id));
                JSONArray legs = item.optJSONArray("legs");
                if (legs != null) for (int l = 0; l < legs.length(); l++) insertLegJson(db, id, legs.getJSONObject(l));
                JSONArray events = item.optJSONArray("events");
                if (events != null) for (int e = 0; e < events.length(); e++) insertEventJson(db, id, events.getJSONObject(e));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return new DatabaseHelper.ImportSummary(inserted, updated);
    }

    private static JSONObject shipmentToJson(Shipment s) throws JSONException {
        return new JSONObject()
                .put("name", s.name).put("trackingNumber", s.trackingNumber).put("carrierHint", s.carrierHint)
                .put("normalizedStatus", s.normalizedStatus).put("statusText", s.statusText)
                .put("estimatedDelivery", s.estimatedDelivery).put("sourceId", s.sourceId)
                .put("sourceName", s.sourceName).put("lastError", s.lastError)
                .put("lastAttemptLog", s.lastAttemptLog).put("createdAt", s.createdAt)
                .put("updatedAt", s.updatedAt).put("lastCheckedAt", s.lastCheckedAt).put("archived", s.archived);
    }

    private static JSONObject legToJson(TrackingLeg l) throws JSONException {
        return new JSONObject()
                .put("trackingNumber", l.trackingNumber).put("carrierHint", l.carrierHint)
                .put("normalizedStatus", l.normalizedStatus).put("statusText", l.statusText)
                .put("estimatedDelivery", l.estimatedDelivery).put("sourceId", l.sourceId)
                .put("sourceName", l.sourceName).put("lastError", l.lastError)
                .put("discoveredAt", l.discoveredAt).put("lastCheckedAt", l.lastCheckedAt);
    }

    private static JSONObject eventToJson(TrackingEvent e) throws JSONException {
        return new JSONObject()
                .put("trackingNumber", e.trackingNumber).put("carrierName", e.carrierName)
                .put("sourceName", e.sourceName).put("eventTime", e.eventTime)
                .put("location", e.location).put("description", e.description)
                .put("rawStatus", e.rawStatus).put("eventKey", e.eventKey);
    }

    private static ContentValues shipmentValuesFromJson(JSONObject item, String tracking) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("name", item.optString("name"));
        values.put("tracking_number", tracking);
        values.put("carrier_hint", item.optString("carrierHint", CarrierDetector.detect(tracking)));
        values.put("normalized_status", item.optString("normalizedStatus", "UNKNOWN"));
        values.put("status_text", item.optString("statusText", "Not checked"));
        values.put("estimated_delivery", item.optString("estimatedDelivery"));
        values.put("source_id", item.optString("sourceId"));
        values.put("source_name", item.optString("sourceName"));
        values.put("last_error", item.optString("lastError"));
        values.put("last_attempt_log", item.optString("lastAttemptLog"));
        values.put("created_at", item.optLong("createdAt", now));
        values.put("updated_at", item.optLong("updatedAt", now));
        values.put("last_checked_at", item.optLong("lastCheckedAt", 0));
        values.put("archived", item.optBoolean("archived", false) ? 1 : 0);
        return values;
    }

    private static void insertLegJson(SQLiteDatabase db, long shipmentId, JSONObject item) {
        String tracking = CarrierDetector.normalizeTrackingNumber(item.optString("trackingNumber"));
        if (!CarrierDetector.plausible(tracking)) return;
        ContentValues values = new ContentValues();
        values.put("shipment_id", shipmentId);
        values.put("tracking_number", tracking);
        values.put("carrier_hint", item.optString("carrierHint", CarrierDetector.detect(tracking)));
        values.put("normalized_status", item.optString("normalizedStatus", "UNKNOWN"));
        values.put("status_text", item.optString("statusText", "Not checked"));
        values.put("estimated_delivery", item.optString("estimatedDelivery"));
        values.put("source_id", item.optString("sourceId"));
        values.put("source_name", item.optString("sourceName"));
        values.put("last_error", item.optString("lastError"));
        values.put("discovered_at", item.optLong("discoveredAt", System.currentTimeMillis()));
        values.put("last_checked_at", item.optLong("lastCheckedAt", 0));
        db.insertWithOnConflict("tracking_legs", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void insertEventJson(SQLiteDatabase db, long shipmentId, JSONObject item) {
        String tracking = CarrierDetector.normalizeTrackingNumber(item.optString("trackingNumber"));
        String description = item.optString("description");
        if (tracking.isEmpty() || description.isEmpty()) return;
        long time = item.optLong("eventTime", 0);
        String location = item.optString("location");
        ContentValues values = new ContentValues();
        values.put("shipment_id", shipmentId);
        values.put("tracking_number", tracking);
        values.put("carrier_name", item.optString("carrierName"));
        values.put("source_name", item.optString("sourceName"));
        values.put("event_time", time);
        values.put("location", location);
        values.put("description", description);
        values.put("raw_status", item.optString("rawStatus"));
        values.put("event_key", item.optString("eventKey", TrackingEvent.makeKey(tracking, time, location, description)));
        db.insertWithOnConflict("tracking_events", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static Long findShipmentId(SQLiteDatabase db, String tracking) {
        try (Cursor cursor = db.query("shipments", new String[]{"id"}, "tracking_number=?", new String[]{tracking}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
    }

    private static String[] ids(long id) { return new String[]{Long.toString(id)}; }
}
