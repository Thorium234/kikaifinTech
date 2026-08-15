package com.schaccs.store;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lossless text codec for a flat string map. Used to store a held import row
 * (student header row, or the fields of a fees-balance row) in the clean_data
 * table. Newlines, backslashes and '=' signs in values are escaped so any cell
 * content survives a save/load round-trip.
 */
public final class CleanDataCodec {

    private CleanDataCodec() {
    }

    public static String encode(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(escape(e.getKey())).append('=').append(escape(e.getValue() == null ? "" : e.getValue()));
        }
        return sb.toString();
    }

    public static Map<String, String> decode(String payload) {
        Map<String, String> map = new LinkedHashMap<>();
        if (payload == null || payload.isEmpty()) {
            return map;
        }
        for (String line : payload.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            int eq = indexOfUnescapedEquals(line);
            if (eq < 0) {
                map.put(unescape(line), "");
            } else {
                map.put(unescape(line.substring(0, eq)), unescape(line.substring(eq + 1)));
            }
        }
        return map;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("=", "\\=");
    }

    private static int indexOfUnescapedEquals(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '\\') {
                i++;
            } else if (line.charAt(i) == '=') {
                return i;
            }
        }
        return -1;
    }

    private static String unescape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                if (next == 'n') {
                    sb.append('\n');
                    i++;
                    continue;
                }
                if (next == 'r') {
                    sb.append('\r');
                    i++;
                    continue;
                }
                if (next == '=') {
                    sb.append('=');
                    i++;
                    continue;
                }
                if (next == '\\') {
                    sb.append('\\');
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
