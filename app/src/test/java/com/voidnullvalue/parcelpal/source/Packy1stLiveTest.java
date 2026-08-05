package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;
import com.voidnullvalue.parcelpal.network.SafeHttpClient;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class Packy1stLiveTest {
    @Test public void productionSourceReturnsThePublic1stTimeline() throws Exception {
        SourceRecipe recipe = new SourceRecipe(
                "packy_1st",
                "Packy",
                "aggregator",
                "https://packyapp.com/api/tracking/{tracking}",
                Set.of("packyapp.com"),
                Set.of("1ST")
        );
        PackyTrackingSource source = new PackyTrackingSource(
                recipe,
                new SafeHttpClient(),
                new PackyJsonParser()
        );

        TrackingResult result = source.fetch(new TrackingTarget("1ST06003441583", "1ST"));
        assertTrue(result.isUseful());
        assertEquals("1ST", result.carrierName);
        assertEquals("DELIVERED", result.normalizedStatus);
        assertTrue("Expected a real event timeline", result.events.size() >= 10);
        assertTrue("Expected dated events", result.newestEventTime() > 0);
        System.out.println("LIVE_PACKY_1ST_EVENTS=" + result.events.size());
        System.out.println("LIVE_PACKY_1ST_STATUS=" + result.statusText);
    }
}
