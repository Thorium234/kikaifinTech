package com.schaccs.update;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GitHubApiClientTest {

    private final GitHubApiClient client = new GitHubApiClient();

    @Test
    void parseRelease_withAllFields() {
        String json = """
            {
                "tag_name": "v1.2.3",
                "name": "Release 1.2.3",
                "body": "Bug fixes and improvements",
                "prerelease": false,
                "created_at": "2025-06-15T10:00:00Z",
                "assets": [
                    {
                        "name": "ThorCash-Setup-1.2.3.exe",
                        "size": 104857600,
                        "browser_download_url": "https://github.com/Thorium234/kikaifinTech/releases/download/v1.2.3/ThorCash-Setup-1.2.3.exe"
                    },
                    {
                        "name": "ThorCash-1.2.3.msi",
                        "size": 104857600,
                        "browser_download_url": "https://github.com/Thorium234/kikaifinTech/releases/download/v1.2.3/ThorCash-1.2.3.msi"
                    }
                ]
            }
            """;

        GitHubRelease release = client.parseRelease(json);
        assertEquals("v1.2.3", release.tagName());
        assertEquals("Release 1.2.3", release.name());
        assertEquals("Bug fixes and improvements", release.body());
        assertFalse(release.prerelease());
        assertEquals(2, release.assets().size());
        assertEquals("ThorCash-Setup-1.2.3.exe", release.assets().get(0).name());
        assertEquals(104857600, release.assets().get(0).size());
    }

    @Test
    void parseRelease_withPrerelease() {
        String json = """
            {
                "tag_name": "v2.0.0-beta",
                "name": "Beta 2.0.0",
                "body": "Beta release",
                "prerelease": true,
                "created_at": "2025-07-01T00:00:00Z",
                "assets": []
            }
            """;

        GitHubRelease release = client.parseRelease(json);
        assertTrue(release.prerelease());
        assertTrue(release.assets().isEmpty());
    }

    @Test
    void parseRelease_tagNameWithoutVPrefix() {
        String json = """
            {
                "tag_name": "1.0.0",
                "name": "",
                "body": "",
                "prerelease": false,
                "created_at": "2025-01-01T00:00:00Z",
                "assets": []
            }
            """;

        GitHubRelease release = client.parseRelease(json);
        assertEquals("1.0.0", release.tagName());
    }

    @Test
    void parseRelease_noAssets() {
        String json = """
            {
                "tag_name": "v1.0.0",
                "name": "Initial Release",
                "body": "First stable release",
                "prerelease": false,
                "created_at": "2025-01-01T00:00:00Z"
            }
            """;

        GitHubRelease release = client.parseRelease(json);
        assertTrue(release.assets().isEmpty());
    }

    @Test
    void parseRelease_throwsOnInvalidJson() {
        assertThrows(RuntimeException.class,
            () -> client.parseRelease("not valid json"));
    }
}
