package com.voidnullvalue.parcelpal.source;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Network boundary for a browser-rendered tracking source.
 *
 * <p>A source declares the registrable host suffixes it owns, for example {@code usps.com}. Every
 * navigation and subresource is checked against that list, so the offscreen browser can never reach
 * a third-party host, an analytics endpoint, or a cleartext URL.
 */
final class BrowserHostPolicy {
    private final Set<String> hostSuffixes;

    BrowserHostPolicy(Collection<String> hostSuffixes) {
        Set<String> normalized = new LinkedHashSet<>();
        if (hostSuffixes != null) {
            for (String suffix : hostSuffixes) {
                if (suffix == null) continue;
                String value = suffix.trim().toLowerCase(Locale.US);
                if (!value.isEmpty()) normalized.add(value);
            }
        }
        this.hostSuffixes = Collections.unmodifiableSet(normalized);
    }

    Set<String> hostSuffixes() { return hostSuffixes; }

    boolean isAllowed(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return false;
        if (hostSuffixes.isEmpty()) return false;
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
        for (String suffix : hostSuffixes) {
            if (normalized.equals(suffix) || normalized.endsWith("." + suffix)) return true;
        }
        return false;
    }
}
