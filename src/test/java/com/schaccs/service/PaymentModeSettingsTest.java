package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.enums.PaymentMode;
import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentModeSettingsTest {

    @AfterEach
    void tearDown() {
        Database.getInstance().close();
    }

    @Test
    void newModesArePresentAndEnabledByDefault() {
        assertTrue(PaymentMode.PAYBILL.isAllowed(), "Paybill should be enabled by default");
        assertTrue(PaymentMode.TILL.isAllowed(), "Till should be enabled by default");
        assertTrue(PaymentMode.CASH.isAllowed(), "Cash should be enabled by default");
        assertTrue(PaymentMode.CASH_IN_KIND.isAllowed(), "Cash in Kind should be enabled by default");

        List<PaymentMode> modes = PaymentMode.allowedModes();
        assertTrue(modes.containsAll(Set.of(
                PaymentMode.PAYBILL, PaymentMode.TILL, PaymentMode.CASH, PaymentMode.CASH_IN_KIND)));
    }

    @Test
    void enabledPaymentModesPersistAndReload() {
        SchoolProfile p = AppConfig.getInstance().getSchoolProfile();
        Set<PaymentMode> original = Set.copyOf(p.getEnabledPaymentModes());
        try {
            p.setEnabledPaymentModes(List.of(PaymentMode.CASH, PaymentMode.PAYBILL, PaymentMode.TILL));
            PersistenceService.getInstance().saveAll();

            p.setEnabledPaymentModes(List.of(PaymentMode.BANK_SLIP));
            PersistenceService.getInstance().loadAll();

            assertEquals(Set.of(PaymentMode.CASH, PaymentMode.PAYBILL, PaymentMode.TILL),
                    AppConfig.getInstance().getSchoolProfile().getEnabledPaymentModes());
            assertTrue(PaymentMode.allowedModes().containsAll(
                    List.of(PaymentMode.CASH, PaymentMode.PAYBILL, PaymentMode.TILL)));
            assertFalse(PaymentMode.allowedModes().contains(PaymentMode.CHEQUE));
        } finally {
            p.setEnabledPaymentModes(original);
            PersistenceService.getInstance().saveAll();
        }
    }

    @Test
    void migrationV24IsApplied() throws Exception {
        List<String[]> history = Database.getInstance().migrationHistory();
        assertTrue(history.stream().anyMatch(row -> "24".equals(row[0])
                && "MigrationV24EnabledPaymentModes".equals(row[1])));
    }
}
