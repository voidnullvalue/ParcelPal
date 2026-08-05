package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

        Method bootstrap = ParcelsAppWebSource.class.getDeclaredMethod("bootstrapSession");
        bootstrap.setAccessible(true);
        Object session = bootstrap.invoke(source);

        Method request = null;
        for (Method candidate : ParcelsAppWebSource.class.getDeclaredMethods()) {
            if ("requestTracking".equals(candidate.getName())) {
                request = candidate;
                break;
            }
        }
        if (request == null) throw new AssertionError("requestTracking method not found");
        request.setAccessible(true);

        try {
            TrackingResult result = (TrackingResult) request.invoke(source, session, target, "1st");
            System.out.println("LIVE_1ST_RESULT=" + result.normalizedStatus);
            assertTrue(result.isUseful());
        } catch (InvocationTargetException invocation) {
            Throwable cause = invocation.getCause();
            if (!(cause instanceof ParcelsAppJsonParser.ResponseException response)) throw invocation;
            System.out.println("LIVE_1ST_RESPONSE_CODE=" + response.code);
            assertNotEquals("Explicit carrier slug was rejected", "NO_TRACKER", response.code);
        }
    }
}
