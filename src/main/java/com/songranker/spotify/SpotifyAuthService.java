package com.songranker.spotify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Drives the Spotify "Authorization Code with PKCE" flow, which is the
 * recommended flow for a desktop app: no client secret is embedded in the
 * binary, and the user logs in through their real browser.
 *
 * <p>Flow: open the system browser to Spotify's authorize page -&gt; user logs
 * in and approves -&gt; Spotify redirects to a tiny local HTTP server this class
 * spins up on {@code 127.0.0.1:8888} -&gt; we exchange the returned code for
 * tokens -&gt; tokens are cached via {@link TokenStore} and refreshed as needed.</p>
 */
public final class SpotifyAuthService {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final TokenStore tokenStore = new TokenStore();

    /** Returns a valid access token, running the login flow if necessary. */
    public String getValidAccessToken() throws IOException, InterruptedException {
        SpotifyTokens tokens = tokenStore.load();
        if (tokens != null && !tokens.grants(SpotifyConfig.SCOPES)) {
            // Saved before a newer feature needed extra permissions (or before scopes were tracked):
            // the old token would just 403 with "Insufficient client scope", so log in again once.
            tokenStore.clear();
            tokens = null;
        }
        if (tokens == null) {
            tokens = runLoginFlow();
        } else if (tokens.isExpired()) {
            try {
                tokens = refresh(tokens);
            } catch (IOException refreshFailed) {
                // The refresh token itself is dead (expired/revoked). The stored tokens are no
                // good anymore, so clear them and send the user through a fresh login instead of
                // surfacing a raw token-refresh error they can't do anything about.
                tokenStore.clear();
                tokens = runLoginFlow();
            }
        }
        return tokens.accessToken;
    }

    public void logout() {
        tokenStore.clear();
    }

    /** Whether we have stored tokens at all. This doesn't guarantee they're still valid or unrevoked. */
    public boolean isLoggedIn() {
        return tokenStore.load() != null;
    }

    private SpotifyTokens runLoginFlow() throws IOException, InterruptedException {
        if (SpotifyConfig.CLIENT_ID.isBlank()) {
            throw new IllegalStateException(
                    "No Spotify client ID found. Set the SPOTIFY_CLIENT_ID environment variable to the " +
                    "client ID of your own app (https://developer.spotify.com/dashboard), " +
                    "then restart Song Ranker.");
        }

        String verifier = PkceUtil.generateCodeVerifier();
        String challenge = PkceUtil.deriveCodeChallenge(verifier);
        String state = PkceUtil.generateState();

        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        // Bound to the loopback address, so only this computer can reach the callback.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", SpotifyConfig.CALLBACK_PORT), 0);
        server.createContext("/callback", exchange -> handleCallback(exchange, state, codeFuture));
        server.start();

        try {
            String authorizeUrl = buildAuthorizeUrl(challenge, state);
            openInBrowser(authorizeUrl);

            // Waits for the user to finish in the browser. After 5 minutes the attempt is dropped.
            String code = codeFuture.get(5, TimeUnit.MINUTES);
            return exchangeCodeForTokens(code, verifier);
        } catch (Exception e) {
            throw new IOException("Spotify login did not complete", e);
        } finally {
            // Always release port 8888, also after a failed or abandoned login.
            server.stop(0);
        }
    }

