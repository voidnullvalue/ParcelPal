package com.voidnullvalue.parcelpal.source;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.voidnullvalue.parcelpal.model.TrackingResult;
import com.voidnullvalue.parcelpal.model.TrackingTarget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads a carrier's own tracking page in an offscreen, network-restricted browser.
 *
 * <p>Several carriers only publish tracking results through client-side rendering, so no plain HTTP
 * fetch can see them. This source creates a throwaway {@link WebView} for one lookup, restricts it
 * to the carrier's own hosts through {@link BrowserHostPolicy}, runs the source's extraction script
 * until it reports a result, then destroys every trace of the session.
 *
 * <p>The source is configured entirely from a {@link SourceRecipe}: the recipe names the extraction
 * script asset, the host suffixes the browser may reach, and the parser converts the script's JSON
 * into the common model. No browser UI is ever shown and no state is reused between lookups.
 */
public final class BrowserSource implements TrackingSource {
    private static final long PAGE_TIMEOUT_MS = 45_000;
    private static final long CALL_TIMEOUT_MS = 52_000;
    private static final long POLL_INTERVAL_MS = 600;

    private final Context context;
    private final SourceRecipe recipe;
    private final BrowserExtractionParser parser;
    private final BrowserHostPolicy hostPolicy;
    private final String extractionScript;

    public BrowserSource(Context context, SourceRecipe recipe, BrowserExtractionParser parser) {
        this.context = context.getApplicationContext();
        this.recipe = recipe;
        this.parser = parser;
        this.hostPolicy = new BrowserHostPolicy(recipe.browserHosts);
        if (recipe.script.isEmpty()) {
            throw new IllegalStateException("Browser source " + recipe.id + " declares no extraction script");
        }
        if (recipe.browserHosts.isEmpty()) {
            throw new IllegalStateException("Browser source " + recipe.id + " declares no browser hosts");
        }
        this.extractionScript = readAsset(this.context, recipe.script);
    }

    @Override public String id() { return recipe.id; }
    @Override public String displayName() { return recipe.name; }
    @Override public String kind() { return recipe.kind; }
    @Override public Set<String> allowedHosts() { return recipe.hosts; }
    @Override public int trust() { return recipe.trust; }
    @Override public boolean supports(TrackingTarget target) { return recipe.supportsAny(target.carrierCandidates); }

