package com.voidnullvalue.parcelpal.source;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ParcelsAppProtocolTest {
    @Test public void reproducesPublicWebTrackingIdTransform() {
        assertEquals(
                "%07%02%7C%7C%7D%7D%7D%00%7C%04%00%7D%01%05%06%03%04%05%06%07%7C%7D",
                ParcelsAppProtocol.encodedTrackingId("9400111206213785678901")
        );
    }

    @Test public void mapsDetectedCarrierToParcelsSlug() {
        assertEquals("usps", ParcelsAppProtocol.carrierSlug("USPS"));
        assertEquals("fedex", ParcelsAppProtocol.carrierSlug("FedEx"));
        assertEquals("1st", ParcelsAppProtocol.carrierSlug("1ST"));
        assertEquals("", ParcelsAppProtocol.carrierSlug("Auto-detect"));
    }
}