    private void handleCallback(HttpExchange exchange, String expectedState, CompletableFuture<String> codeFuture)
            throws IOException {
        try {
            java.util.Map<String, String> params = parseQuery(exchange.getRequestURI());
            String responseBody;
            // state is a random value made for this attempt and sent in the authorize URL.
            // A callback that does not carry the same value did not come from this attempt.
            if (!expectedState.equals(params.get("state"))) {
                responseBody = callbackPage(false, "Login couldn't be verified",
                        "Something didn't match up between this login attempt and Song Ranker. Go back to the app and try connecting again.");
                codeFuture.completeExceptionally(new IOException("OAuth state mismatch"));
            } else if (params.containsKey("error")) {
                responseBody = callbackPage(false, "Login cancelled",
                        "Spotify reported: " + params.get("error") + ". You can close this tab and try again from Song Ranker.");
                codeFuture.completeExceptionally(new IOException("Spotify returned error: " + params.get("error")));
            } else {
                responseBody = callbackPage(true, "You're connected",
                        "Song Ranker is linked to your Spotify account. You can close this tab and go back to the app.");
                codeFuture.complete(params.get("code"));
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }

    /** A small self-contained HTML page shown in the user's browser after the Spotify redirect. */
    private static String callbackPage(boolean success, String heading, String message) {
        String accent = success ? "#1DB954" : "#e74c3c";
        String icon = success ? "&#10003;" : "&#10005;";
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Song Ranker</title><style>"
                + "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:#121212;font-family:'Segoe UI',Arial,sans-serif;color:#f2f2f2;}"
                + ".card{text-align:center;padding:40px 48px;}"
                + ".badge{width:64px;height:64px;border-radius:50%;background:" + accent + ";color:#121212;"
                + "font-size:32px;line-height:64px;margin:0 auto 20px;font-weight:bold;}"
                + "h1{font-size:22px;margin:0 0 10px;}"
                + "p{font-size:14px;color:#b3b3b3;max-width:360px;margin:0 auto;}"
                + "</style></head><body><div class=\"card\">"
                + "<div class=\"badge\">" + icon + "</div>"
                + "<h1>" + heading + "</h1><p>" + message + "</p>"
                + "</div></body></html>";
    }

    private String buildAuthorizeUrl(String codeChallenge, String state) {
        String query = "response_type=code"
                + "&client_id=" + enc(SpotifyConfig.CLIENT_ID)
                + "&scope=" + enc(SpotifyConfig.SCOPES)
                + "&redirect_uri=" + enc(SpotifyConfig.REDIRECT_URI)
                + "&state=" + enc(state)
                + "&code_challenge_method=S256"
                + "&code_challenge=" + enc(codeChallenge);
        return "https://accounts.spotify.com/authorize?" + query;
    }

    private SpotifyTokens exchangeCodeForTokens(String code, String verifier) throws IOException, InterruptedException {
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(SpotifyConfig.REDIRECT_URI)
                + "&client_id=" + enc(SpotifyConfig.CLIENT_ID)
                // The verifier proves this request comes from the app that started the login.
                // It replaces a client secret.
                + "&code_verifier=" + enc(verifier);
        SpotifyTokens tokens = postForTokens(form);
        tokenStore.save(tokens);
        return tokens;
    }

    private SpotifyTokens refresh(SpotifyTokens old) throws IOException, InterruptedException {
        String form = "grant_type=refresh_token"
                + "&refresh_token=" + enc(old.refreshToken)
                + "&client_id=" + enc(SpotifyConfig.CLIENT_ID);
        SpotifyTokens fresh = postForTokens(form);
        if (fresh.refreshToken == null || fresh.refreshToken.isBlank()) {
            fresh.refreshToken = old.refreshToken; // Spotify doesn't always rotate it
        }
        if (fresh.scope == null) fresh.scope = old.scope;
        tokenStore.save(fresh);
        return fresh;
    }

    private SpotifyTokens postForTokens(String form) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Spotify token request failed (" + response.statusCode() + "): " + response.body());
        }
        JsonNode json = mapper.readTree(response.body());
        String accessToken = json.get("access_token").asText();
        String refreshToken = json.has("refresh_token") ? json.get("refresh_token").asText() : null;
        int expiresIn = json.get("expires_in").asInt();
        SpotifyTokens tokens = new SpotifyTokens(accessToken, refreshToken, expiresIn);
        tokens.scope = json.path("scope").asText(null);
        return tokens;
    }

    private static void openInBrowser(String url) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url));
        } else {
            System.out.println("Open this URL to log in to Spotify:\n" + url);
        }
    }

    private static java.util.Map<String, String> parseQuery(URI uri) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        String query = uri.getRawQuery();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) continue;
            String key = java.net.URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = java.net.URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            map.put(key, value);
        }
        return map;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
