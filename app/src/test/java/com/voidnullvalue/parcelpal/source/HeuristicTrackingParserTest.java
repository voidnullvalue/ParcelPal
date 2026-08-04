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

    @Test public void rejectsDeliveryTimeLabelAndScriptTokens() {
        String tracking = "92055902673388000068080083";
        String html = "<html><body><div>" + tracking + "</div>" +
                "<div class=\"tracking-card\"><h2>Delivery time</h2></div>" +
                "<script>{\"status\":\"Delivery time\",\"cacheKey\":\"VMKE5P1MJ23EPNLT7FXLZMLHL6DKG\"," +
                "\"requestId\":\"UNVLH8BDGTNVVHPPI3P1VCGVMBDSAC6JL99GRT7W\"}</script>" +
                "<p>Example: RA123456789CN</p></body></html>";
        TrackingResult result = new HeuristicTrackingParser().parse(html, tracking, "parcelsapp", "ParcelsApp");
        assertFalse(result.isUseful());
        assertTrue(result.events.isEmpty());
        assertTrue(result.linkedTrackingNumbers.isEmpty());
    }

    @Test public void extractsOnlyContextualLinkedTrackingNumbers() {
        String tracking = "GM123456789012345678";
        String linked = "9400111206213785678901";
        String html = "<html><body><div>" + tracking + "</div>" +
                "<div data-status=\"true\">In transit to destination</div>" +
                "<div>USPS tracking number: " + linked + "</div>" +
                "<div>Example international number RA123456789CN</div>" +
                "<script>var token='VMKE5P1MJ23EPNLT7FXLZMLHL6DKG';</script></body></html>";
        TrackingResult result = new HeuristicTrackingParser().parse(html, tracking, "test", "Test");
        assertTrue(result.isUseful());
        assertEquals(1, result.linkedTrackingNumbers.size());
        assertEquals(linked, result.linkedTrackingNumbers.get(0));
    }
}
