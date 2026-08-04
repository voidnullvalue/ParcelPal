package com.voidnullvalue.parcelpal.source;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SourceRecipe {
    public final String id;
    public final String name;
    public final String kind;
    public final String urlTemplate;
    public final Set<String> hosts;
    public final Set<String> carriers;

    public SourceRecipe(String id, String name, String kind, String urlTemplate, Set<String> hosts, Set<String> carriers) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.urlTemplate = urlTemplate;
        this.hosts = Collections.unmodifiableSet(new LinkedHashSet<>(hosts));
        this.carriers = Collections.unmodifiableSet(new LinkedHashSet<>(carriers));
    }

    public boolean supportsCarrier(String carrier) {
        if (carriers.isEmpty()) return true;
        for (String supported : carriers) if (supported.equalsIgnoreCase(carrier)) return true;
        return false;
    }
}
