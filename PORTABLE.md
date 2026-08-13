# ThorCash — Portable ZIP Build (CLI)

This document explains how to produce the **portable ZIP** distribution of ThorCash
for future versions, and how the current app-image was produced.

## What the portable ZIP is

`target\dist\ThorCash-Portable-1.0.0.zip` (~91 MB) is the finished application,
fully self-contained:

- `ThorCash.exe` — the launcher
- `app\` — the main JAR, all dependency JARs, JavaFX native DLLs, launcher config
- `runtime\` — a custom JVM created with `jlink` (includes `java.exe` / `javaw.exe`)

The client just unzips it anywhere and double-clicks `ThorCash.exe`.
**No installer is run, no JVM is installed on the client, no admin rights needed.**
This is the package that was verified working on the client machine.

> Platform requirement: **Windows 10/11 (64-bit)**. The bundled JDK 26 runtime
> cannot load on Windows 7/8 or 32-bit Windows.

## Prerequisites (build machine)

| Tool | Version | Check |
|------|---------|-------|
| JDK | 26.0.2 | `"C:\Program Files\Java\jdk-26.0.2\bin\java" -version` |
| Maven | 3.9+ | `mvn -version` |
| PowerShell | 5.1+ | `$PSVersionTable` |

WiX is **not** needed for the ZIP (only for the MSI/bootstrapper).

## How the app-image is produced — step by step

All commands run from the project root.

### 1. Build and test

```
mvn clean test
mvn package -DskipTests
```

`mvn package` produces:

| Output | Purpose |
|--------|---------|
| `target\thorcash-<version>.jar` | executable application JAR |
| `target\libs\*.jar` | runtime dependency JARs |
| `target\installer-input\` | main JAR + all dependencies (pre-assembled by the Maven dependency plugin) |

### 2. Create the custom JVM runtime (jlink)

```
"C:\Program Files\Java\jdk-26.0.2\bin\jlink.exe" ^
    --module-path "C:\Program Files\Java\jdk-26.0.2\jmods" ^
    --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.security.jgss,java.sql,java.xml,java.xml.crypto,jdk.jfr,jdk.unsupported,jdk.zipfs,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.management,jdk.management.agent ^
    --no-header-files ^
    --no-man-pages ^
    --strip-debug ^
    --compress=2 ^
    --output target\runtime
```

Produces `target\runtime\` with `bin\java.exe` and `bin\javaw.exe`.

> **Keep the module list in sync** with `build-installer.bat` and `pom.xml`.
> Missing modules (e.g. `java.net.http` for the update checker) caused the
> original "Failed to launch JVM" failure.

### 3. Extract the JavaFX native DLLs into the input dir

```
powershell -NoProfile -ExecutionPolicy Bypass -File src\main\installer\extract-javafx-dlls.ps1 -OutputDir target\installer-input
```

Copies the JavaFX `*.dll` files (glass.dll, prism_d3d.dll, etc.) out of the
Gluon `-win` jars in `%UserProfile%\.m2` into `target\installer-input\`.

### 4. Create the application image (jpackage)

**This is the command that produces the app-image:**

```
"C:\Program Files\Java\jdk-26.0.2\bin\jpackage.exe" ^
    --type app-image ^
    --runtime-image target\runtime ^
    --dest target\app-image ^
    --name ThorCash ^
    --app-version 1.0.0 ^
    --input target\installer-input ^
    --main-jar thorcash-1.0.0.jar ^
    --main-class com.schaccs.Launcher ^
    --icon src\main\resources\icon.ico ^
    --vendor "Thor Technologies" ^
    --java-options "-Xmx512m" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Djava.library.path=."
```

Output: `target\app-image\ThorCash\` containing `ThorCash.exe`, `app\`, and `runtime\`.

Notes:
- `--runtime-image target\runtime` makes jpackage bundle *our* jlink runtime
  (which includes `java.exe`/`javaw.exe`) instead of building its own.
- `--main-jar thorcash-1.0.0.jar` must match the jar name in `target\installer-input\`
  (it is derived from the version in `pom.xml`).

### 5. Zip the app-image

```
powershell -NoProfile -Command "Compress-Archive -Path 'target\app-image\ThorCash\*' -DestinationPath 'target\dist\ThorCash-Portable-1.0.0.zip' -Force"
```

Output: `target\dist\ThorCash-Portable-1.0.0.zip` (~91 MB) — **this is the file to hand to clients.**

## Quick path for a future version

If you already ran `build-installer.bat` (it performs steps 1–3 and rebuilds
`target\runtime` + `target\installer-input`), you only need steps 4 and 5 above.
You can run them straight after the bat completes.

## Smoke test (verify before shipping)

1. Extract the ZIP anywhere (e.g. `D:\ThorCash`).
2. Double-click `ThorCash.exe` — splash screen then main window must appear.
3. Prove the bundled JVM works with **no system Java**:
   - In a fresh PowerShell: `$env:PATH = "C:\Windows\System32;C:\Windows"; Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue`
   - Run `.\ThorCash.exe` from the extracted folder — the app must still launch.

## Version bump checklist (new release)

| Location | Change |
|----------|--------|
| `pom.xml` (`<version>`) | new version — controls jar name + `version.properties` |
| `build-installer.bat` (`APP_VERSION`) | new version |
| `src\main\installer\extract-javafx-dlls.ps1` | only when upgrading JavaFX (currently 21.0.6) |
| `src\main\installer\wix\Bundle.wxs`, `build-bootstrapper.bat` | auto — version read from the MSI filename |

Then update `--app-version`, `--main-jar`, and the ZIP name in steps 4–5 above.
