package com.voidnullvalue.parcelpal.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CarrierDetector {
    public static final String AUTO_DETECT = "Auto-detect";

    private static final List<String> CARRIERS = Collections.unmodifiableList(Arrays.asList(
            AUTO_DETECT, "USPS", "UPS", "FedEx", "DHL", "DHL Express", "DHL eCommerce",
            "Amazon Logistics", "OnTrac", "LaserShip", "Canada Post", "Royal Mail",
            "Australia Post", "International Post", "YunExpress", "Cainiao", "4PX", "UniUni", "1ST"
    ));

    private CarrierDetector() {}

    public static List<String> carrierChoices() { return CARRIERS; }

    public static String defaultChoice() { return AUTO_DETECT; }

    public static String normalizeChoice(CharSequence value) {
        if (value == null) return AUTO_DETECT;
        String candidate = value.toString().trim();
        if (candidate.isEmpty()) return AUTO_DETECT;
        for (String carrier : CARRIERS) {
            if (carrier.equalsIgnoreCase(candidate)) return carrier;
        }
        return AUTO_DETECT;
    }

    public static String normalizeTrackingNumber(String value) {
        if (value == null) return "";
        return value.toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
    }

    /**
     * Returns the single most likely carrier, or {@link #AUTO_DETECT} when the format is unrecognized.
     * A tracking number can legitimately belong to more than one carrier at once, so callers that
     * perform lookups should prefer {@link #detectAll(String)}.
     */
    public static String detect(String raw) {
        return detectAll(raw).get(0);
    }

    /**
     * Returns every carrier whose number format matches, most likely first.
     *
     * <p>Many numbers are genuinely ambiguous. A {@code 1ST}-prefixed consignment is issued by 1ST
     * Group but is normally moved and reported by Cainiao, and an S10 number ending in a country
     * code is handled both by that country's post and by the origin consolidator. Returning every
     * plausible carrier lets the source registry query all of them instead of guessing one.
     *
     * <p>The list is never empty; an unrecognized format yields a single {@link #AUTO_DETECT} entry.
     */
    public static List<String> detectAll(String raw) {
        String n = normalizeTrackingNumber(raw);
        List<String> out = new ArrayList<>();

        if (n.matches("1ST[0-9]{11}")) {
            add(out, "1ST");
            add(out, "Cainiao");
        }
        if (n.matches("1Z[0-9A-Z]{16}")) add(out, "UPS");
        if (n.matches("TBA[0-9]{12,}")) add(out, "Amazon Logistics");
        if (n.matches("(?:D1|C1)[0-9A-Z]{12,}")) add(out, "OnTrac");
        if (n.matches("L[A-Z][0-9]{8,}")) add(out, "LaserShip");
        if (n.matches("9[2345][0-9]{18,32}") || n.matches("[0-9]{20,34}")) add(out, "USPS");
        if (n.matches("[0-9]{12}") || n.matches("[0-9]{15}") || n.matches("[0-9]{18}")) add(out, "FedEx");
        if (n.matches("GM[0-9]{16,22}") || n.matches("C[A-Z][0-9]{9}DE") || n.matches("[A-Z]{3}[0-9]{18,}")) add(out, "DHL eCommerce");
        if (n.matches("JD[0-9]{16,}") || n.matches("[0-9]{10,11}")) add(out, "DHL");
        if (n.matches("[A-Z]{2}[0-9]{9}CA")) add(out, "Canada Post");
        if (n.matches("[A-Z]{2}[0-9]{9}GB")) add(out, "Royal Mail");
        if (n.matches("[A-Z]{2}[0-9]{9}AU")) add(out, "Australia Post");
        if (n.matches("[A-Z]{2}[0-9]{9}[A-Z]{2}")) add(out, "International Post");
        if (n.matches("[A-Z]{2}[0-9]{9}CN")) add(out, "Cainiao");
        if ((n.startsWith("YT") || n.startsWith("YH")) && n.length() >= 16) {
            add(out, "YunExpress");
            add(out, "Cainiao");
        }
        if ((n.startsWith("LP") || n.startsWith("LA")) && n.length() >= 14) add(out, "Cainiao");
        if (n.startsWith("4PX") || n.startsWith("LZ") && n.length() > 13) {
            add(out, "4PX");
            add(out, "Cainiao");
        }
        if (n.startsWith("UNI") || n.startsWith("UUS")) add(out, "UniUni");

        if (out.isEmpty()) out.add(AUTO_DETECT);
        return Collections.unmodifiableList(out);
    }

    public static boolean plausible(String raw) {
        String n = normalizeTrackingNumber(raw);
        if (n.length() < 7 || n.length() > 50) return false;
        boolean digit = false;
        for (int i = 0; i < n.length(); i++) digit |= Character.isDigit(n.charAt(i));
        return digit;
    }

    private static void add(List<String> carriers, String carrier) {
        if (!carriers.contains(carrier)) carriers.add(carrier);
    }
}
