package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PackyJsonParserTest {
    private static final String FIXTURE = """
            {
              "trackCode":"1ST06003441583",
              "status":"delivered",
              "events":[
                {
                  "status":"delivered",
                  "eventDate":"2024-12-30T12:40:00.000000Z",
                  "description":"Delivered, Front Door/Porch",
                  "carrierCode":"1st",
                  "place":"SUMTER SC 29153",
                  "carrier":{"code":"1st","name":"1ST"}
                },
                {
                  "status":"transit",
                  "eventDate":"2024-12-30T06:10:00.000000Z",
                  "description":"Out for Delivery, Expected Delivery by 9:00pm",
                  "carrierCode":"1st",
                  "place":"SUMTER SC 29153",
                  "carrier":{"code":"1st","name":"1ST"}
                }
              ]
            }
            """;

    @Test public void parsesStructured1stTimeline() throws Exception {
        TrackingResult result = new PackyJsonParser().parse(
                FIXTURE,
                "1ST06003441583",
                "packy_1st",
                "Packy"
        );

        assertTrue(result.isUseful());
        assertEquals("DELIVERED", result.normalizedStatus);
        assertEquals("Delivered, Front Door/Porch", result.statusText);
        assertEquals("1ST", result.carrierName);
        assertEquals("Packy", result.sourceName);
        assertEquals(2, result.events.size());
        assertEquals("SUMTER SC 29153", result.events.get(0).location);
        assertTrue(result.events.get(0).eventTime > result.events.get(1).eventTime);
    }

    @Test(expected = IOException.class)
    public void rejectsAResponseForAnotherTrackingNumber() throws Exception {
        new PackyJsonParser().parse(FIXTURE, "1ST06000000000", "packy_1st", "Packy");
    }

    @Test(expected = IOException.class)
    public void rejectsEmptyTrackingData() throws Exception {
        new PackyJsonParser().parse(
                "{\"trackCode\":\"1ST06003441583\",\"status\":\"transit\",\"events\":[]}",
                "1ST06003441583",
                "packy_1st",
                "Packy"
        );
    }
}
