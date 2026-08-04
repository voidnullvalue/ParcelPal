package com.voidnullvalue.parcelpal.source;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class UspsBrowserLiveTest {
    @Test(timeout = 120_000)
    public void actualAndroidWebViewReturnsLiveUspsTimeline() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String trackingNumber = String.join("", "92055902", "6733880000", "68080083");
        SourceRecipe recipe = new SourceRecipe(
                "usps",
                "USPS",
                "direct",
                "https://tools.usps.com/go/TrackConfirmAction?tLabels={tracking}",
                new LinkedHashSet<>(Arrays.asList("tools.usps.com", "www.usps.com")),
                new LinkedHashSet<>(Collections.singletonList("USPS"))
        );

        TrackingResult result = new UspsBrowserSource(context, recipe, new UspsDomParser())
                .fetch(new TrackingTarget(trackingNumber, "USPS"));

        Log.i("ParcelPalLiveTest", "status=" + result.normalizedStatus +
                " events=" + result.events.size() +
                " etaPresent=" + !result.estimatedDelivery.isEmpty());
        assertTrue("Expected a useful USPS result", result.isUseful());
        assertFalse("Expected at least one USPS timeline event", result.events.isEmpty());
    }
}
