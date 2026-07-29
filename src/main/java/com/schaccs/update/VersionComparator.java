package com.schaccs.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionComparator {

    private static final Pattern VERSION_PATTERN =
            Pattern.compile("^(\\d+)(?:\\.(\\d+)(?:\\.(\\d+))?)?");

    private VersionComparator() {}

    public static int compare(String v1, String v2) {
        int[] p1 = parse(v1);
        int[] p2 = parse(v2);
        if (p1 == null || p2 == null) {
            throw new IllegalArgumentException(
                "Versions must start with semantic versioning (e.g. 1.2.3)");
        }
        for (int i = 0; i < 3; i++) {
            if (p1[i] != p2[i]) {
                return Integer.compare(p1[i], p2[i]);
            }
        }
        return 0;
    }

    public static boolean isNewer(String current, String latest) {
        return compare(latest, current) > 0;
    }

    private static int[] parse(String version) {
        String cleaned = version.trim().replaceFirst("^[vV]", "");
        Matcher m = VERSION_PATTERN.matcher(cleaned);
        if (!m.find()) return null;
        return new int[] {
            Integer.parseInt(m.group(1)),
            m.group(2) != null ? Integer.parseInt(m.group(2)) : 0,
            m.group(3) != null ? Integer.parseInt(m.group(3)) : 0
        };
    }
}
