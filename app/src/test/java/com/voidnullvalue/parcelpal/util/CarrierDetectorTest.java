package com.voidnullvalue.parcelpal.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CarrierDetectorTest {
    @Test public void detectsCommonFormats() {
        assertEquals("UPS", CarrierDetector.detect("1Z999AA10123456784"));
        assertEquals("USPS", CarrierDetector.detect("9400111206213785678901"));
        assertEquals("FedEx", CarrierDetector.detect("123456789012"));
        assertEquals("DHL eCommerce", CarrierDetector.detect("GM123456789012345678"));
        assertEquals("Amazon Logistics", CarrierDetector.detect("TBA123456789012"));
        assertEquals("Royal Mail", CarrierDetector.detect("AA123456789GB"));
    }

    @Test public void acceptsLongUspsHandoffNumbers() {
        assertEquals("USPS", CarrierDetector.detect("92612999998771000123456789"));
        assertTrue(CarrierDetector.plausible("92612999998771000123456789"));
    }

    @Test public void defaultsBlankOrUnknownCarrierChoiceToAutoDetect() {
        assertEquals("Auto-detect", CarrierDetector.defaultChoice());
        assertEquals("Auto-detect", CarrierDetector.normalizeChoice(null));
        assertEquals("Auto-detect", CarrierDetector.normalizeChoice(""));
        assertEquals("Auto-detect", CarrierDetector.normalizeChoice("not a carrier"));
        assertEquals("USPS", CarrierDetector.normalizeChoice("usps"));
    }

    @Test public void normalizesWhitespaceAndPunctuation() {
        assertEquals("1Z999AA10123456784", CarrierDetector.normalizeTrackingNumber("1Z 999-AA1 01 2345 6784"));
    }
}
