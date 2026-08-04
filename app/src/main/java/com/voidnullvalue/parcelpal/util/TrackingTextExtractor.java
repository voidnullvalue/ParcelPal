package com.voidnullvalue.parcelpal.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TrackingTextExtractor {
    private static final Pattern CANDIDATE = Pattern.compile("(?i)\\b(?:1Z[0-9A-Z]{16}|TBA[0-9]{12,}|[A-Z]{2}[0-9]{9}[A-Z]{2}|9[2345][0-9]{18,32}|[A-Z0-9]{8,50})\\b");

    private TrackingTextExtractor() {}

    public static String bestCandidate(String text) {
        if (text == null) return "";
        String best = "";
        Matcher matcher = CANDIDATE.matcher(text);
        while (matcher.find()) {
            String candidate = CarrierDetector.normalizeTrackingNumber(matcher.group());
            if (!CarrierDetector.plausible(candidate)) continue;
            if (score(candidate) > score(best)) best = candidate;
        }
        return best;
    }

    private static int score(String candidate) {
        if (candidate == null || candidate.isEmpty()) return -1;
        String carrier = CarrierDetector.detect(candidate);
        int recognized = "Auto-detect".equals(carrier) ? 0 : 100;
        return recognized + candidate.length();
    }
}
