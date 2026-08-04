package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceCoreTest {

    @AfterEach
    void tearDown() {
        Database.getInstance().close();
    }

    @Test
    void migrationHistoryContainsAppliedMigrations() throws Exception {
        List<String[]> history = Database.getInstance().migrationHistory();

        assertFalse(history.isEmpty());
        assertTrue(history.stream().anyMatch(row -> "1".equals(row[0]) && "MigrationV1SchoolSettingsDiscount".equals(row[1])));
        assertTrue(history.stream().anyMatch(row -> "2".equals(row[0]) && "MigrationV2ReceiptReversed".equals(row[1])));
        assertTrue(history.stream().anyMatch(row -> "3".equals(row[0]) && "MigrationV3SchoolLogoPath".equals(row[1])));
        assertTrue(history.stream().anyMatch(row -> "4".equals(row[0]) && "MigrationV4ReceiptStampSignaturePaths".equals(row[1])));
        assertTrue(history.stream().anyMatch(row -> "18".equals(row[0]) && "MigrationV18BackfillReceiptHashes".equals(row[1])));
    }

    @Test
    void schoolSettingsPersistAndReloadBrandingFields() {
        SchoolProfile profile = AppConfig.getInstance().getSchoolProfile();
        String marker = "Test School " + UUID.randomUUID();
        profile.setSchoolName(marker);
        profile.setLocation("Test Location");
        profile.setPrincipal("Test Principal");
        profile.setBankName("Test Bank");
        profile.setBankAccount("1234567890");
        profile.setPayBill("111222");
        profile.setPayBillAccount("ABC123");
        profile.setAcademicYear(2030);
        profile.setNextReceiptNumber(9000);
        profile.setNextVoucherNumber(8000);
        profile.setCashPolicy("Policy Text");
        profile.setSiblingDiscountEnabled(true);
        profile.setSiblingDiscountRate(CurrencyConfig.money("0.15"));
        profile.setLogoPath("/tmp/logo-" + UUID.randomUUID() + ".png");
        profile.setStampPath("/tmp/stamp-" + UUID.randomUUID() + ".png");
        profile.setSignaturePath("/tmp/signature-" + UUID.randomUUID() + ".png");
        AppConfig.getInstance().setCurrentUser("PersistenceTester");

        PersistenceService.getInstance().saveAll();

        profile.setSchoolName("Changed Name");
        profile.setLogoPath(null);
        profile.setStampPath(null);
        profile.setSignaturePath(null);
        AppConfig.getInstance().setCurrentUser("ChangedUser");

        PersistenceService.getInstance().loadAll();

        SchoolProfile reloaded = AppConfig.getInstance().getSchoolProfile();
        assertEquals(marker, reloaded.getSchoolName());
        assertEquals("Test Location", reloaded.getLocation());
        assertEquals("Test Principal", reloaded.getPrincipal());
        assertEquals("Test Bank", reloaded.getBankName());
        assertEquals("1234567890", reloaded.getBankAccount());
        assertEquals("111222", reloaded.getPayBill());
        assertEquals("ABC123", reloaded.getPayBillAccount());
        assertEquals(2030, reloaded.getAcademicYear());
        assertEquals(9000, reloaded.getNextReceiptNumber());
        assertEquals(8000, reloaded.getNextVoucherNumber());
        assertEquals("Policy Text", reloaded.getCashPolicy());
        assertTrue(reloaded.isSiblingDiscountEnabled());
        assertEquals(CurrencyConfig.money("0.15"), reloaded.getSiblingDiscountRate());
        assertNotNull(reloaded.getLogoPath());
        assertNotNull(reloaded.getStampPath());
        assertNotNull(reloaded.getSignaturePath());
        assertEquals("PersistenceTester", AppConfig.getInstance().getCurrentUser());
    }
}
