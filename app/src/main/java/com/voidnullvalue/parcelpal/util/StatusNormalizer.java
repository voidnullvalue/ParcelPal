package com.voidnullvalue.parcelpal.util;

import java.util.Locale;

public final class StatusNormalizer {
    private StatusNormalizer() {}

    public static String normalize(String text) {
        if (text == null || text.trim().isEmpty()) return "UNKNOWN";
        String s = text.toLowerCase(Locale.US);
        if (containsAny(s, "delivered", "delivery completed", "received by recipient", "signed for")) return "DELIVERED";
        if (containsAny(s, "out for delivery", "with delivery courier", "courier is delivering", "on vehicle for delivery")) return "OUT_FOR_DELIVERY";
        if (containsAny(s, "attempted delivery", "delivery attempt", "recipient unavailable", "notice left")) return "ATTEMPTED";
        if (containsAny(s, "exception", "failed", "held", "return to sender", "undeliverable", "delay", "damaged", "lost")) return "EXCEPTION";
        if (containsAny(s, "customs", "clearance", "import scan", "export scan")) return "CUSTOMS";
        if (containsAny(s, "available for pickup", "ready for collection", "pickup point")) return "READY_FOR_PICKUP";
        if (containsAny(s, "label created", "shipment information", "pre-shipment", "electronic information", "order data transmitted")) return "PRE_TRANSIT";
        if (containsAny(s, "in transit", "departed", "arrived", "processed", "accepted", "picked up", "handover", "sorting", "facility")) return "IN_TRANSIT";
        return "UNKNOWN";
    }

    public static int priority(String normalized) {
        if (normalized == null) return 0;
        return switch (normalized) {
            case "EXCEPTION" -> 90;
            case "OUT_FOR_DELIVERY" -> 80;
            case "READY_FOR_PICKUP" -> 75;
            case "ATTEMPTED" -> 70;
            case "CUSTOMS" -> 60;
            case "IN_TRANSIT" -> 50;
            case "PRE_TRANSIT" -> 40;
            case "DELIVERED" -> 30;
            default -> 0;
        };
    }

    private static boolean containsAny(String source, String... needles) {
        for (String needle : needles) if (source.contains(needle)) return true;
        return false;
    }
}
