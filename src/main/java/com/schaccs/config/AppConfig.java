package com.schaccs.config;

/**
 * Application-wide singleton configuration.
 */
public final class AppConfig {

    private static final AppConfig INSTANCE = new AppConfig();

    private final SchoolProfile schoolProfile = new SchoolProfile();
    private String currentUser = "Bursar";
    private String currentUserRole = "BURSAR";

    private AppConfig() {
    }

    public static AppConfig getInstance() {
        return INSTANCE;
    }

    public SchoolProfile getSchoolProfile() {
        return schoolProfile;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(String currentUser) {
        this.currentUser = currentUser;
    }

    public String getCurrentUserRole() {
        return currentUserRole;
    }

    public void setCurrentUserRole(String currentUserRole) {
        this.currentUserRole = currentUserRole;
    }

    public int getAcademicYear() {
        return schoolProfile.getAcademicYear();
    }
}
