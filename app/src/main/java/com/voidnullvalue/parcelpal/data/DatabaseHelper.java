package com.voidnullvalue.parcelpal.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.voidnullvalue.parcelpal.model.Shipment;
import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.model.TrackingLeg;
import com.voidnullvalue.parcelpal.util.CarrierDetector;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "parcel_pal.db";
    private static final int DB_VERSION = 2;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createShipments(db);
        createLegs(db);
        createEvents(db);
    }

    private static void createShipments(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE shipments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL DEFAULT ''," +
                "tracking_number TEXT NOT NULL UNIQUE," +
                "carrier_hint TEXT NOT NULL DEFAULT 'Auto-detect'," +
                "normalized_status TEXT NOT NULL DEFAULT 'UNKNOWN'," +
                "status_text TEXT NOT NULL DEFAULT 'Not checked'," +
                "estimated_delivery TEXT NOT NULL DEFAULT ''," +
                "source_id TEXT NOT NULL DEFAULT ''," +
                "source_name TEXT NOT NULL DEFAULT ''," +
                "last_error TEXT NOT NULL DEFAULT ''," +
                "last_attempt_log TEXT NOT NULL DEFAULT ''," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "last_checked_at INTEGER NOT NULL DEFAULT 0," +
                "archived INTEGER NOT NULL DEFAULT 0" +
                ")");
        db.execSQL("CREATE INDEX idx_shipments_archived_updated ON shipments(archived, updated_at DESC)");
    }

    private static void createLegs(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracking_legs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "shipment_id INTEGER NOT NULL," +
                "tracking_number TEXT NOT NULL," +
                "carrier_hint TEXT NOT NULL DEFAULT 'Auto-detect'," +
                "normalized_status TEXT NOT NULL DEFAULT 'UNKNOWN'," +
                "status_text TEXT NOT NULL DEFAULT 'Not checked'," +
                "estimated_delivery TEXT NOT NULL DEFAULT ''," +
                "source_id TEXT NOT NULL DEFAULT ''," +
                "source_name TEXT NOT NULL DEFAULT ''," +
                "last_error TEXT NOT NULL DEFAULT ''," +
                "discovered_at INTEGER NOT NULL," +
                "last_checked_at INTEGER NOT NULL DEFAULT 0," +
                "UNIQUE(shipment_id, tracking_number)," +
                "FOREIGN KEY(shipment_id) REFERENCES shipments(id) ON DELETE CASCADE" +
                ")");
        db.execSQL("CREATE INDEX idx_legs_shipment ON tracking_legs(shipment_id, discovered_at)");
    }

    private static void createEvents(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracking_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "shipment_id INTEGER NOT NULL," +
                "tracking_number TEXT NOT NULL DEFAULT ''," +
                "carrier_name TEXT NOT NULL DEFAULT ''," +
                "source_name TEXT NOT NULL DEFAULT ''," +
                "event_time INTEGER NOT NULL DEFAULT 0," +
                "location TEXT NOT NULL DEFAULT ''," +
                "description TEXT NOT NULL," +
                "raw_status TEXT NOT NULL DEFAULT ''," +
                "event_key TEXT NOT NULL," +
                "UNIQUE(shipment_id, event_key)," +
                "FOREIGN KEY(shipment_id) REFERENCES shipments(id) ON DELETE CASCADE" +
                ")");
        db.execSQL("CREATE INDEX idx_events_shipment_time ON tracking_events(shipment_id, event_time DESC, id DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE shipments ADD COLUMN estimated_delivery TEXT NOT NULL DEFAULT ''");
            createLegs(db);
            db.execSQL("ALTER TABLE tracking_events ADD COLUMN tracking_number TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE tracking_events ADD COLUMN carrier_name TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE tracking_events ADD COLUMN source_name TEXT NOT NULL DEFAULT ''");
            db.execSQL("UPDATE tracking_events SET tracking_number=(SELECT tracking_number FROM shipments WHERE shipments.id=tracking_events.shipment_id)");
        }
    }

    public synchronized long insertShipment(String name, String trackingNumber, String carrierHint) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("name", safe(name));
        values.put("tracking_number", trackingNumber);
        values.put("carrier_hint", safeOr(carrierHint, "Auto-detect"));
        values.put("created_at", now);
        values.put("updated_at", now);
        return getWritableDatabase().insertOrThrow("shipments", null, values);
    }

    public synchronized List<Shipment> listShipments(boolean archived) {
        List<Shipment> result = new ArrayList<>();
        String order = "CASE normalized_status " +
                "WHEN 'EXCEPTION' THEN 0 WHEN 'OUT_FOR_DELIVERY' THEN 1 WHEN 'READY_FOR_PICKUP' THEN 2 " +
                "WHEN 'ATTEMPTED' THEN 3 WHEN 'IN_TRANSIT' THEN 4 WHEN 'CUSTOMS' THEN 5 " +
                "WHEN 'PRE_TRANSIT' THEN 6 WHEN 'DELIVERED' THEN 7 ELSE 8 END, updated_at DESC";
        try (Cursor cursor = getReadableDatabase().query("shipments", null, "archived=?",
                new String[]{archived ? "1" : "0"}, null, null, order)) {
            while (cursor.moveToNext()) result.add(DatabaseRows.readShipment(cursor));
        }
        return result;
    }

    public synchronized List<Shipment> listAllShipments() {
        List<Shipment> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("shipments", null, null, null, null, null, "created_at ASC")) {
            while (cursor.moveToNext()) result.add(DatabaseRows.readShipment(cursor));
        }
        return result;
    }

    public synchronized Shipment getShipment(long id) {
        try (Cursor cursor = getReadableDatabase().query("shipments", null, "id=?",
                new String[]{Long.toString(id)}, null, null, null)) {
            return cursor.moveToFirst() ? DatabaseRows.readShipment(cursor) : null;
        }
    }

    public synchronized Shipment findShipmentByTracking(String trackingNumber) {
        try (Cursor cursor = getReadableDatabase().query("shipments", null, "tracking_number=?",
                new String[]{trackingNumber}, null, null, null)) {
            return cursor.moveToFirst() ? DatabaseRows.readShipment(cursor) : null;
        }
    }

    public synchronized void updateShipmentName(long id, String name) {
        ContentValues values = new ContentValues();
        values.put("name", safe(name));
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("shipments", values, "id=?", ids(id));
    }

    public synchronized void updateShipmentCarrier(long id, String carrier) {
        ContentValues values = new ContentValues();
        values.put("carrier_hint", safeOr(carrier, "Auto-detect"));
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("shipments", values, "id=?", ids(id));
    }

    public synchronized void setArchived(long id, boolean archived) {
        ContentValues values = new ContentValues();
        values.put("archived", archived ? 1 : 0);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("shipments", values, "id=?", ids(id));
    }

    public synchronized void updateShipmentResult(long id, String normalizedStatus, String statusText,
                                                   String estimatedDelivery, String sourceId, String sourceName,
                                                   String error, String attemptLog) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("normalized_status", safeOr(normalizedStatus, "UNKNOWN"));
        values.put("status_text", safeOr(statusText, "Unknown"));
        values.put("estimated_delivery", safe(estimatedDelivery));
        values.put("source_id", safe(sourceId));
        values.put("source_name", safe(sourceName));
        values.put("last_error", safe(error));
        values.put("last_attempt_log", safe(attemptLog));
        values.put("last_checked_at", now);
        values.put("updated_at", now);
        getWritableDatabase().update("shipments", values, "id=?", ids(id));
    }

    public synchronized void updateShipmentError(long id, String error, String attemptLog) {
        ContentValues values = new ContentValues();
        values.put("last_error", safe(error));
        values.put("last_attempt_log", safe(attemptLog));
        values.put("last_checked_at", System.currentTimeMillis());
        getWritableDatabase().update("shipments", values, "id=?", ids(id));
    }

    public synchronized void upsertDiscoveredLegs(long shipmentId, String rootTracking, List<String> trackingNumbers) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            for (String raw : trackingNumbers) {
                String number = CarrierDetector.normalizeTrackingNumber(raw);
                if (!CarrierDetector.plausible(number) || number.equals(rootTracking)) continue;
                ContentValues values = new ContentValues();
                values.put("shipment_id", shipmentId);
                values.put("tracking_number", number);
                values.put("carrier_hint", CarrierDetector.detect(number));
                values.put("discovered_at", now);
                db.insertWithOnConflict("tracking_legs", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized List<TrackingLeg> listLegs(long shipmentId) {
        List<TrackingLeg> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("tracking_legs", null, "shipment_id=?",
                ids(shipmentId), null, null, "discovered_at ASC, id ASC")) {
            while (cursor.moveToNext()) result.add(DatabaseRows.readLeg(cursor));
        }
        return result;
    }

    public synchronized void updateLegResult(long legId, String normalizedStatus, String statusText,
                                              String estimatedDelivery, String sourceId, String sourceName, String error) {
        ContentValues values = new ContentValues();
        values.put("normalized_status", safeOr(normalizedStatus, "UNKNOWN"));
        values.put("status_text", safeOr(statusText, "Unknown"));
        values.put("estimated_delivery", safe(estimatedDelivery));
        values.put("source_id", safe(sourceId));
        values.put("source_name", safe(sourceName));
        values.put("last_error", safe(error));
        values.put("last_checked_at", System.currentTimeMillis());
        getWritableDatabase().update("tracking_legs", values, "id=?", ids(legId));
    }

    public synchronized void updateLegError(long legId, String error) {
        ContentValues values = new ContentValues();
        values.put("last_error", safe(error));
        values.put("last_checked_at", System.currentTimeMillis());
        getWritableDatabase().update("tracking_legs", values, "id=?", ids(legId));
    }

    public synchronized void replaceEventsForTracking(long shipmentId, String trackingNumber, String carrierName,
                                                       String sourceName, List<TrackingEvent> events) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("tracking_events", "shipment_id=? AND tracking_number=?",
                    new String[]{Long.toString(shipmentId), trackingNumber});
            for (TrackingEvent event : events) {
                ContentValues values = new ContentValues();
                values.put("shipment_id", shipmentId);
                values.put("tracking_number", trackingNumber);
                values.put("carrier_name", safeOr(event.carrierName, carrierName));
                values.put("source_name", safeOr(event.sourceName, sourceName));
                values.put("event_time", event.eventTime);
                values.put("location", safe(event.location));
                values.put("description", safeOr(event.description, "Tracking update"));
                values.put("raw_status", safe(event.rawStatus));
                values.put("event_key", safeOr(event.eventKey,
                        TrackingEvent.makeKey(trackingNumber, event.eventTime, event.location, event.description)));
                db.insertWithOnConflict("tracking_events", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized List<TrackingEvent> listEvents(long shipmentId) {
        List<TrackingEvent> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("tracking_events", null, "shipment_id=?",
                ids(shipmentId), null, null, "CASE WHEN event_time=0 THEN 1 ELSE 0 END, event_time DESC, id DESC")) {
            while (cursor.moveToNext()) result.add(DatabaseRows.readEvent(cursor));
        }
        return result;
    }

    public synchronized void deleteShipment(long id) {
        getWritableDatabase().delete("shipments", "id=?", ids(id));
    }

    public synchronized JSONObject exportJson() throws JSONException {
        return DatabaseBackup.exportJson(this);
    }

    public synchronized ImportSummary importJson(JSONObject root) throws JSONException {
        return DatabaseBackup.importJson(this, root);
    }

    private static String[] ids(long id) { return new String[]{Long.toString(id)}; }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String safeOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public static final class ImportSummary {
        public final int inserted;
        public final int updated;
        public ImportSummary(int inserted, int updated) { this.inserted = inserted; this.updated = updated; }
    }
}
