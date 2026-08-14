package com.schaccs.enums;

import com.schaccs.config.AppConfig;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public enum PaymentMode {
    CASH("Cash"),
    BANK_SLIP("Bank Slip"),
    CHEQUE("Cheque"),
    MPESA("M-Pesa"),
    MONEY_ORDER("Money Order"),
    PAYBILL("Paybill"),
    TILL("Till"),
    CASH_IN_KIND("Cash in Kind");

    private final String displayName;

    PaymentMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Whether this mode is currently accepted by the school (configured in Settings). */
    public boolean isAllowed() {
        return AppConfig.getInstance().getSchoolProfile().getEnabledPaymentModes().contains(this);
    }

    /** Modes the school currently accepts, in declaration order. */
    public static List<PaymentMode> allowedModes() {
        return Arrays.stream(values()).filter(PaymentMode::isAllowed).toList();
    }

    /** Parse persisted mode names, ignoring unknown/blank entries. */
    public static Set<PaymentMode> fromNames(Collection<String> names) {
        Set<PaymentMode> result = new LinkedHashSet<>();
        if (names != null) {
            for (String name : names) {
                if (name == null || name.isBlank()) continue;
                try {
                    result.add(valueOf(name.trim()));
                } catch (IllegalArgumentException ignored) {
                    // Unknown stored name: skip it.
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
