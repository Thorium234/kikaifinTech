package com.schaccs.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleConsumer;

public class DownloadManager {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final HttpClient client;

    public DownloadManager() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public CompletableFuture<Path> downloadInstaller(
            String downloadUrl, long expectedSize, DoubleConsumer progressCallback) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(downloadUrl))
            .timeout(TIMEOUT)
            .GET()
            .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new RuntimeException(
                        "Download failed with HTTP " + response.statusCode());
                }
                long contentLength = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElse(expectedSize);

                try {
                    Path tempFile = Files.createTempFile("Thorcash-Update-", ".exe");
                    try (InputStream in = response.body();
                         OutputStream out = Files.newOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        long totalBytesRead = 0;
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                            if (contentLength > 0 && progressCallback != null) {
                                progressCallback.accept(
                                    (double) totalBytesRead / contentLength);
                            }
                        }
                    }
                    long actualSize = Files.size(tempFile);
                    if (expectedSize > 0 && actualSize != expectedSize) {
                        Files.deleteIfExists(tempFile);
                        throw new RuntimeException(
                            "Downloaded file size mismatch: expected "
                            + expectedSize + " but got " + actualSize);
                    }
                    return tempFile;
                } catch (IOException e) {
                    throw new RuntimeException("Failed to save downloaded file", e);
                }
            });
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] data = Files.readAllBytes(file);
            byte[] hash = md.digest(data);
            return HexFormat.of().formatHex(hash).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static boolean verifyChecksum(Path file, String expectedSha256)
            throws IOException {
        String actual = sha256(file);
        return actual.equalsIgnoreCase(expectedSha256);
    }
}
