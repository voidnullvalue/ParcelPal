package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.util.CarrierDetector;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class ParcelsAppProtocol {
    private static final int TRACKING_SHIFT = 76;

    private ParcelsAppProtocol() {}

    static String encodedTrackingId(String rawTrackingNumber) {
        String normalized = CarrierDetector.normalizeTrackingNumber(rawTrackingNumber);
        String uriEncoded = encodeUriComponent(normalized);
        StringBuilder shifted = new StringBuilder(uriEncoded.length());
        for (int i = 0; i < uriEncoded.length(); i++) {
            char character = uriEncoded.charAt(i);
            shifted.append(character <= 126
                    ? (char) ((character + TRACKING_SHIFT) % 126)
                    : character);
        }
        return encodeUriComponent(shifted.toString());
    }

    static String carrierSlug(String carrierHint) {
        if (carrierHint == null) return "";
        return switch (carrierHint.trim().toLowerCase(Locale.US)) {
            case "usps" -> "usps";
            case "ups" -> "ups";
            case "fedex" -> "fedex";
            case "dhl", "dhl express" -> "dhl-express";
            case "dhl ecommerce" -> "dhl-global-mail";
            case "amazon logistics" -> "amazon-logistics";
            case "ontrac" -> "ontrac";
            case "lasership" -> "lasership";
            case "canada post" -> "canada-post";
            case "royal mail" -> "royal-mail";
            case "australia post" -> "australia-post";
            case "yunexpress" -> "yun-express";
            case "cainiao" -> "cainiao";
            case "4px" -> "4px";
            case "uniuni" -> "uniuni";
            default -> "";
        };
    }

    private static String encodeUriComponent(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length * 3);
        for (byte raw : bytes) {
            int valueByte = raw & 0xff;
            char character = (char) valueByte;
            if (isUriComponentSafe(character)) {
                encoded.append(character);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit((valueByte >>> 4) & 0xf, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(valueByte & 0xf, 16)));
            }
        }
        return encoded.toString();
    }

    private static boolean isUriComponentSafe(char character) {
        return character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '-'
                || character == '_'
                || character == '.'
                || character == '!'
                || character == '~'
                || character == '*'
                || character == '\''
                || character == '('
                || character == ')';
    }
}
