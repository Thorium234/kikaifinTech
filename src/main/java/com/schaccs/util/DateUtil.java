package com.schaccs.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class DateUtil {

    public static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private DateUtil() {
    }

    public static String format(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY);
    }

    public static String formatLong(LocalDate date) {
        return date == null ? "" : date.format(LONG);
    }
}
