package com.voidnullvalue.parcelpal.source;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SourceRecipe {
    /** Carrier-owned page or API fetched over plain HTTPS. */
    public static final String KIND_DIRECT = "direct";
    /** Carrier-owned page that only renders in a browser engine. */
    public static final String KIND_BROWSER = "browser";
    /** Third-party service that resolves numbers across carriers. */
    public static final String KIND_AGGREGATOR = "aggregator";
    /** Not fetched by ParcelPal; offered to the user as an external link. */
    public static final String KIND_LINK = "link";

    public final String id;
    public final String name;
    public final String kind;
    public final String urlTemplate;
    public final Set<String> hosts;
    public final Set<String> carriers;
    /** Host suffixes a browser source may load, e.g. {@code usps.com}. Empty for non-browser kinds. */
    public final Set<String> browserHosts;
    /** Asset name of the extraction script for browser sources. */
    public final String script;
    /** Higher scores win ties when sources disagree. */
    public final int trust;

    public SourceRecipe(String id, String name, String kind, String urlTemplate, Set<String> hosts,
                        Set<String> carriers, Set<String> browserHosts, String script, int trust) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.urlTemplate = urlTemplate;
        this.hosts = Collections.unmodifiableSet(new LinkedHashSet<>(hosts));
        this.carriers = Collections.unmodifiableSet(new LinkedHashSet<>(carriers));
        this.browserHosts = Collections.unmodifiableSet(new LinkedHashSet<>(browserHosts));
        this.script = script == null ? "" : script;
        this.trust = trust;
    }

    /** True when this recipe is fetched during a refresh rather than only linked. */
    public boolean fetchable() {
        return !KIND_LINK.equals(kind);
    }

    /** True when this recipe represents a carrier's own tracking service. */
    public boolean carrierOwned() {
        return KIND_DIRECT.equals(kind) || KIND_BROWSER.equals(kind);
    }

    public boolean supportsCarrier(String carrier) {
        if (carriers.isEmpty()) return true;
        for (String supported : carriers) if (supported.equalsIgnoreCase(carrier)) return true;
        return false;
    }

    /** True when any of the candidate carriers is supported. A recipe without carriers matches all. */
    public boolean supportsAny(Collection<String> candidates) {
        if (carriers.isEmpty()) return true;
        if (candidates == null || candidates.isEmpty()) return false;
        for (String candidate : candidates) if (supportsCarrier(candidate)) return true;
        return false;
    }

    /** The first candidate this recipe supports, or the first candidate when it supports all. */
    public String matchedCarrier(Collection<String> candidates) {
        if (candidates == null || candidates.isEmpty()) return "";
        for (String candidate : candidates) if (supportsCarrier(candidate)) return candidate;
        return "";
    }

    public String url(String trackingNumber) {
        return urlTemplate.replace("{tracking}", trackingNumber);
    }
}
