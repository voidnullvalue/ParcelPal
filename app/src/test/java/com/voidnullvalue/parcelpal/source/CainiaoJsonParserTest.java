package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingEvent;
import com.voidnullvalue.parcelpal.model.TrackingResult;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Fixtures captured from the live {@code global.cainiao.com} tracking endpoint. Cainiao holds the
 * origin-side scans for {@code 1ST} consignments that the issuing carrier does not publish, so this
 * parser is what makes those scans visible.
 */
public final class CainiaoJsonParserTest {
    private static final String DELIVERING = "{\"module\":[{"
            + "\"mailNo\":\"1ST06013631493\",\"originCountry\":\"Mainland China\",\"destCountry\":\"USA\","
            + "\"status\":\"DELIVERING\",\"statusDesc\":\"Delivering\",\"mailNoSource\":\"AE\","
            + "\"detailList\":["
            + "{\"time\":1785953580000,\"desc\":\"Outbound in sorting center\","
            + "\"standerdDesc\":\"[Mayong Town] Departed from sorting center\",\"timeZone\":\"GMT+8\","
            + "\"actionCode\":\"SC_OUTBOUND_SUCCESS\"},"
            + "{\"time\":1785915266000,\"desc\":\"Inbound in sorting center\","
            + "\"standerdDesc\":\"[Mayong Town] Processing at sorting center\",\"timeZone\":\"GMT+8\","
            + "\"actionCode\":\"SC_INBOUND_SUCCESS\"},"
            + "{\"time\":1785838943115,\"desc\":\"Order received successfully\","
            + "\"standerdDesc\":\"Shipment information received by warehouse electronically\","
            + "\"timeZone\":\"GMT+0800\",\"actionCode\":\"GWMS_ACCEPT\"}"
            + "]}],\"success\":true}";

    /** Cainiao answers for unknown numbers with a module that simply has no detail. */
    private static final String UNKNOWN_NUMBER =
            "{\"module\":[{\"mailNo\":\"9400111899223197428490\",\"mailNoSource\":\"EXTERNAL\","
                    + "\"detailList\":[]}],\"success\":true}";

    private final CainiaoJsonParser parser = new CainiaoJsonParser();

    @Test public void readsEveryScanNewestFirst() throws IOException {
        TrackingResult result = parse(DELIVERING);

        assertEquals(3, result.events.size());
        assertEquals("Cainiao", result.carrierName);
        assertEquals("cainiao", result.sourceId);
        assertEquals(1785953580000L, result.events.get(0).eventTime);
        assertEquals(1785838943115L, result.events.get(2).eventTime);
    }

    @Test public void liftsTheBracketedFacilityIntoTheLocation() throws IOException {
        TrackingEvent newest = parse(DELIVERING).events.get(0);

        assertEquals("Mayong Town", newest.location);
        assertEquals("Departed from sorting center", newest.description);
        assertEquals("SC_OUTBOUND_SUCCESS", newest.rawStatus);
    }

    @Test public void reportsTheNewestScanAsTheCurrentStatus() throws IOException {
        TrackingResult result = parse(DELIVERING);

        assertEquals("Departed from sorting center", result.statusText);
        assertEquals("IN_TRANSIT", result.normalizedStatus);
        assertTrue(result.isUseful());
    }

    @Test public void rejectsANumberCainiaoDoesNotKnow() {
        try {
            parser.parse(UNKNOWN_NUMBER, "9400111899223197428490", "cainiao", "Cainiao");
            fail("An empty detail list must not be reported as tracking data");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("no tracking record"));
        }
    }

    @Test public void rejectsAResponseAboutADifferentNumber() {
        try {
            parser.parse(DELIVERING, "1ST99999999999", "cainiao", "Cainiao");
            fail("A mismatched tracking number must not be accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("different tracking record"));
        }
    }

    @Test public void rejectsMalformedJson() {
        try {
            parser.parse("<html>blocked</html>", "1ST06013631493", "cainiao", "Cainiao");
            fail("HTML must not parse as a tracking response");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("invalid JSON"));
        }
    }

    private TrackingResult parse(String payload) throws IOException {
        return parser.parse(payload, "1ST06013631493", "cainiao", "Cainiao");
    }
}
