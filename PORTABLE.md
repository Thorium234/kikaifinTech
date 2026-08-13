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
4. Check the database landed beside the app: after first launch there must be a
   `database` folder containing `schaccs.db` in the same directory as `ThorCash.exe`. Settings →
   **Database Location & Retrieval** shows that exact path and can open the folder,
   copy the path, or export a copy of the database for safe-keeping. The app never
   writes to `~\.schaccs` in packaged mode (it only migrates a pre-existing
   `~\.schaccs\schaccs.db` in on the very first packaged run).

## Version bump checklist (new release)

| Location | Change |
|----------|--------|
| `pom.xml` (`<version>`) | new version — controls jar name + `version.properties` |
| `build-installer.bat` (`APP_VERSION`) | new version |
| `src\main\installer\extract-javafx-dlls.ps1` | only when upgrading JavaFX (currently 21.0.6) |
| `src\main\installer\wix\Bundle.wxs`, `build-bootstrapper.bat` | auto — version read from the MSI filename |

Then update `--app-version`, `--main-jar`, and the ZIP name in steps 4–5 above.

---

## Generating a new MSI installer

The MSI is the Windows Installer package — the same self-contained app, but
installable via the standard "run the installer" flow. It never needs a JVM.

**Recommended:** run the one-command pipeline which builds everything (tests,
runtime, MSI, bootstrapper EXE, checksums):

```
build-installer.bat
```

**Or, from an existing build** (when `target\runtime` and `target\installer-input`
already exist — e.g. right after `build-installer.bat` or after the portable steps
1–3 above), run jpackage directly:

```
"C:\Program Files\Java\jdk-26.0.2\bin\jpackage.exe" ^
    --type msi ^
    --runtime-image target\runtime ^
    --dest target\installer ^
    --name ThorCash ^
    --app-version 1.0.0 ^
    --input target\installer-input ^
    --main-jar thorcash-1.0.0.jar ^
    --main-class com.schaccs.Launcher ^
    --icon src\main\resources\icon.ico ^
    --vendor "Thor Technologies" ^
    --license-file src\main\installer\eula.rtf ^
    --win-shortcut ^
    --win-menu ^
    --win-dir-chooser ^
    --java-options "-Xmx512m" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Djava.library.path=."
```

Output: `target\installer\ThorCash-1.0.0.msi` (~92 MB).

### Per-user MSI (no admin rights needed)

Add `--win-per-user-install`. This installs to `%LOCALAPPDATA%\ThorCash` instead of
`Program Files` and can be installed without elevation:

```
... --type msi --win-per-user-install ...
```

> Verified: a per-user MSI built this way installed and launched on this machine
> with no system Java (identical layout to the ZIP). Both MSI variants are
> byte-for-byte the same app as the portable ZIP — if the ZIP runs on a machine,
> the current MSI/EXE build runs there too.

## Generating a new bootstrapper EXE

The bootstrapper (`ThorCash-Setup-1.0.0.exe`) is a WiX Burn bundle that wraps the
MSI with the license page, install-folder picker, progress bar and admin
elevation. It is produced by `build-installer.bat` automatically. To build it
manually after generating the MSI:

```
set "WIX=C:\Program Files (x86)\WiX Toolset v3.14\bin"
"%WIX%\candle.exe" -nologo -out target\bootstrapper\Bundle.wixobj ^
    -ext WixBalExtension -ext WixUtilExtension ^
    -dProjectDir="src\main\installer\wix" ^
    -dMsiPath="target\installer\ThorCash-1.0.0.msi" ^
    -dVersion=1.0.0 ^
    -dIconPath="src\main\resources\icon.ico" ^
    src\main\installer\wix\Bundle.wxs
"%WIX%\light.exe" -nologo -out target\bootstrapper-output\ThorCash-Setup-1.0.0.exe ^
    -ext WixBalExtension -ext WixUtilExtension ^
    target\bootstrapper\Bundle.wixobj
```

Output: `target\bootstrapper-output\ThorCash-Setup-1.0.0.exe` (~91 MB).

## Which distribution to hand to clients

| File | When to use |
|------|-------------|
| `ThorCash-Portable-1.0.0.zip` | **Most reliable.** No install, no admin, runs anywhere — proven working on the client machine. |
| `ThorCash-Setup-1.0.0.exe` | Standard "run the installer" flow (EULA + shortcut, admin). Same app as the ZIP. |
| `ThorCash-1.0.0.msi` | Enterprise deployment (GPO / Intune / silent install). |

If a machine reports "Failed to launch JVM" from the installed EXE but the ZIP
runs fine on the same machine, it is **not** the package — it is either a stale
(pre-fix) installer being re-run, or antivirus/permission interference on the
Program Files path. Use the ZIP there.

## Windows compatibility (important)

### Requirements

The bundled runtime is built by **JDK 26, which only runs on Windows 10/11
(64-bit)**. On Windows 7/8/8.1 or 32-bit Windows the bundled `jvm.dll` cannot load
and the app shows "Failed to launch JVM" — for the ZIP, MSI and EXE alike.

### 32-bit Windows is not possible (verified)

32-bit Windows support is **impossible for this app** — not because of packaging,
but because **OpenJFX (JavaFX 9+, including the 21 we use) has no 32-bit Windows
build at all**. The `org.openjfx` artifacts on Maven Central ship exactly these
classifiers: `linux`, `mac`, `mac-aarch64`, `win` — the `win` jars/DLLs are 64-bit
only. No JDK (32-bit or otherwise) can load x64 JavaFX native DLLs on a 32-bit OS.
A full Swing or web-based rewrite (~33k lines of JavaFX) would be the only path.

### The cost myth — you do NOT need an expensive machine

ThorCash runs fine on any 64-bit PC with 4 GB RAM, which costs **$80–400**
(used business desktops are cheapest) — not $40,000. Before buying anything:

1. Check whether the school's "old" PCs are actually 64-bit capable. On each
   machine, run:
   ```
   wmic cpu get AddressWidth
   ```
   - `AddressWidth` = 64 → hardware is 64-bit, the PC just runs 32-bit Windows.
     Install **64-bit Windows 10/11** (free reinstall) and the existing ZIP works
     with zero new hardware.
   - `AddressWidth` = 32 → genuine 32-bit hardware (pre-2006 era). No modern
     software runs there (Chrome, Firefox, LibreOffice have all dropped 32-bit
     Windows too), so it is not a ThorCash-specific problem.

2. If any genuinely 32-bit-only machines must be used, the realistic options are:
   - Run ThorCash on one 64-bit PC and access it remotely (Remote Desktop) from the
     32-bit machines, or
   - Replace the handful of 32-bit units with used 64-bit desktops (~$80 each),
     which is far cheaper than any software workaround.