    @Override
    public TrackingResult fetch(TrackingTarget target) throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IOException(recipe.name + " browser lookup cannot run on the UI thread");
        }
        String encoded = URLEncoder.encode(target.trackingNumber, StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        String url = recipe.url(encoded);
        if (!hostPolicy.isAllowed(url)) {
            throw new IOException(recipe.name + " tracking URL is outside the approved browser hosts");
        }
        BrowserSession session = new BrowserSession(context, url, extractionScript, hostPolicy, recipe.name);
        new Handler(Looper.getMainLooper()).post(session::start);

        final boolean completed;
        try {
            completed = session.latch.await(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            session.cancel(recipe.name + " browser lookup was interrupted");
            throw new IOException(recipe.name + " browser lookup was interrupted", interrupted);
        }
        if (!completed) {
            session.cancel(recipe.name + " tracking page timed out");
            throw new IOException(recipe.name + " tracking page timed out");
        }
        String error = session.error.get();
        if (error != null) throw new IOException(error);
        String json = session.result.get();
        if (json == null || json.trim().isEmpty()) {
            throw new IOException(recipe.name + " browser returned no extraction data");
        }
        return parser.parse(json, target, recipe.id, recipe.name);
    }

    private static String readAsset(Context context, String name) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(name), StandardCharsets.UTF_8))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
            return output.toString();
        } catch (IOException error) {
            throw new IllegalStateException("Could not load extraction script " + name, error);
        }
    }

    private static String decodeJavascriptResult(String raw) throws Exception {
        if (raw == null || "null".equals(raw)) return "";
        return new JSONArray("[" + raw + "]").getString(0);
    }

    private static final class BrowserSession {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>();

        private final Context context;
        private final String url;
        private final String extractionScript;
        private final BrowserHostPolicy hostPolicy;
        private final String sourceName;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final AtomicBoolean done = new AtomicBoolean(false);
        private long deadline;
        private WebView webView;
        private CookieManager cookies;
        private boolean previousAcceptCookies;
        private boolean pollStarted;
        private String lastExtractionError = "";

        BrowserSession(Context context, String url, String extractionScript,
                       BrowserHostPolicy hostPolicy, String sourceName) {
            this.context = context;
            this.url = url;
            this.extractionScript = extractionScript;
            this.hostPolicy = hostPolicy;
            this.sourceName = sourceName;
        }

        void start() {
            if (done.get()) return;
            try {
                if (WebView.getCurrentWebViewPackage() == null) {
                    finishError("No Android System WebView provider is installed");
                    return;
                }
                cookies = CookieManager.getInstance();
                previousAcceptCookies = cookies.acceptCookie();
                cookies.setAcceptCookie(true);
                deadline = SystemClock.elapsedRealtime() + PAGE_TIMEOUT_MS;
                main.postDelayed(() -> finishError(timeoutMessage()), PAGE_TIMEOUT_MS);
                cookies.removeAllCookies(ignored -> createAndLoad());
            } catch (RuntimeException error) {
                finishError("Could not initialize the " + sourceName + " browser: " + safeMessage(error));
            }
        }

        @SuppressLint("SetJavaScriptEnabled")
        private void createAndLoad() {
            if (done.get()) return;
            try {
                webView = new WebView(context);
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setGeolocationEnabled(false);
                settings.setAllowFileAccess(false);
                settings.setAllowContentAccess(false);
                settings.setJavaScriptCanOpenWindowsAutomatically(false);
                settings.setSupportMultipleWindows(false);
                settings.setMediaPlaybackRequiresUserGesture(true);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                settings.setSaveFormData(false);
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    WebView.startSafeBrowsing(context, null);
                }
                webView.setWebViewClient(new LockedWebViewClient());
                webView.loadUrl(url);
            } catch (RuntimeException error) {
                finishError("Could not start the " + sourceName + " browser: " + safeMessage(error));
            }
        }

        void cancel(String message) {
            main.post(() -> finishError(message));
        }

        private void beginPolling() {
            if (pollStarted || done.get()) return;
            pollStarted = true;
            poll();
        }

        private void poll() {
            if (done.get()) return;
            if (SystemClock.elapsedRealtime() >= deadline) {
                finishError(timeoutMessage());
                return;
            }
            WebView current = webView;
            if (current == null) {
                finishError(sourceName + " browser closed before tracking data loaded");
                return;
            }
            current.evaluateJavascript(extractionScript, raw -> {
                if (done.get()) return;
                try {
                    String decoded = decodeJavascriptResult(raw);
                    if (!decoded.isEmpty()) {
                        JSONObject object = new JSONObject(decoded);
                        if (object.optBoolean("challenge", false) || object.optBoolean("ready", false)) {
                            finishSuccess(decoded);
                            return;
                        }
                    }
                } catch (Exception extractionError) {
                    lastExtractionError = safeMessage(extractionError);
                }
                main.postDelayed(this::poll, POLL_INTERVAL_MS);
            });
        }

        private String timeoutMessage() {
            if (lastExtractionError.isEmpty()) return sourceName + " tracking page timed out before status loaded";
            return sourceName + " tracking page timed out: " + lastExtractionError;
        }

        private void finishSuccess(String json) {
            if (!done.compareAndSet(false, true)) return;
            result.set(json);
            cleanup();
            latch.countDown();
        }

        private void finishError(String message) {
            if (!done.compareAndSet(false, true)) return;
            error.set(message);
            cleanup();
            latch.countDown();
        }

        private void cleanup() {
            WebView current = webView;
            webView = null;
            if (current != null) {
                try { current.stopLoading(); } catch (RuntimeException ignored) {}
                try { current.clearHistory(); } catch (RuntimeException ignored) {}
                try { current.clearCache(true); } catch (RuntimeException ignored) {}
                try { current.removeAllViews(); } catch (RuntimeException ignored) {}
                try { current.destroy(); } catch (RuntimeException ignored) {}
            }
            if (cookies != null) {
                try { cookies.removeAllCookies(null); } catch (RuntimeException ignored) {}
                try { cookies.flush(); } catch (RuntimeException ignored) {}
                try { cookies.setAcceptCookie(previousAcceptCookies); } catch (RuntimeException ignored) {}
            }
        }

        private final class LockedWebViewClient extends WebViewClient {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !hostPolicy.isAllowed(request.getUrl().toString());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String targetUrl) {
                return !hostPolicy.isAllowed(targetUrl);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (hostPolicy.isAllowed(request.getUrl().toString())) {
                    return super.shouldInterceptRequest(view, request);
                }
                return blockedResponse();
            }

            @Override
            @SuppressWarnings("deprecation")
            public WebResourceResponse shouldInterceptRequest(WebView view, String targetUrl) {
                if (hostPolicy.isAllowed(targetUrl)) return super.shouldInterceptRequest(view, targetUrl);
                return blockedResponse();
            }

            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                if (!hostPolicy.isAllowed(loadedUrl)) {
                    finishError(sourceName + " redirected to an unapproved host");
                    return;
                }
                beginPolling();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError webError) {
                if (request.isForMainFrame()) {
                    finishError(sourceName + " browser network error: " + webError.getDescription());
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) {
                    finishError(sourceName + " browser returned HTTP " + response.getStatusCode());
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError sslError) {
                handler.cancel();
                finishError(sourceName + " browser rejected an invalid TLS certificate");
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                finishError(sourceName + " browser process stopped unexpectedly");
                return true;
            }
        }

        private static WebResourceResponse blockedResponse() {
            return new WebResourceResponse("text/plain", "UTF-8",
                    new ByteArrayInputStream(new byte[0]));
        }

        private static String safeMessage(Throwable error) {
            String message = error.getMessage();
            return message == null || message.trim().isEmpty()
                    ? error.getClass().getSimpleName() : message.trim();
        }
    }
}
