package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;

import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class ParcelsApp1stLiveTest {
    @Test public void public1stSampleIsRoutedToARealCarrier() throws Exception {
        SourceRecipe recipe = new SourceRecipe(
                "parcelsapp",
                "ParcelsApp",
                "aggregator",
                "https://parcelsapp.com/en/tracking/{tracking}",
                Set.of("parcelsapp.com"),
                Collections.emptySet()
        );
        ParcelsAppWebSource source = new ParcelsAppWebSource(recipe, new ParcelsAppJsonParser());
        TrackingTarget target = new TrackingTarget("1ST06003441583", "1ST");

        try {
            TrackingResult result = source.fetch(target);
            assertTrue(result.isUseful());
        } catch (ParcelsAppJsonParser.ResponseException response) {
            assertNotEquals("NO_TRACKER", response.code);
            assertTrue("Expected carrier recognition or historical data, got " + response.code,
                    "NO_DATA".equals(response.code) || "INVALID_TRACKING_NUMBER".equals(response.code));
        }
    }
}
