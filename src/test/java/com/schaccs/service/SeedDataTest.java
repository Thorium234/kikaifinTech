package com.schaccs.service;

import com.schaccs.service.setup.DemoDataSeeder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SeedDataTest {

    @Test
    void runDemoDataSeeder() {
        assertDoesNotThrow(() -> DemoDataSeeder.seed());
    }
}
