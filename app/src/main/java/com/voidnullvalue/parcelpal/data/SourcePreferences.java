package com.voidnullvalue.parcelpal.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class SourcePreferences {
    private static final String FILE = "source_preferences";
    private final SharedPreferences prefs;

    public SourcePreferences(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean directEnabled() { return prefs.getBoolean("direct", true); }
    public boolean packyEnabled() { return prefs.getBoolean("packy_1st", true); }
    public boolean parcelsEnabled() { return prefs.getBoolean("parcelsapp", true); }
    public boolean postalNinjaEnabled() { return prefs.getBoolean("postal_ninja", true); }
    public boolean trackGlobalEnabled() { return prefs.getBoolean("track_global", true); }
    public boolean backgroundEnabled() { return prefs.getBoolean("background", false); }
    public boolean notifyDeliveredOnly() { return prefs.getBoolean("notify_delivered_only", false); }

    public boolean sourceEnabled(String id, String kind) {
        if ("direct".equals(kind)) return directEnabled();
        return switch (id) {
            case "packy_1st" -> packyEnabled();
            case "parcelsapp" -> parcelsEnabled();
            case "postal_ninja" -> postalNinjaEnabled();
            case "track_global" -> trackGlobalEnabled();
            default -> false;
        };
    }

    public void setDirectEnabled(boolean value) { prefs.edit().putBoolean("direct", value).apply(); }
    public void setPackyEnabled(boolean value) { prefs.edit().putBoolean("packy_1st", value).apply(); }
    public void setParcelsEnabled(boolean value) { prefs.edit().putBoolean("parcelsapp", value).apply(); }
    public void setPostalNinjaEnabled(boolean value) { prefs.edit().putBoolean("postal_ninja", value).apply(); }
    public void setTrackGlobalEnabled(boolean value) { prefs.edit().putBoolean("track_global", value).apply(); }
    public void setBackgroundEnabled(boolean value) { prefs.edit().putBoolean("background", value).apply(); }
    public void setNotifyDeliveredOnly(boolean value) { prefs.edit().putBoolean("notify_delivered_only", value).apply(); }
}
