package com.voidnullvalue.parcelpal.util;

import com.voidnullvalue.parcelpal.model.TrackingEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Collapses the events of several tracking sources into one timeline.
 *
 * <p>ParcelPal stores every source's events separately so that no source can overwrite another.
 * The same physical scan therefore appears once per source that reported it, and a single source
 * sometimes repeats a scan in its own response. This merger folds those together while keeping the
 * list of sources that agreed on each entry, so the detail screen can show corroboration instead of
 * duplicates.
 */
public final class TimelineMerger {
    /** Two scans this close together are treated as the same instant regardless of location. */
    private static final long SAME_INSTANT_MS = 60_000L;
    /** Identical wording at a compatible location within this window is treated as one scan. */
    private static final long SAME_SCAN_MS = 3 * 60 * 60 * 1000L;

    private TimelineMerger() {}

    public static final class MergedEvent {
        /** The most detailed event in the cluster. */
        public final TrackingEvent event;
        /** Distinct source display names that reported this event, in trust order. */
        public final List<String> sourceNames;
        /** Distinct carrier names attached to this event, in trust order. */
        public final List<String> carrierNames;
        /** Number of raw rows folded into this entry. */
        public final int reportCount;

        MergedEvent(TrackingEvent event, List<String> sourceNames, List<String> carrierNames, int reportCount) {
            this.event = event;
            this.sourceNames = Collections.unmodifiableList(sourceNames);
            this.carrierNames = Collections.unmodifiableList(carrierNames);
            this.reportCount = reportCount;
        }
    }

    public static List<MergedEvent> merge(List<TrackingEvent> events) {
        return merge(events, Collections.emptyMap());
    }

    /**
     * @param events           raw event rows from every source
     * @param sourceTrustById  optional trust score per source id; higher wins ties
     */
    public static List<MergedEvent> merge(List<TrackingEvent> events, Map<String, Integer> sourceTrustById) {
        if (events == null || events.isEmpty()) return Collections.emptyList();
        Map<String, Integer> trust = sourceTrustById == null ? Collections.emptyMap() : sourceTrustById;

        List<TrackingEvent> ordered = new ArrayList<>(events);
        ordered.sort(Comparator
                .comparingInt((TrackingEvent event) -> -trustOf(event, trust))
                .thenComparingLong(event -> -event.eventTime));

        Map<String, List<Cluster>> byDescription = new LinkedHashMap<>();
        List<Cluster> clusters = new ArrayList<>();
        for (TrackingEvent event : ordered) {
            String key = safe(event.trackingNumber).toUpperCase(Locale.US)
                    + "\u001f" + normalizeDescription(event.description);
            List<Cluster> candidates = byDescription.computeIfAbsent(key, ignored -> new ArrayList<>());
            Cluster target = null;
            for (Cluster candidate : candidates) {
                if (candidate.accepts(event)) {
                    target = candidate;
                    break;
                }
            }
            if (target == null) {
                target = new Cluster(event, trustOf(event, trust));
                candidates.add(target);
                clusters.add(target);
            } else {
                target.add(event, trustOf(event, trust));
            }
        }

        List<MergedEvent> merged = new ArrayList<>(clusters.size());
        for (Cluster cluster : clusters) merged.add(cluster.toMergedEvent());
        merged.sort(Comparator
                .comparingInt((MergedEvent item) -> item.event.eventTime == 0 ? 1 : 0)
                .thenComparingLong(item -> -item.event.eventTime));
        return merged;
    }

    /**
     * Reduces a description to comparable text: lowercase, without bracketed facility prefixes,
     * carrier note labels, or punctuation.
     */
    public static String normalizeDescription(String description) {
        if (description == null) return "";
        String text = description.toLowerCase(Locale.US)
                .replaceAll("\\[[^\\]]*\\]", " ")
                .replaceAll("^\\s*(carrier note|note|status)\\s*:", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return text.replaceAll("\\s+", " ");
    }

    private static String normalizeLocation(String location) {
        if (location == null) return "";
        return location.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static boolean locationsCompatible(String first, String second) {
        String a = normalizeLocation(first);
        String b = normalizeLocation(second);
        if (a.isEmpty() || b.isEmpty()) return true;
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private static int trustOf(TrackingEvent event, Map<String, Integer> trust) {
        Integer score = trust.get(safe(event.sourceId));
        return score == null ? 0 : score;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Cluster {
        private final List<String> sourceNames = new ArrayList<>();
        private final List<String> carrierNames = new ArrayList<>();
        private final List<TrackingEvent> members = new ArrayList<>();
        private TrackingEvent best;
        private int bestScore;

        Cluster(TrackingEvent first, int trust) {
            best = first;
            bestScore = score(first, trust);
            record(first);
        }

        boolean accepts(TrackingEvent candidate) {
            for (TrackingEvent member : members) {
                if (member.eventTime == 0 || candidate.eventTime == 0) return true;
                long delta = Math.abs(member.eventTime - candidate.eventTime);
                if (delta <= SAME_INSTANT_MS) return true;
                if (delta <= SAME_SCAN_MS && locationsCompatible(member.location, candidate.location)) return true;
            }
            return false;
        }

        void add(TrackingEvent event, int trust) {
            int candidateScore = score(event, trust);
            if (candidateScore > bestScore) {
                best = event;
                bestScore = candidateScore;
            }
            record(event);
        }

        private void record(TrackingEvent event) {
            members.add(event);
            String source = safe(event.sourceName);
            if (!source.isEmpty() && !sourceNames.contains(source)) sourceNames.add(source);
            String carrier = safe(event.carrierName);
            if (!carrier.isEmpty() && !carrierNames.contains(carrier)) carrierNames.add(carrier);
        }

        MergedEvent toMergedEvent() {
            TrackingEvent canonical = best;
            if (canonical.eventTime == 0) {
                for (TrackingEvent member : members) {
                    if (member.eventTime != 0) {
                        canonical = member;
                        break;
                    }
                }
            }
            return new MergedEvent(canonical, sourceNames, carrierNames, members.size());
        }

        private static int score(TrackingEvent event, int trust) {
            int score = trust;
            if (event.eventTime != 0) score += 1000;
            if (!safe(event.location).isEmpty()) score += 500;
            score += Math.min(safe(event.description).length(), 200);
            return score;
        }
    }
}
