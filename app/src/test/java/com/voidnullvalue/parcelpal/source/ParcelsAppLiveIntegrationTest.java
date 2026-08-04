package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ParcelsAppLiveIntegrationTest {
    @Test public void retrievesLiveUspsTrackingStates() throws Exception {
        String trackingNumber = String.join("", "92055902", "6733880000", "68080083");
        SourceRecipe recipe = new SourceRecipe(
                "parcelsapp",
                "ParcelsApp",
                "aggregator",
                "https://parcelsapp.com/en/tracking/{tracking}",
                new LinkedHashSet<>(Collections.singletonList("parcelsapp.com")),
                Collections.emptySet()
        );
        ParcelsAppWebSource source = new ParcelsAppWebSource(recipe, new ParcelsAppJsonParser());
        TrackingResult result = source.fetch(new TrackingTarget(trackingNumber, "USPS"));

        assertTrue("Expected a credible live USPS result", result.isUseful());
        assertFalse("Expected at least one live USPS tracking event", result.events.isEmpty());
    }
}
