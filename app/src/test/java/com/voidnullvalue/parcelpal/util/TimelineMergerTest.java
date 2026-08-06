package com.voidnullvalue.parcelpal.util;

import com.voidnullvalue.parcelpal.model.TrackingEvent;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class TimelineMergerTest {
    private static final Map<String, Integer> TRUST = trust();

    @Test public void foldsTheSameScanReportedByTwoSources() {
        List<TimelineMerger.MergedEvent> merged = TimelineMerger.merge(Arrays.asList(
                event("cainiao", "Cainiao", 1_000_000L, "Mayong Town", "Departed from sorting center"),
                event("parcelsapp", "ParcelsApp", 1_000_000L, "", "Departed from sorting center")
        ), TRUST);

        assertEquals(1, merged.size());
        assertEquals(2, merged.get(0).reportCount);
        assertEquals(Arrays.asList("Cainiao", "ParcelsApp"), merged.get(0).sourceNames);
    }

    @Test public void keepsTheEntryThatCarriesTheLocation() {
        List<TimelineMerger.MergedEvent> merged = TimelineMerger.merge(Arrays.asList(
                event("parcelsapp", "ParcelsApp", 1_000_000L, "", "Departed from sorting center"),
                event("cainiao", "Cainiao", 1_000_000L, "Mayong Town", "Departed from sorting center")
        ), TRUST);

        assertEquals(1, merged.size());
        assertEquals("Mayong Town", merged.get(0).event.location);
    }

    @Test public void collapsesASourceRepeatingItsOwnScan() {
        List<TimelineMerger.MergedEvent> merged = TimelineMerger.merge(Arrays.asList(
                event("packy_1st", "Packy", 5_000L, "", "Parcel information received"),
                event("packy_1st", "Packy", 5_000L, "", "Parcel information received")
        ), TRUST);

        assertEquals(1, merged.size());
        assertEquals(Collections.singletonList("Packy"), merged.get(0).sourceNames);
    }

    @Test public void keepsDistinctScansOfTheSameWordingApart() {
        long day = 24 * 60 * 60 * 1000L;
        List<TimelineMerger.MergedEvent> merged = TimelineMerger.merge(Arrays.asList(
                event("cainiao", "Cainiao", day, "Shenzhen", "Arrived at facility"),
                event("cainiao", "Cainiao", 3 * day, "Los Angeles", "Arrived at facility")
        ), TRUST);

        assertEquals(2, merged.size());
    }

    @Test public void sortsNewestFirstAndPushesUndatedEntriesLast() {
        List<TimelineMerger.MergedEvent> merged = TimelineMerger.merge(Arrays.asList(
                event("usps", "USPS", 0L, "", "Label created"),
                event("usps", "USPS", 10_000L, "", "In transit"),
                event("usps", "USPS", 90_000L, "", "Out for delivery")
        ), TRUST);

        assertEquals(3, merged.size());
        assertEquals("Out for delivery", merged.get(0).event.description);
        assertEquals("In transit", merged.get(1).event.description);
        assertEquals("Label created", merged.get(2).event.description);
    }

    @Test public void doesNotMergeAcrossTrackingNumbers() {
        TrackingEvent leg = event("cainiao", "Cainiao", 1_000_000L, "", "Arrived at facility");
        leg.trackingNumber = "9400111899223197428490";
        List<TimelineMerger.MergedEvent> merged = TimelineMerger.merge(Arrays.asList(
                event("cainiao", "Cainiao", 1_000_000L, "", "Arrived at facility"), leg), TRUST);

        assertEquals(2, merged.size());
    }

    @Test public void normalizesWordingBeforeComparing() {
        assertEquals("departed from sorting center",
                TimelineMerger.normalizeDescription("[Mayong Town] Departed from sorting center"));
        assertEquals("in transit", TimelineMerger.normalizeDescription("  In   Transit.  "));
    }

    @Test public void toleratesAnEmptyTimeline() {
        assertTrue(TimelineMerger.merge(Collections.emptyList()).isEmpty());
    }

    private static TrackingEvent event(String sourceId, String sourceName, long time,
                                       String location, String description) {
        TrackingEvent event = new TrackingEvent();
        event.trackingNumber = "1ST06013631493";
        event.carrierName = sourceName;
        event.sourceId = sourceId;
        event.sourceName = sourceName;
        event.eventTime = time;
        event.location = location;
        event.description = description;
        event.eventKey = TrackingEvent.makeKey(event.trackingNumber, time, location, description);
        return event;
    }

    private static Map<String, Integer> trust() {
        Map<String, Integer> trust = new HashMap<>();
        trust.put("usps", 100);
        trust.put("cainiao", 70);
        trust.put("packy_1st", 60);
        trust.put("parcelsapp", 50);
        return trust;
    }
}
