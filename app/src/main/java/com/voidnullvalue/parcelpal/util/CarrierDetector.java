package com.voidnullvalue.parcelpal.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CarrierDetector {
    public static final String AUTO_DETECT = "Auto-detect";

    private static final List<String> CARRIERS = Collections.unmodifiableList(Arrays.asList(
            AUTO_DETECT, "USPS", "UPS", "FedEx", "DHL", "DHL Express", "DHL eCommerce",
            "Amazon Logistics", "OnTrac", "LaserShip", "Canada Post", "Royal Mail",
            "Australia Post", "International Post", "YunExpress", "Cainiao", "4PX", "UniUni"
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

    public static String detect(String raw) {
        String n = normalizeTrackingNumber(raw);
        if (n.matches("1Z[0-9A-Z]{16}")) return "UPS";
        if (n.matches("TBA[0-9]{12,}")) return "Amazon Logistics";
        if (n.matches("(?:D1|C1)[0-9A-Z]{12,}")) return "OnTrac";
        if (n.matches("L[A-Z][0-9]{8,}")) return "LaserShip";
        if (n.matches("9[2345][0-9]{18,32}") || n.matches("[0-9]{20,34}")) return "USPS";
        if (n.matches("[0-9]{12}") || n.matches("[0-9]{15}") || n.matches("[0-9]{18}")) return "FedEx";
        if (n.matches("GM[0-9]{16,22}") || n.matches("C[A-Z][0-9]{9}DE") || n.matches("[A-Z]{3}[0-9]{18,}")) return "DHL eCommerce";
        if (n.matches("JD[0-9]{16,}") || n.matches("[0-9]{10,11}")) return "DHL";
        if (n.matches("[A-Z]{2}[0-9]{9}CA")) return "Canada Post";
        if (n.matches("[A-Z]{2}[0-9]{9}GB")) return "Royal Mail";
        if (n.matches("[A-Z]{2}[0-9]{9}AU")) return "Australia Post";
        if (n.matches("[A-Z]{2}[0-9]{9}[A-Z]{2}")) return "International Post";
        if ((n.startsWith("YT") || n.startsWith("YH")) && n.length() >= 16) return "YunExpress";
        if ((n.startsWith("LP") || n.startsWith("LA")) && n.length() >= 14) return "Cainiao";
        if (n.startsWith("4PX") || n.startsWith("LZ") && n.length() > 13) return "4PX";
        if (n.startsWith("UNI") || n.startsWith("UUS")) return "UniUni";
        return AUTO_DETECT;
    }

    public static boolean plausible(String raw) {
        String n = normalizeTrackingNumber(raw);
        if (n.length() < 7 || n.length() > 50) return false;
        boolean digit = false;
        for (int i = 0; i < n.length(); i++) digit |= Character.isDigit(n.charAt(i));
        return digit;
    }
}
