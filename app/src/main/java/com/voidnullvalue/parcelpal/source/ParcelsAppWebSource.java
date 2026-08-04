package com.voidnullvalue.parcelpal.source;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ParcelsAppWebSource implements TrackingSource {
    private static final String ORIGIN = "https://parcelsapp.com";
    private static final String BOOTSTRAP_URL = ORIGIN + "/en";
    private static final String TRACK_URL = ORIGIN + "/api/v2/parcels";
    private static final int MAX_RESPONSE_BYTES = 3_000_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    private final SourceRecipe recipe;
    private final ParcelsAppJsonParser parser;

    public ParcelsAppWebSource(SourceRecipe recipe, ParcelsAppJsonParser parser) {
        this.recipe = recipe;
        this.parser = parser;
    }

    @Override public String id() { return recipe.id; }
    @Override public String displayName() { return recipe.name; }
    @Override public String kind() { return recipe.kind; }
    @Override public Set<String> allowedHosts() { return recipe.hosts; }
    @Override public boolean supports(TrackingTarget target) { return recipe.supportsCarrier(target.carrierHint); }

    @Override
    public TrackingResult fetch(TrackingTarget target) throws IOException {
        String slug = ParcelsAppProtocol.carrierSlug(target.carrierHint);
        ParcelsAppJsonParser.ResponseException lastResponseError = null;

        for (int sessionAttempt = 0; sessionAttempt < 2; sessionAttempt++) {
            Session session = bootstrapSession();
            try {
                TrackingResult result = requestTracking(session, target, slug);
                requireUseful(result);
                return result;
            } catch (ParcelsAppJsonParser.ResponseException first) {
                lastResponseError = first;
                if ("NO_TRACKER".equals(first.code) && !slug.isEmpty()) {
                    try {
                        TrackingResult fallback = requestTracking(session, target, "");
                        requireUseful(fallback);
                        return fallback;
                    } catch (ParcelsAppJsonParser.ResponseException second) {
                        lastResponseError = second;
                    }
                }
                if (!"RELOAD".equals(lastResponseError.code) || sessionAttempt > 0) throw lastResponseError;
            }
        }

        if (lastResponseError != null) throw lastResponseError;
        throw new IOException("ParcelsApp did not return tracking data");
    }

    private Session bootstrapSession() throws IOException {
        EphemeralCookieJar cookieJar = new EphemeralCookieJar();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
                .callTimeout(50, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .cookieJar(cookieJar)
                .retryOnConnectionFailure(true)
                .build();

        Request request = new Request.Builder()
                .url(BOOTSTRAP_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.8")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("DNT", "1")
                .header("Sec-GPC", "1")
                .get()
                .build();

        String html = execute(client, request, "text/html");
        Document document = Jsoup.parse(html, BOOTSTRAP_URL);
        Element token = document.selectFirst("meta[name=csrf-token]");
        String csrf = token == null ? "" : token.attr("content").trim();
        if (csrf.isEmpty()) throw new IOException("ParcelsApp session did not include a CSRF token");
        if (cookieJar.isEmpty()) throw new IOException("ParcelsApp session cookie was not established");
        return new Session(client, csrf);
    }

    private TrackingResult requestTracking(Session session, TrackingTarget target, String slug) throws IOException {
        FormBody.Builder form = new FormBody.Builder()
                .add("trackingId", ParcelsAppProtocol.encodedTrackingId(target.trackingNumber))
                .add("carrier", "Auto-Detect")
                .add("language", "en")
                .add("country", "Unknown")
                .add("platform", "web-android")
                .add("wd", "false")
                .add("c", "false")
                .add("p", "0")
                .add("l", "1");
        if (slug != null && !slug.isEmpty()) form.add("slug", slug);
        RequestBody body = form.build();

        Request request = new Request.Builder()
                .url(TRACK_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Language", "en-US,en;q=0.8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-CSRF-Token", session.csrf)
                .header("Origin", ORIGIN)
                .header("Referer", BOOTSTRAP_URL)
                .header("DNT", "1")
                .header("Sec-GPC", "1")
                .post(body)
                .build();

        String json = execute(session.client, request, "application/json");
        return parser.parse(json, target, recipe.id, recipe.name);
    }

    private static String execute(OkHttpClient client, Request request, String expectedType) throws IOException {
        requireParcelsAppHost(request.url());
        try (Response response = client.newCall(request).execute()) {
            if (response.isRedirect()) throw new IOException("Unexpected ParcelsApp redirect");
            if (!response.isSuccessful()) throw new IOException("ParcelsApp HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("ParcelsApp returned an empty response");
            long length = body.contentLength();
            if (length > MAX_RESPONSE_BYTES) throw new IOException("ParcelsApp response exceeds size limit");
            MediaType type = body.contentType();
            String contentType = type == null ? "" : type.toString();
            if (!contentType.toLowerCase().contains(expectedType.toLowerCase())) {
                throw new IOException("ParcelsApp returned unexpected content type: " + contentType);
            }
            byte[] bytes = body.bytes();
            if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("ParcelsApp response exceeds size limit");
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void requireUseful(TrackingResult result) throws IOException {
        if (!result.isUseful()) throw new IOException("ParcelsApp returned JSON without a credible tracking state");
    }

    private static void requireParcelsAppHost(HttpUrl url) throws IOException {
        if (!"https".equalsIgnoreCase(url.scheme()) || !"parcelsapp.com".equalsIgnoreCase(url.host())) {
            throw new IOException("Blocked ParcelsApp request outside the approved host");
        }
    }

    private static final class Session {
        final OkHttpClient client;
        final String csrf;

        Session(OkHttpClient client, String csrf) {
            this.client = client;
            this.csrf = csrf;
        }
    }

    private static final class EphemeralCookieJar implements CookieJar {
        private final List<Cookie> cookies = new ArrayList<>();

        @Override
        public synchronized void saveFromResponse(HttpUrl url, List<Cookie> responseCookies) {
            cookies.removeIf(cookie -> cookie.matches(url));
            cookies.addAll(responseCookies);
        }

        @Override
        public synchronized List<Cookie> loadForRequest(HttpUrl url) {
            long now = System.currentTimeMillis();
            cookies.removeIf(cookie -> cookie.expiresAt() < now);
            List<Cookie> matching = new ArrayList<>();
            for (Cookie cookie : cookies) if (cookie.matches(url)) matching.add(cookie);
            return Collections.unmodifiableList(matching);
        }

        synchronized boolean isEmpty() {
            return cookies.isEmpty();
        }
    }
}
