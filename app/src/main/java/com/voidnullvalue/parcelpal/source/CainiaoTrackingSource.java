package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;
import com.voidnullvalue.parcelpal.network.SafeHttpClient;
import com.voidnullvalue.parcelpal.util.CarrierDetector;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Cookie-free JSON lookup against Cainiao's global tracking endpoint.
 *
 * <p>The endpoint takes the tracking number as a query parameter and needs no session, key, or
 * browser. It is the only source that reports origin-side scans for AliExpress consignments, which
 * is why it runs alongside the number's issuing carrier rather than as a fallback behind it.
 */
public final class CainiaoTrackingSource implements TrackingSource {
    private final SourceRecipe recipe;
    private final SafeHttpClient httpClient;
    private final CainiaoJsonParser parser;

    public CainiaoTrackingSource(SourceRecipe recipe, SafeHttpClient httpClient, CainiaoJsonParser parser) {
        this.recipe = recipe;
        this.httpClient = httpClient;
        this.parser = parser;
    }

    @Override public String id() { return recipe.id; }
    @Override public String displayName() { return recipe.name; }
    @Override public String kind() { return recipe.kind; }
    @Override public Set<String> allowedHosts() { return recipe.hosts; }
    @Override public int trust() { return recipe.trust; }
    @Override public boolean supports(TrackingTarget target) { return recipe.supportsAny(target.carrierCandidates); }

    @Override
    public TrackingResult fetch(TrackingTarget target) throws IOException {
        String trackingNumber = CarrierDetector.normalizeTrackingNumber(target.trackingNumber);
        if (trackingNumber.isEmpty()) throw new IOException("Cainiao requires a tracking number");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json, text/plain, */*");
        headers.put("Referer", "https://global.cainiao.com/newDetail.htm?mailNoList=" + trackingNumber);
        SafeHttpClient.HttpResponse response = httpClient.get(recipe.url(trackingNumber), recipe.hosts, headers);
        return parser.parse(response.body, trackingNumber, recipe.id, recipe.name);
    }
}
