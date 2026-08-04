package com.voidnullvalue.parcelpal.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class StatusNormalizerTest {
    @Test public void normalizesCoreStatuses() {
        assertEquals("DELIVERED", StatusNormalizer.normalize("Delivered, In/At Mailbox"));
        assertEquals("OUT_FOR_DELIVERY", StatusNormalizer.normalize("Out for delivery"));
        assertEquals("PRE_TRANSIT", StatusNormalizer.normalize("Label created"));
        assertEquals("CUSTOMS", StatusNormalizer.normalize("Customs clearance processing"));
        assertEquals("EXCEPTION", StatusNormalizer.normalize("Delivery exception: weather delay"));
        assertEquals("READY_FOR_PICKUP", StatusNormalizer.normalize("Available for pickup"));
    }
}
