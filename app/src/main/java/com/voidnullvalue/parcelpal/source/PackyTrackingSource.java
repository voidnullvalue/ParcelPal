package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;
import com.voidnullvalue.parcelpal.network.SafeHttpClient;
import com.voidnullvalue.parcelpal.util.CarrierDetector;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class PackyTrackingSource implements TrackingSource {
    private final SourceRecipe recipe;
    private final SafeHttpClient httpClient;
    private final PackyJsonParser parser;

    public PackyTrackingSource(SourceRecipe recipe, SafeHttpClient httpClient, PackyJsonParser parser) {
        this.recipe = recipe;
        this.httpClient = httpClient;
        this.parser = parser;
    }

    @Override public String id() { return recipe.id; }
    @Override public String displayName() { return recipe.name; }
    @Override public String kind() { return recipe.kind; }
    @Override public Set<String> allowedHosts() { return recipe.hosts; }
    @Override public boolean supports(TrackingTarget target) { return recipe.supportsCarrier(target.carrierHint); }

    @Override
    public TrackingResult fetch(TrackingTarget target) throws IOException {
        String trackingNumber = CarrierDetector.normalizeTrackingNumber(target.trackingNumber);
        if (!trackingNumber.matches("1ST[0-9]{11}")) {
            throw new IOException("Unsupported 1ST tracking-number format");
        }

        String url = recipe.urlTemplate.replace("{tracking}", trackingNumber);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Referer", "https://packyapp.com/en/carriers/1st");
        SafeHttpClient.HttpResponse response = httpClient.get(url, recipe.hosts, headers);
        return parser.parse(response.body, trackingNumber, recipe.id, recipe.name);
    }
}
