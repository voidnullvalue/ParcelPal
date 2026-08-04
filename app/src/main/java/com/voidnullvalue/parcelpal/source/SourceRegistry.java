package com.voidnullvalue.parcelpal.source;

import android.content.Context;

import com.voidnullvalue.parcelpal.data.SourcePreferences;
import com.voidnullvalue.parcelpal.model.TrackingTarget;
import com.voidnullvalue.parcelpal.network.SafeHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SourceRegistry {
    private final SourcePreferences preferences;
    private final SafeHttpClient http = new SafeHttpClient();
    private final HeuristicTrackingParser parser = new HeuristicTrackingParser();
    private final ParcelsAppJsonParser parcelsAppParser = new ParcelsAppJsonParser();
    private final List<SourceRecipe> recipes;

    public SourceRegistry(Context context, SourcePreferences preferences) {
        this.preferences = preferences;
        this.recipes = loadRecipes(context);
    }

    public List<TrackingSource> sourcesFor(TrackingTarget target) {
        List<TrackingSource> direct = new ArrayList<>();
        List<TrackingSource> aggregators = new ArrayList<>();
        for (SourceRecipe recipe : recipes) {
            if (!preferences.sourceEnabled(recipe.id, recipe.kind)) continue;
            TrackingSource source = "parcelsapp".equals(recipe.id)
                    ? new ParcelsAppWebSource(recipe, parcelsAppParser)
                    : new GenericHtmlSource(recipe, http, parser);
            if (!source.supports(target)) continue;
            if ("direct".equals(recipe.kind)) direct.add(source); else aggregators.add(source);
        }
        direct.addAll(aggregators);
        return direct;
    }

    public List<SourceRecipe> recipes() { return recipes; }

    private static List<SourceRecipe> loadRecipes(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("sources.json"), StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            JSONArray array = new JSONObject(json.toString()).getJSONArray("sources");
            List<SourceRecipe> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                Set<String> hosts = strings(item.getJSONArray("hosts"));
                Set<String> carriers = item.has("carriers") ? strings(item.getJSONArray("carriers")) : Collections.emptySet();
                result.add(new SourceRecipe(
                        item.getString("id"),
                        item.getString("name"),
                        item.getString("kind"),
                        item.getString("url"),
                        hosts,
                        carriers
                ));
            }
            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load tracking source definitions", e);
        }
    }

    private static Set<String> strings(JSONArray array) {
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) result.add(array.optString(i));
        return result;
    }
}
