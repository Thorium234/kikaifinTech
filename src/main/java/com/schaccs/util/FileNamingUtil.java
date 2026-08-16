package com.schaccs.util;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Builds descriptive default names for save dialogs, e.g.
 * {@code Kikay_FinTech_Fee_Balances_2026-08-16.pdf}, so every exported
 * PDF/Excel/CSV file is uniquely identifiable by school, report and date.
 */
public final class FileNamingUtil {

    private static final String PREFIX = "Kikay_FinTech";

    private FileNamingUtil() {
    }

    /**
     * Turn an existing default file name into a descriptive one, preserving the
     * extension. {@code "fee-balances.pdf"} becomes
     * {@code "Kikay_FinTech_Fee_Balances_2026-08-16.pdf"}.
     */
    public static String suggest(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return fileName;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        return PREFIX + "_" + toTitleSlug(base) + "_" + LocalDate.now() + ext;
    }

    /**
     * Convert a kebab/snake-case base name into Title_Case words, preserving
     * existing acronyms, e.g. {@code "fee-balances"} -> {@code "Fee_Balances"},
     * {@code "FORM_1_2026"} -> {@code "FORM_1_2026"}.
     */
    static String toTitleSlug(String base) {
        StringBuilder out = new StringBuilder();
        for (String word : base.split("[^a-zA-Z0-9]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('_');
            }
            if (isAllUpper(word)) {
                out.append(word);
            } else {
                out.append(Character.toUpperCase(word.charAt(0)));
                out.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.toString();
    }

    private static boolean isAllUpper(String word) {
        return word.equals(word.toUpperCase(Locale.ROOT));
    }
}
