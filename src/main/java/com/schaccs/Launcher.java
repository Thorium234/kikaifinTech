package com.schaccs;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Non-JavaFX entry point so the jar/module path can launch cleanly.
 * The javafx-maven-plugin also targets this class.
 *
 * This launcher sets up unconditional startup logging so that runtime
 * failures on installed machines are captured in a file.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        setupStartupLogging();
        MainApp.main(args);
    }

    private static void setupStartupLogging() {
        try {
            Path logsDir = Path.of(System.getProperty("user.home"), ".thorcash", "logs");
            Files.createDirectories(logsDir);

            Path startupLog = logsDir.resolve("startup.log");
            // Open in append mode so subsequent runs add to the file
            PrintStream ps = new PrintStream(
                    Files.newOutputStream(startupLog,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.APPEND),
                    true, "UTF-8");

            // Redirect console output so we always capture startup exceptions.
            System.setOut(ps);
            System.setErr(ps);

            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                throwable.printStackTrace(ps);
                ps.flush();
            });
        } catch (IOException e) {
            // Best-effort logging; do not block app startup if logging setup fails.
            e.printStackTrace();
        }
    }
}
