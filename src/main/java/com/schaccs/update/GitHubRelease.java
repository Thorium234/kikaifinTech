package com.schaccs.update;

import java.time.Instant;
import java.util.List;

public record GitHubRelease(
    String tagName,
    String name,
    String body,
    boolean prerelease,
    Instant createdAt,
    List<Asset> assets
) {
    public record Asset(
        String name,
        long size,
        String browserDownloadUrl
    ) {}
}
