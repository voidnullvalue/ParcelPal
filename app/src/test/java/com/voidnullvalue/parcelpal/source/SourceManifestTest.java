package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingTarget;
import com.voidnullvalue.parcelpal.util.CarrierDetector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guards {@code assets/sources.json} against the mistakes that silently disable a source: a URL
 * without the tracking placeholder, a host that does not match the URL, a browser source with no
 * extraction script, or a duplicate id.
 */
public final class SourceManifestTest {
    private final List<SourceRecipe> recipes = loadRecipes();

    @Test public void everyRecipeIsCompleteAndConsistent() {
        Set<String> ids = new HashSet<>();
        for (SourceRecipe recipe : recipes) {
            assertTrueMessage("duplicate source id " + recipe.id, ids.add(recipe.id));
            assertTrueMessage(recipe.id + " has no name", !recipe.name.isEmpty());
            assertTrueMessage(recipe.id + " has an unknown kind: " + recipe.kind, knownKind(recipe.kind));
            assertTrueMessage(recipe.id + " has no {tracking} placeholder",
                    recipe.urlTemplate.contains("{tracking}"));
            assertTrueMessage(recipe.id + " is not HTTPS", recipe.urlTemplate.startsWith("https://"));
            assertTrueMessage(recipe.id + " declares no hosts", !recipe.hosts.isEmpty());

            String host = URI.create(recipe.url("TEST123")).getHost();
            assertTrueMessage(recipe.id + " fetches " + host + " which is not in its host allowlist",
                    recipe.hosts.contains(host));
        }
    }

    @Test public void browserSourcesDeclareAScriptAndTheirOwnHosts() {
        for (SourceRecipe recipe : recipes) {
            if (!SourceRecipe.KIND_BROWSER.equals(recipe.kind)) continue;
            assertTrueMessage(recipe.id + " declares no extraction script", !recipe.script.isEmpty());
            assertTrueMessage(recipe.id + " declares no browser hosts", !recipe.browserHosts.isEmpty());
            assertTrueMessage(recipe.id + " ships no " + recipe.script,
                    Files.exists(assetsDirectory().resolve(recipe.script)));

            BrowserHostPolicy policy = new BrowserHostPolicy(recipe.browserHosts);
            assertTrueMessage(recipe.id + " cannot load its own tracking URL",
                    policy.isAllowed(recipe.url("TEST123")));
        }
    }

    @Test public void fetchedSourcesAreRankedByTrust() {
        for (SourceRecipe recipe : recipes) {
            if (!recipe.fetchable()) continue;
            assertTrueMessage(recipe.id + " has no trust score", recipe.trust > 0);
        }
    }

    @Test public void theOneStFormatReachesBothItsCarrierAndCainiao() {
        TrackingTarget target = new TrackingTarget("1ST06013631493", CarrierDetector.AUTO_DETECT);
        List<String> matched = new ArrayList<>();
        for (SourceRecipe recipe : recipes) {
            if (recipe.fetchable() && recipe.supportsAny(target.carrierCandidates)) matched.add(recipe.id);
        }

        assertTrueMessage("Packy must still answer for 1ST: " + matched, matched.contains("packy_1st"));
        assertTrueMessage("Cainiao must answer for 1ST: " + matched, matched.contains("cainiao"));
        assertTrueMessage("ParcelsApp is carrier agnostic: " + matched, matched.contains("parcelsapp"));
    }

    @Test public void aUspsNumberIsNotSentToChineseAggregators() {
        TrackingTarget target = new TrackingTarget("9400111206213785678901", CarrierDetector.AUTO_DETECT);
        for (SourceRecipe recipe : recipes) {
            if (!recipe.fetchable()) continue;
            if (!"cainiao".equals(recipe.id)) continue;
            assertTrueMessage("Cainiao must not be queried for a plain USPS number",
                    !recipe.supportsAny(target.carrierCandidates));
        }
    }

    private static boolean knownKind(String kind) {
        return SourceRecipe.KIND_DIRECT.equals(kind) || SourceRecipe.KIND_BROWSER.equals(kind)
                || SourceRecipe.KIND_AGGREGATOR.equals(kind) || SourceRecipe.KIND_LINK.equals(kind);
    }

    private static void assertTrueMessage(String message, boolean condition) {
        org.junit.Assert.assertTrue(message, condition);
    }

    private static List<SourceRecipe> loadRecipes() {
        try {
            String json = new String(Files.readAllBytes(assetsDirectory().resolve("sources.json")),
                    StandardCharsets.UTF_8);
            JSONArray array = new JSONObject(json).getJSONArray("sources");
            List<SourceRecipe> recipes = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                recipes.add(new SourceRecipe(
                        item.getString("id"),
                        item.getString("name"),
                        item.getString("kind"),
                        item.getString("url"),
                        strings(item.optJSONArray("hosts")),
                        strings(item.optJSONArray("carriers")),
                        strings(item.optJSONArray("browserHosts")),
                        item.optString("script", ""),
                        item.optInt("trust", 0)));
            }
            return recipes;
        } catch (IOException | JSONException unreadable) {
            // Android's org.json declares JSONException as checked, unlike the desktop artifact.
            throw new IllegalStateException("Could not read sources.json", unreadable);
        }
    }

    private static Set<String> strings(JSONArray array) {
        Set<String> values = new java.util.LinkedHashSet<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    /** Unit tests run from the module directory under Gradle and from the repository root locally. */
    private static Path assetsDirectory() {
        for (String candidate : new String[]{"src/main/assets", "app/src/main/assets"}) {
            Path path = Paths.get(candidate);
            if (Files.isDirectory(path)) return path;
        }
        throw new IllegalStateException("Could not locate app assets from " + Paths.get("").toAbsolutePath());
    }
}
