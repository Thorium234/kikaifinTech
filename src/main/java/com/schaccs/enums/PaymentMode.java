package com.schaccs.enums;

import java.util.Arrays;
import java.util.List;

public enum PaymentMode {
    CASH("Cash", false),
    BANK_SLIP("Bank Slip", true),
    CHEQUE("Cheque", true),
    MPESA("M-Pesa", true),
    MONEY_ORDER("Money Order", true);

    private final String displayName;
    private final boolean allowed;

    PaymentMode(String displayName, boolean allowed) {
        this.displayName = displayName;
        this.allowed = allowed;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isAllowed() {
        return allowed;
    }

    /** Modes the school currently accepts (cash is excluded by policy). */
    public static List<PaymentMode> allowedModes() {
        return Arrays.stream(values()).filter(PaymentMode::isAllowed).toList();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
