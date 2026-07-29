package com.schaccs.update;

import java.io.IOException;
import java.nio.file.Path;

public final class InstallerLauncher {

    private InstallerLauncher() {}

    public static void launch(Path installerPath) throws IOException {
        String path = installerPath.toAbsolutePath().toString();
        ProcessBuilder pb = new ProcessBuilder(path, "/SILENT", "/SUPPRESSMSGBOXES");
        pb.inheritIO();
        pb.start();
    }
}
