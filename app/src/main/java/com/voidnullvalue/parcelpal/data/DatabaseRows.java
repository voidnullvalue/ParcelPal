package com.voidnullvalue.parcelpal.data;

import android.database.Cursor;

import com.voidnullvalue.parcelpal.model.Shipment;
import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.model.TrackingLeg;

final class DatabaseRows {
    private DatabaseRows() {}

    static Shipment readShipment(Cursor c) {
        Shipment s = new Shipment();
        s.id = longValue(c, "id");
        s.name = stringValue(c, "name");
        s.trackingNumber = stringValue(c, "tracking_number");
        s.carrierHint = stringValue(c, "carrier_hint");
        s.normalizedStatus = stringValue(c, "normalized_status");
        s.statusText = stringValue(c, "status_text");
        s.estimatedDelivery = stringValue(c, "estimated_delivery");
        s.sourceId = stringValue(c, "source_id");
        s.sourceName = stringValue(c, "source_name");
        s.lastError = stringValue(c, "last_error");
        s.lastAttemptLog = stringValue(c, "last_attempt_log");
        s.createdAt = longValue(c, "created_at");
        s.updatedAt = longValue(c, "updated_at");
        s.lastCheckedAt = longValue(c, "last_checked_at");
        s.archived = intValue(c, "archived") != 0;
        return s;
    }

    static TrackingLeg readLeg(Cursor c) {
        TrackingLeg l = new TrackingLeg();
        l.id = longValue(c, "id");
        l.shipmentId = longValue(c, "shipment_id");
        l.trackingNumber = stringValue(c, "tracking_number");
        l.carrierHint = stringValue(c, "carrier_hint");
        l.normalizedStatus = stringValue(c, "normalized_status");
        l.statusText = stringValue(c, "status_text");
        l.estimatedDelivery = stringValue(c, "estimated_delivery");
        l.sourceId = stringValue(c, "source_id");
        l.sourceName = stringValue(c, "source_name");
        l.lastError = stringValue(c, "last_error");
        l.discoveredAt = longValue(c, "discovered_at");
        l.lastCheckedAt = longValue(c, "last_checked_at");
        return l;
    }

    static TrackingEvent readEvent(Cursor c) {
        TrackingEvent e = new TrackingEvent();
        e.id = longValue(c, "id");
        e.shipmentId = longValue(c, "shipment_id");
        e.trackingNumber = stringValue(c, "tracking_number");
        e.carrierName = stringValue(c, "carrier_name");
        e.sourceName = stringValue(c, "source_name");
        e.eventTime = longValue(c, "event_time");
        e.location = stringValue(c, "location");
        e.description = stringValue(c, "description");
        e.rawStatus = stringValue(c, "raw_status");
        e.eventKey = stringValue(c, "event_key");
        return e;
    }

    private static int index(Cursor c, String name) { return c.getColumnIndexOrThrow(name); }
    private static String stringValue(Cursor c, String name) {
        String value = c.getString(index(c, name));
        return value == null ? "" : value;
    }
    private static long longValue(Cursor c, String name) { return c.getLong(index(c, name)); }
    private static int intValue(Cursor c, String name) { return c.getInt(index(c, name)); }
}
