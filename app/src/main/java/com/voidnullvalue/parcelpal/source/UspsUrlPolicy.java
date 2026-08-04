package com.voidnullvalue.parcelpal.source;

import java.net.URI;
import java.util.Locale;

final class UspsUrlPolicy {
    private UspsUrlPolicy() {}

    static boolean isAllowed(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return false;
        final URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        if ("about".equalsIgnoreCase(scheme) || "data".equalsIgnoreCase(scheme) ||
                "blob".equalsIgnoreCase(scheme)) return true;
        if (!"https".equalsIgnoreCase(scheme)) return false;
        String host = uri.getHost();
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.US);
        return normalized.equals("usps.com") || normalized.endsWith(".usps.com");
    }
}
