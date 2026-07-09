package com.schaccs;

/**
 * Non-JavaFX entry point so the jar/module path can launch cleanly.
 * The javafx-maven-plugin also targets this class.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        MainApp.main(args);
    }
}
