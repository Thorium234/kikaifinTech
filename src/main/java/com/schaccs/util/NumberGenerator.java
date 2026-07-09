package com.schaccs.util;

import com.schaccs.config.AppConfig;

public final class NumberGenerator {

    private NumberGenerator() {
    }

    public static long nextReceiptNumber() {
        return AppConfig.getInstance().getSchoolProfile().allocateReceiptNumber();
    }
}
