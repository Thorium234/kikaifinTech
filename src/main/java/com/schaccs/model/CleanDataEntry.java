package com.schaccs.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A row held back for cleaning after an import: it carried mistakes, so it was
 * persisted instead of being committed. The held row lives here until the user
 * returns and fixes it, at which point it auto-imports into the complete data.
 */
public final class CleanDataEntry {

    public enum Type { STUDENT, FEES_BALANCE }

    private final String id;
    private final Type type;
    private final Map<String, String> fields;
    private final LocalDateTime createdAt;

    private CleanDataEntry(String id, Type type, Map<String, String> fields, LocalDateTime createdAt) {
        this.id = id;
        this.type = type;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.createdAt = createdAt;
    }

    public static CleanDataEntry create(Type type, Map<String, String> fields) {
        return new CleanDataEntry(java.util.UUID.randomUUID().toString(), type, fields, LocalDateTime.now());
    }

    /** Reconstruct an entry from the database. */
    public static CleanDataEntry restore(String id, Type type, Map<String, String> fields, LocalDateTime createdAt) {
        return new CleanDataEntry(id, type, fields, createdAt);
    }

    public String getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getName() {
        return firstNonBlank(fields, "fullname", "studentname", "name");
    }

    public String getDetail() {
        if (type == Type.STUDENT) {
            return joinNonBlank(" ", first(fields, "admissionnumber", "admission"),
                    first(fields, "formclass"), first(fields, "stream"));
        }
        StringBuilder sb = new StringBuilder();
        String sheet = fields.get("sheetName");
        if (sheet != null && !sheet.isBlank()) {
            sb.append(sheet);
            String row = fields.get("rowNumber");
            if (row != null && !row.isBlank()) {
                sb.append(" (row ").append(row).append(")");
            }
            sb.append(" ");
        }
        return sb.toString().trim() + joinNonBlank(" ", first(fields, "admissionNumber"),
                first(fields, "formClass"), first(fields, "stream"));
    }

    private static String first(Map<String, String> m, String... keys) {
        for (String key : keys) {
            String value = m.get(key);
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    private static String firstNonBlank(Map<String, String> m, String... keys) {
        for (String key : keys) {
            String value = m.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(sep);
                }
                sb.append(part);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CleanDataEntry that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return type + " — " + getName();
    }
}
