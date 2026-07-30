package com.schaccs.update;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VersionComparatorTest {

    @Test
    @DisplayName("equal versions return 0")
    void equalVersions() {
        assertEquals(0, VersionComparator.compare("1.0.0", "1.0.0"));
        assertEquals(0, VersionComparator.compare("2.5.10", "2.5.10"));
        assertEquals(0, VersionComparator.compare("0.0.0", "0.0.0"));
    }

    @Test
    @DisplayName("newer version is greater (returns < 0)")
    void newerIsGreater() {
        assertTrue(VersionComparator.compare("1.0.0", "2.0.0") < 0);
        assertTrue(VersionComparator.compare("1.0.0", "1.1.0") < 0);
        assertTrue(VersionComparator.compare("1.0.0", "1.0.1") < 0);
    }

    @Test
    @DisplayName("older version is less (returns > 0)")
    void olderIsLess() {
        assertTrue(VersionComparator.compare("2.0.0", "1.0.0") > 0);
        assertTrue(VersionComparator.compare("1.1.0", "1.0.0") > 0);
        assertTrue(VersionComparator.compare("1.0.1", "1.0.0") > 0);
    }

    @Test
    @DisplayName("isNewer returns true for newer version")
    void isNewer_returnsTrue() {
        assertTrue(VersionComparator.isNewer("1.0.0", "2.0.0"));
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.1"));
    }

    @Test
    @DisplayName("isNewer returns false for older or equal version")
    void isNewer_returnsFalse() {
        assertFalse(VersionComparator.isNewer("2.0.0", "1.0.0"));
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.0"));
    }

    @Test
    @DisplayName("handles version prefix 'v'")
    void handlesVersionPrefixV() {
        assertTrue(VersionComparator.isNewer("1.0.0", "v2.0.0"));
        assertTrue(VersionComparator.isNewer("1.0.0", "v1.0.1"));
    }

    @Test
    @DisplayName("handles partial versions (fewer segments)")
    void handlesPartialVersions() {
        assertTrue(VersionComparator.isNewer("1.0", "2.0"));
        assertEquals(0, VersionComparator.compare("1", "1.0.0"));
    }

    @Test
    @DisplayName("throws on invalid version string")
    void throwsOnInvalidVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> VersionComparator.compare("abc", "1.0.0"));
    }
}
