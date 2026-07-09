package com.schaccs.enums;

public enum AcademicTerm {
    TERM_1("Term 1", 1),
    TERM_2("Term 2", 2),
    TERM_3("Term 3", 3);

    private final String displayName;
    private final int number;

    AcademicTerm(String displayName, int number) {
        this.displayName = displayName;
        this.number = number;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getNumber() {
        return number;
    }

    public static AcademicTerm fromNumber(int number) {
        for (AcademicTerm t : values()) {
            if (t.number == number) {
                return t;
            }
        }
        throw new IllegalArgumentException("Invalid term number: " + number);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
