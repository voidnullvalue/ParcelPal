package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;
import com.voidnullvalue.parcelpal.network.SafeHttpClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class GenericHtmlSource implements TrackingSource {
    private final SourceRecipe recipe;
    private final SafeHttpClient httpClient;
    private final HeuristicTrackingParser parser;

    public GenericHtmlSource(SourceRecipe recipe, SafeHttpClient httpClient, HeuristicTrackingParser parser) {
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
        String encoded = URLEncoder.encode(target.trackingNumber, StandardCharsets.UTF_8).replace("+", "%20");
        String url = recipe.urlTemplate.replace("{tracking}", encoded);
        SafeHttpClient.HttpResponse response = httpClient.get(url, recipe.hosts);
        TrackingResult result = parser.parse(response.body, target.trackingNumber, recipe.id, recipe.name);
        if (!result.isUseful()) {
            throw new IOException("Public response contained no usable tracking data; JavaScript, a challenge, or a changed page format may be required");
        }
        return result;
    }
}
