package com.voidnullvalue.parcelpal.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class SafeHttpClient {
    private static final int MAX_RESPONSE_BYTES = 3_000_000;
    private static final int MAX_REDIRECTS = 3;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .retryOnConnectionFailure(true)
            .build();

    public HttpResponse get(String url, Set<String> allowedHosts) throws IOException {
        return get(url, allowedHosts, Collections.emptyMap(), 0);
    }

    public HttpResponse get(String url, Set<String> allowedHosts, Map<String, String> extraHeaders) throws IOException {
        return get(url, allowedHosts, extraHeaders, 0);
    }

    private HttpResponse get(String url, Set<String> allowedHosts, Map<String, String> extraHeaders, int redirectCount) throws IOException {
        HttpUrl parsed;
        try {
            parsed = HttpUrl.get(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid source URL", e);
        }
        if (!"https".equalsIgnoreCase(parsed.scheme())) throw new IOException("Cleartext source URL blocked");
        requireAllowedHost(parsed.host(), allowedHosts);

        Request.Builder request = new Request.Builder()
                .url(parsed)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 ParcelPal/1.0")
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.1")
                .header("Accept-Language", "en-US,en;q=0.8")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("DNT", "1")
                .header("Sec-GPC", "1");
        for (Map.Entry<String, String> header : extraHeaders.entrySet()) request.header(header.getKey(), header.getValue());

        try (Response response = client.newCall(request.build()).execute()) {
            if (response.isRedirect()) {
                if (redirectCount >= MAX_REDIRECTS) throw new IOException("Too many redirects");
                String location = response.header("Location");
                if (location == null) throw new IOException("Redirect without Location header");
                HttpUrl next = parsed.resolve(location);
                if (next == null) throw new IOException("Invalid redirect URL");
                requireAllowedHost(next.host(), allowedHosts);
                return get(next.toString(), allowedHosts, extraHeaders, redirectCount + 1);
            }
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty response");
            long declared = body.contentLength();
            if (declared > MAX_RESPONSE_BYTES) throw new IOException("Response exceeds size limit");
            Map<String, String> headers = new LinkedHashMap<>();
            for (String name : response.headers().names()) headers.put(name, response.header(name, ""));
            return new HttpResponse(
                    response.request().url().toString(),
                    response.header("Content-Type", ""),
                    readLimited(body.byteStream()),
                    headers
            );
        }
    }

    private static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) throw new IOException("Response exceeds size limit");
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void requireAllowedHost(String host, Set<String> allowedHosts) throws IOException {
        String normalized = host.toLowerCase(Locale.US);
        for (String allowed : allowedHosts) {
            String exact = allowed.toLowerCase(Locale.US).trim();
            if (normalized.equals(exact)) return;
        }
        throw new IOException("Blocked request to unapproved host: " + host);
    }

    public static final class HttpResponse {
        public final String finalUrl;
        public final String contentType;
        public final String body;
        public final Map<String, String> headers;

        HttpResponse(String finalUrl, String contentType, String body, Map<String, String> headers) {
            this.finalUrl = finalUrl;
            this.contentType = contentType;
            this.body = body;
            this.headers = Collections.unmodifiableMap(headers);
        }
    }
}
