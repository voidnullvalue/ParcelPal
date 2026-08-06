package com.voidnullvalue.parcelpal.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Per-source opt-out, stored by source id.
 *
 * <p>Sources are enabled by default so that a new adapter shipped in {@code sources.json} starts
 * working without a code change here. Earlier versions stored one switch for all carrier-owned
 * pages and one per aggregator, so those keys are still honored until the user changes the setting.
 */
public final class SourcePreferences {
    private static final String FILE = "source_preferences";
    private static final String SOURCE_PREFIX = "source_";
    private static final String LEGACY_CARRIER_OWNED = "direct";

    private final SharedPreferences prefs;

    public SourcePreferences(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean sourceEnabled(String id, String kind) {
        if (id == null || id.trim().isEmpty()) return false;
        String key = SOURCE_PREFIX + id;
        if (prefs.contains(key)) return prefs.getBoolean(key, true);
        if (prefs.contains(id)) return prefs.getBoolean(id, true);
        if (carrierOwned(kind) && prefs.contains(LEGACY_CARRIER_OWNED)) {
            return prefs.getBoolean(LEGACY_CARRIER_OWNED, true);
        }
        return true;
    }

    public void setSourceEnabled(String id, boolean value) {
        if (id == null || id.trim().isEmpty()) return;
        prefs.edit().putBoolean(SOURCE_PREFIX + id, value).apply();
    }

    public boolean backgroundEnabled() { return prefs.getBoolean("background", false); }
    public boolean notifyDeliveredOnly() { return prefs.getBoolean("notify_delivered_only", false); }

    public void setBackgroundEnabled(boolean value) { prefs.edit().putBoolean("background", value).apply(); }
    public void setNotifyDeliveredOnly(boolean value) { prefs.edit().putBoolean("notify_delivered_only", value).apply(); }

    private static boolean carrierOwned(String kind) {
        return "direct".equals(kind) || "browser".equals(kind);
    }
}
