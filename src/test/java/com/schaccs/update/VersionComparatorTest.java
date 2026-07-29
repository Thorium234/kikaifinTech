package com.schaccs.update;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VersionComparatorTest {

    @Test
    void equalVersions() {
        assertEquals(0, VersionComparator.compare("1.0.0", "1.0.0"));
        assertEquals(0, VersionComparator.compare("2.5.10", "2.5.10"));
        assertEquals(0, VersionComparator.compare("0.0.0", "0.0.0"));
    }

    @Test
    void newerIsGreater() {
        assertTrue(VersionComparator.compare("1.0.0", "2.0.0") < 0);
        assertTrue(VersionComparator.compare("1.0.0", "1.1.0") < 0);
        assertTrue(VersionComparator.compare("1.0.0", "1.0.1") < 0);
    }

    @Test
    void olderIsLess() {
        assertTrue(VersionComparator.compare("2.0.0", "1.0.0") > 0);
        assertTrue(VersionComparator.compare("1.1.0", "1.0.0") > 0);
        assertTrue(VersionComparator.compare("1.0.1", "1.0.0") > 0);
    }

    @Test
    void isNewer_returnsTrue() {
        assertTrue(VersionComparator.isNewer("1.0.0", "2.0.0"));
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.1"));
    }

    @Test
    void isNewer_returnsFalse() {
        assertFalse(VersionComparator.isNewer("2.0.0", "1.0.0"));
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.0"));
    }

    @Test
    void handlesVersionPrefixV() {
        assertTrue(VersionComparator.isNewer("1.0.0", "v2.0.0"));
        assertTrue(VersionComparator.isNewer("1.0.0", "v1.0.1"));
    }

    @Test
    void handlesPartialVersions() {
        assertTrue(VersionComparator.isNewer("1.0", "2.0"));
        assertEquals(0, VersionComparator.compare("1", "1.0.0"));
    }

    @Test
    void throwsOnInvalidVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> VersionComparator.compare("abc", "1.0.0"));
    }
}
