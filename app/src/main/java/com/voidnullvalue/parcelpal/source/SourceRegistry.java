package com.voidnullvalue.parcelpal.source;

import android.content.Context;

import com.voidnullvalue.parcelpal.data.SourcePreferences;
import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;
import com.voidnullvalue.parcelpal.network.SafeHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SourceRegistry {
    private final Context context;
    private final SourcePreferences preferences;
    private final SafeHttpClient http = new SafeHttpClient();
    private final HeuristicTrackingParser parser = new HeuristicTrackingParser();
    private final ParcelsAppJsonParser parcelsAppParser = new ParcelsAppJsonParser();
    private final PackyJsonParser packyParser = new PackyJsonParser();
    private final CainiaoJsonParser cainiaoParser = new CainiaoJsonParser();
    private final UspsDomParser uspsParser = new UspsDomParser();
    private final List<SourceRecipe> recipes;

    public SourceRegistry(Context context, SourcePreferences preferences) {
        this.context = context.getApplicationContext();
        this.preferences = preferences;
        this.recipes = loadRecipes(this.context);
    }

    /**
     * Every enabled source that could hold data for this target, most trusted first.
     *
     * <p>A tracking number often exists in more than one system at once, so this returns all
     * matching sources rather than a fallback chain. The caller queries them together and merges
     * whatever comes back.
     */
    public List<TrackingSource> sourcesFor(TrackingTarget target) {
        List<TrackingSource> carrierOwned = new ArrayList<>();
        List<TrackingSource> aggregators = new ArrayList<>();
        for (SourceRecipe recipe : recipes) {
            if (!recipe.fetchable()) continue;
            if (!preferences.sourceEnabled(recipe.id, recipe.kind)) continue;
            if (!recipe.supportsAny(target.carrierCandidates)) continue;
            TrackingSource source = create(recipe);
            if (!source.supports(target)) continue;
            if (recipe.carrierOwned()) carrierOwned.add(source); else aggregators.add(source);
        }
        Comparator<TrackingSource> byTrust = Comparator.comparingInt((TrackingSource source) -> -source.trust());
        carrierOwned.sort(byTrust);
        aggregators.sort(byTrust);
        carrierOwned.addAll(aggregators);
        return carrierOwned;
    }

    /** Carrier and aggregator pages ParcelPal cannot read itself but the user can open in a browser. */
    public List<SourceRecipe> linksFor(TrackingTarget target) {
        List<SourceRecipe> links = new ArrayList<>();
        for (SourceRecipe recipe : recipes) {
            if (!SourceRecipe.KIND_LINK.equals(recipe.kind)) continue;
            if (!recipe.supportsAny(target.carrierCandidates)) continue;
            links.add(recipe);
        }
        return Collections.unmodifiableList(links);
    }

    public List<SourceRecipe> recipes() { return recipes; }

    /** Recipes ParcelPal fetches during a refresh, in the order they are offered in settings. */
    public List<SourceRecipe> fetchableRecipes() {
        List<SourceRecipe> fetchable = new ArrayList<>();
        for (SourceRecipe recipe : recipes) if (recipe.fetchable()) fetchable.add(recipe);
        return Collections.unmodifiableList(fetchable);
    }

    public Map<String, Integer> trustBySourceId() {
        Map<String, Integer> trust = new LinkedHashMap<>();
        for (SourceRecipe recipe : recipes) trust.put(recipe.id, recipe.trust);
        return Collections.unmodifiableMap(trust);
    }

    private TrackingSource create(SourceRecipe recipe) {
        try {
            if (SourceRecipe.KIND_BROWSER.equals(recipe.kind)) {
                return new BrowserSource(context, recipe, browserParser(recipe));
            }
            if ("cainiao".equals(recipe.id)) {
                return new CainiaoTrackingSource(recipe, http, cainiaoParser);
            }
            if ("parcelsapp".equals(recipe.id)) {
                return new ParcelsAppWebSource(recipe, parcelsAppParser);
            }
            if ("packy_1st".equals(recipe.id)) {
                return new PackyTrackingSource(recipe, http, packyParser);
            }
            return new GenericHtmlSource(recipe, http, parser);
        } catch (RuntimeException misconfigured) {
            return new UnavailableSource(recipe, misconfigured);
        }
    }

    private BrowserExtractionParser browserParser(SourceRecipe recipe) {
        if ("usps".equals(recipe.id)) return uspsParser;
        throw new IllegalStateException("No extraction parser is registered for browser source " + recipe.id);
    }

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
                result.add(new SourceRecipe(
                        item.getString("id"),
                        item.getString("name"),
                        item.getString("kind"),
                        item.getString("url"),
                        strings(item.optJSONArray("hosts")),
                        strings(item.optJSONArray("carriers")),
                        strings(item.optJSONArray("browserHosts")),
                        item.optString("script", ""),
                        item.optInt("trust", 0)
                ));
            }
            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load tracking source definitions", e);
        }
    }

    private static Set<String> strings(JSONArray array) {
        Set<String> result = new LinkedHashSet<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    /** Stands in for a recipe that could not be built, so the failure appears in the source audit. */
    private static final class UnavailableSource implements TrackingSource {
        private final SourceRecipe recipe;
        private final RuntimeException cause;

        UnavailableSource(SourceRecipe recipe, RuntimeException cause) {
            this.recipe = recipe;
            this.cause = cause;
        }

        @Override public String id() { return recipe.id; }
        @Override public String displayName() { return recipe.name; }
        @Override public String kind() { return recipe.kind; }
        @Override public Set<String> allowedHosts() { return recipe.hosts; }
        @Override public int trust() { return recipe.trust; }
        @Override public boolean supports(TrackingTarget target) { return true; }

        @Override
        public TrackingResult fetch(TrackingTarget target) throws IOException {
            String message = cause.getMessage();
            throw new IOException(message == null || message.trim().isEmpty()
                    ? recipe.name + " is misconfigured" : message, cause);
        }
    }
}
