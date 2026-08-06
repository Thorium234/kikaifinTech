package com.schaccs.util;

import javafx.stage.FileChooser;

import java.io.File;

/**
 * Remembers the most recently used folder for file open/save dialogs within the
 * current live session, so the next dialog opens where the previous one ended —
 * the way a browser remembers your download location.
 */
public final class FileDialogMemory {

    private static File lastDirectory;

    private FileDialogMemory() {
    }

    /** Point a new dialog at the last-used folder, if any. */
    public static void applyTo(FileChooser chooser) {
        if (chooser == null || lastDirectory == null) {
            return;
        }
        if (lastDirectory.isDirectory() && lastDirectory.canRead()) {
            chooser.setInitialDirectory(lastDirectory);
        }
    }

    /** Record the folder of a file that was opened or saved. */
    public static void remember(File file) {
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && parent.isDirectory()) {
            lastDirectory = parent;
        }
    }
}
