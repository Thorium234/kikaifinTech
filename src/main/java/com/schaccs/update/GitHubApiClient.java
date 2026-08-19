package com.schaccs.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitHubApiClient {

    private static final String API_URL =
        "https://api.github.com/repos/Thorium234/kikaifinTech/releases/latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient client;

    public GitHubApiClient() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    }

    public CompletableFuture<GitHubRelease> fetchLatestRelease() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ThorCash-App-Updater/1.0")
            .timeout(TIMEOUT)
            .GET()
            .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new RuntimeException(
                        "GitHub API returned " + response.statusCode());
                }
                return parseRelease(response.body());
            });
    }

    GitHubRelease parseRelease(String json) {
        try {
            String tagName = extractString(json, "tag_name");
            String name = extractString(json, "name");
            String body = extractString(json, "body");
            boolean prerelease = extractBoolean(json, "prerelease");
            Instant createdAt = Instant.parse(extractString(json, "created_at"));

            List<GitHubRelease.Asset> assets = new ArrayList<>();
            String assetsStr = extractRawArray(json, "assets");
            if (assetsStr != null) {
                List<String> objects = splitObjects(assetsStr);
                for (String obj : objects) {
                    String assetName = extractString(obj, "name");
                    long size = extractLong(obj, "size");
                    String url = extractString(obj, "browser_download_url");
                    if (!assetName.isEmpty()) {
                        assets.add(new GitHubRelease.Asset(assetName, size, url));
                    }
                }
            }

            return new GitHubRelease(tagName, name, body, prerelease, createdAt, assets);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse GitHub release JSON", e);
        }
    }

    private String extractString(String json, String key) {
        Pattern p = Pattern.compile(
            "\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\/", "/");
        }
        return "";
    }

    private boolean extractBoolean(String json, String key) {
        Pattern p = Pattern.compile(
            "\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(json);
        return m.find() && Boolean.parseBoolean(m.group(1));
    }

    private long extractLong(String json, String key) {
        Pattern p = Pattern.compile(
            "\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        return 0;
    }

    private String extractRawArray(String json, String key) {
        Pattern keyPat = Pattern.compile("\"" + key + "\"\\s*:");
        Matcher m = keyPat.matcher(json);
        if (!m.find()) return null;

        int start = m.end();
        int depth = 0;
        int arrStart = -1;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                if (depth == 0) arrStart = i;
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0 && arrStart >= 0) {
                    return json.substring(arrStart, i + 1);
                }
            }
        }
        return null;
    }

    private List<String> splitObjects(String array) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < array.length(); i++) {
            char c = array.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(array.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }
}
