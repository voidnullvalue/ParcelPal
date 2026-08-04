package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class ParcelsAppJsonParserTest {
    @Test public void parsesStructuredShipmentTimeline() throws Exception {
        String tracking = "9400111206213785678901";
        String linked = "GM123456789012345678";
        String json = "{" +
                "\"trackingId\":\"" + tracking + "\"," +
                "\"status\":\"delivered\"," +
                "\"estimatedDelivery\":\"2026-08-04\"," +
                "\"states\":[" +
                "{\"state\":\"Delivered, In/At Mailbox\",\"location\":\"Austin, TX\",\"date\":\"2026-08-04T18:42:00Z\",\"carrier\":0}," +
                "{\"state\":\"Out for Delivery\",\"location\":\"Austin, TX\",\"date\":\"2026-08-04T12:15:00Z\",\"carrier\":0}" +
                "]," +
                "\"services\":[{\"slug\":\"usps\",\"name\":\"USPS\"}]," +
                "\"detectedCarrier\":{\"slug\":\"usps\",\"name\":\"USPS\"}," +
                "\"externalTracking\":[" +
                "{\"trackingId\":\"" + tracking + "\"}," +
                "{\"trackingId\":\"" + linked + "\"}" +
                "]" +
                "}";

        TrackingResult result = new ParcelsAppJsonParser().parse(
                json,
                new TrackingTarget(tracking, "USPS"),
                "parcelsapp",
                "ParcelsApp"
        );

        assertTrue(result.isUseful());
        assertEquals("DELIVERED", result.normalizedStatus);
        assertEquals("Delivered, In/At Mailbox", result.statusText);
        assertEquals("USPS", result.carrierName);
        assertEquals("2026-08-04", result.estimatedDelivery);
        assertEquals(2, result.events.size());
        assertEquals("USPS", result.events.get(0).carrierName);
        assertEquals(1, result.linkedTrackingNumbers.size());
        assertEquals(linked, result.linkedTrackingNumbers.get(0));
    }

    @Test public void exposesStructuredProtocolErrors() throws Exception {
        try {
            new ParcelsAppJsonParser().parse(
                    "{\"error\":\"CAPTCHA\"}",
                    new TrackingTarget("9400111206213785678901", "USPS"),
                    "parcelsapp",
                    "ParcelsApp"
            );
            fail("Expected CAPTCHA error");
        } catch (ParcelsAppJsonParser.ResponseException error) {
            assertEquals("CAPTCHA", error.code);
            assertTrue(error.getMessage().contains("browser verification"));
        }
    }

    @Test public void rejectsJsonWithoutCredibleTrackingState() throws Exception {
        TrackingResult result = new ParcelsAppJsonParser().parse(
                "{\"status\":\"Delivery time\",\"states\":[]}",
                new TrackingTarget("9400111206213785678901", "USPS"),
                "parcelsapp",
                "ParcelsApp"
        );
        assertFalse(result.isUseful());
    }
}
