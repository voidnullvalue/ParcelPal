package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class UspsDomParserTest {
    private final UspsDomParser parser = new UspsDomParser();
    private final TrackingTarget target = new TrackingTarget("9400111899560000000000", "USPS");

    @Test public void parsesUspsTimelineAndDeliveryEstimate() throws Exception {
        String json = "{" +
                "\"version\":1," +
                "\"ready\":true," +
                "\"challenge\":false," +
                "\"status\":\"Moving Through Network\"," +
                "\"banners\":[\"Expected Delivery by Tuesday, August 5, 2026\"]," +
                "\"steps\":[" +
                "\"Moving Through Network\\nIn Transit to Next Facility\\nAugust 4, 2026, 1:42 pm\"," +
                "\"Arrived at USPS Regional Facility\\nDALLAS TX DISTRIBUTION CENTER\\nAugust 3, 2026, 9:18 pm\"" +
                "]," +
                "\"details\":[]" +
                "}";

        TrackingResult result = parser.parse(json, target, "usps", "USPS");

        assertEquals("USPS", result.carrierName);
        assertEquals("IN_TRANSIT", result.normalizedStatus);
        assertTrue(result.statusText.contains("Moving Through Network"));
        assertTrue(result.estimatedDelivery.contains("Expected Delivery"));
        assertEquals(2, result.events.size());
        assertTrue(result.events.get(0).eventTime > result.events.get(1).eventTime);
        assertEquals("DALLAS TX DISTRIBUTION CENTER", result.events.get(1).location);
        assertFalse(result.events.get(0).eventKey.isEmpty());
    }

    @Test public void parsesDeliveredStatusWithoutHistory() throws Exception {
        String json = "{" +
                "\"ready\":true," +
                "\"challenge\":false," +
                "\"status\":\"Delivered\"," +
                "\"steps\":[]," +
                "\"details\":[]," +
                "\"banners\":[]" +
                "}";

        TrackingResult result = parser.parse(json, target, "usps", "USPS");

        assertEquals("DELIVERED", result.normalizedStatus);
        assertEquals(1, result.events.size());
        assertEquals("Delivered", result.events.get(0).description);
    }

    @Test public void rejectsInteractiveChallenge() throws Exception {
        try {
            parser.parse("{\"ready\":false,\"challenge\":true}", target, "usps", "USPS");
            fail("Expected challenge rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("verification challenge"));
        }
    }

    @Test public void rejectsEmptyExtraction() throws Exception {
        try {
            parser.parse("{\"ready\":true,\"challenge\":false,\"steps\":[],\"details\":[]}",
                    target, "usps", "USPS");
            fail("Expected empty result rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no usable tracking status"));
        }
    }
}
