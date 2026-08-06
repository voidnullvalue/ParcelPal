package com.voidnullvalue.parcelpal.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CarrierDetectorTest {
    @Test public void detectsCommonFormats() {
        assertEquals("UPS", CarrierDetector.detect("1Z999AA10123456784"));
        assertEquals("USPS", CarrierDetector.detect("9400111206213785678901"));
        assertEquals("FedEx", CarrierDetector.detect("123456789012"));
        assertEquals("DHL eCommerce", CarrierDetector.detect("GM123456789012345678"));
        assertEquals("Amazon Logistics", CarrierDetector.detect("TBA123456789012"));
        assertEquals("Royal Mail", CarrierDetector.detect("AA123456789GB"));
        assertEquals("1ST", CarrierDetector.detect("1ST06013631493"));
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
        assertEquals("1ST", CarrierDetector.normalizeChoice("1st"));
    }

    @Test public void normalizesWhitespaceAndPunctuation() {
        assertEquals("1Z999AA10123456784", CarrierDetector.normalizeTrackingNumber("1Z 999-AA1 01 2345 6784"));
    }

    @Test public void reportsEveryCarrierAOneStNumberBelongsTo() {
        List<String> candidates = CarrierDetector.detectAll("1ST06013631493");

        assertEquals("1ST", candidates.get(0));
        assertTrue("Cainiao also carries 1ST consignments", candidates.contains("Cainiao"));
    }

    @Test public void treatsChineseS10NumbersAsPostAndConsolidator() {
        List<String> candidates = CarrierDetector.detectAll("LX123456789CN");

        assertTrue(candidates.contains("International Post"));
        assertTrue(candidates.contains("Cainiao"));
    }

    @Test public void keepsAnUnambiguousNumberToOneCarrier() {
        assertEquals(1, CarrierDetector.detectAll("1Z999AA10123456784").size());
        assertEquals("UPS", CarrierDetector.detectAll("1Z999AA10123456784").get(0));
    }

    @Test public void reportsAutoDetectForAnUnrecognizedFormat() {
        List<String> candidates = CarrierDetector.detectAll("ZZZ7");

        assertEquals(1, candidates.size());
        assertEquals(CarrierDetector.AUTO_DETECT, candidates.get(0));
    }

    @Test public void neverReturnsAnEmptyCandidateList() {
        assertFalse(CarrierDetector.detectAll(null).isEmpty());
        assertFalse(CarrierDetector.detectAll("").isEmpty());
    }

    @Test public void keepsTheOldPrimaryDetectionForEveryKnownFormat() {
        assertEquals("Canada Post", CarrierDetector.detect("AA123456789CA"));
        assertEquals("Australia Post", CarrierDetector.detect("AA123456789AU"));
        assertEquals("International Post", CarrierDetector.detect("AA123456789FR"));
        assertEquals("UniUni", CarrierDetector.detect("UNI1234567"));
        assertEquals("OnTrac", CarrierDetector.detect("D10012345678AB"));
    }

    /** An LP number matches LaserShip's shape, but it is the Cainiao format in practice. */
    @Test public void offersCainiaoAlongsideLaserShipForLpNumbers() {
        List<String> candidates = CarrierDetector.detectAll("LP12345678901234");

        assertEquals("LaserShip", candidates.get(0));
        assertTrue(candidates.contains("Cainiao"));
    }
}
