package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HeuristicTrackingParserTest {
    @Test public void parsesEmbeddedJsonEventsAndLinkedLeg() {
        String tracking = "GM123456789012345678";
        String html = "<html><head><title>DHL tracking</title></head><body>" + tracking +
                "<script>{\"trackingNumber\":\"" + tracking + "\",\"events\":[" +
                "{\"date\":\"2026-08-04T12:00:00Z\",\"status\":\"In transit to destination\",\"location\":\"Dallas, TX\"}," +
                "{\"date\":\"2026-08-03T12:00:00Z\",\"status\":\"Shipment accepted\",\"location\":\"Houston, TX\"}]," +
                "\"finalMileTracking\":\"9400111206213785678901\"}</script></body></html>";
        TrackingResult result = new HeuristicTrackingParser().parse(html, tracking, "test", "Test Source");
        assertTrue(result.isUseful());
        assertEquals("IN_TRANSIT", result.normalizedStatus);
        assertFalse(result.events.isEmpty());
        assertTrue(result.linkedTrackingNumbers.contains("9400111206213785678901"));
    }

    @Test public void rejectsBoilerplateWithoutRequestedTrackingNumber() {
        String html = "<html><body><h1>Track any package</h1><p>Delivered packages are easy to find.</p></body></html>";
        TrackingResult result = new HeuristicTrackingParser().parse(html, "1Z999AA10123456784", "test", "Test");
        assertFalse(result.isUseful());
    }
}
