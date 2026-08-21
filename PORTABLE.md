# ThorCash — Build & Installer Guide

How to produce all three distribution artifacts from source.

---

## Artifacts

| File | Location | What it is |
|------|----------|------------|
| `ThorCash-Portable-<ver>.zip` | `target\dist\` | **Portable package** — unzip anywhere, double-click `ThorCash.exe`. No install, no admin, no JVM needed. This is what clients use. |
| `ThorCash-Setup-<ver>.exe` | `target\bootstrapper-output\` | **Shareable installer** — single EXE with EULA, install-folder picker, progress bar, admin elevation, desktop shortcut, Start Menu entry. Wraps the MSI. |
| `ThorCash-<ver>.msi` | `target\installer\` | **MSI installer** — Windows Installer package for enterprise deployment (GPO / Intune / silent install). Not shared with clients. |

All three are the same app — same JAR, same bundled JVM, same behavior. Pick based on deployment method.

---

## Prerequisites (build machine)

| Tool | Version | How to verify |
|------|---------|---------------|
| JDK | 26.0.2 | `"C:\Program Files\Java\jdk-26.0.2\bin\java" -version` |
| Maven | 3.9+ | `mvn -version` |
| WiX Toolset v3 | 3.14 | `candle -?` |
| PowerShell | 5.1+ | `$PSVersionTable` |

WiX is only needed for the MSI and bootstrapper EXE. The portable ZIP can be built without it.

Install WiX if missing:
```
winget install WiXToolset.WiXToolset
```

---

## One-Command Build (recommended)

From the project root:

```
build-installer.bat
```

This runs everything automatically:

1. Verify environment (JDK, Maven, WiX)
2. Run tests (`mvn clean test`)
3. Package JAR + dependencies (`mvn package -DskipTests`)
4. Create custom JVM runtime with `jlink`
5. Assemble installer input (JARs + JavaFX native DLLs)
6. Build MSI with `jpackage`
7. Verify MSI contains runtime
8. Build bootstrapper EXE with WiX Burn
9. Build portable ZIP from app-image
10. Generate SHA-256 checksums

On success, all three artifacts exist:

```
target\dist\ThorCash-Portable-1.0.13.zip            (90 MB)
target\bootstrapper-output\ThorCash-Setup-1.0.13.exe  (91 MB)
target\installer\ThorCash-1.0.13.msi                (92 MB)
```

---

## Manual Step-by-Step Build

If you want to build individual artifacts or understand what the bat file does.

All commands run from the project root.

### Step 1 — Build and test

```
mvn clean test
mvn package -DskipTests
```

Produces:
- `target\thorcash-<version>.jar` — executable JAR
- `target\libs\*.jar` — dependency JARs
- `target\installer-input\` — main JAR + all dependencies (pre-assembled by Maven)

### Step 2 — Create custom JVM runtime (jlink)

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

Produces `target\runtime\` (~52 MB) with `bin\java.exe` and `bin\javaw.exe`.

> Keep the module list in sync with `build-installer.bat`. Missing modules cause "Failed to launch JVM" at runtime.

### Step 3 — Extract JavaFX native DLLs

```
powershell -NoProfile -ExecutionPolicy Bypass -File src\main\installer\extract-javafx-dlls.ps1 -OutputDir target\installer-input
```

Copies `glass.dll`, `prism_d3d.dll`, etc. from the Gluon `-win` JARs in your Maven cache into `target\installer-input\`.

### Step 4a — Portable ZIP

```
"C:\Program Files\Java\jdk-26.0.2\bin\jpackage.exe" ^
    --type app-image ^
    --runtime-image target\runtime ^
    --dest target\app-image ^
    --name ThorCash ^
    --app-version 1.0.13 ^
    --input target\installer-input ^
    --main-jar thorcash-1.0.13.jar ^
    --main-class com.schaccs.Launcher ^
    --icon src\main\resources\icon.ico ^
    --vendor "Thor Technologies" ^
    --java-options "-Xmx512m" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Djava.library.path=."
```

Then zip it:

```
powershell -NoProfile -Command "Compress-Archive -Path 'target\app-image\ThorCash\*' -DestinationPath 'target\dist\ThorCash-Portable-1.0.13.zip' -Force"
```

Output: `target\dist\ThorCash-Portable-1.0.13.zip` — **this is the file to give clients.**

### Step 4b — MSI installer

```
"C:\Program Files\Java\jdk-26.0.2\bin\jpackage.exe" ^
    --type msi ^
    --runtime-image target\runtime ^
    --dest target\installer ^
    --name ThorCash ^
    --app-version 1.0.13 ^
    --input target\installer-input ^
    --main-jar thorcash-1.0.13.jar ^
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

Output: `target\installer\ThorCash-1.0.13.msi`

### Step 4c — Bootstrapper EXE (wraps MSI)

Requires WiX Toolset v3.14 (`candle.exe` and `light.exe` on PATH).

```
set "WIX=C:\Program Files (x86)\WiX Toolset v3.14\bin"

"%WIX%\candle.exe" -nologo ^
    -out target\bootstrapper\Bundle.wixobj ^
    -ext WixBalExtension -ext WixUtilExtension ^
    -dProjectDir="src\main\installer\wix" ^
    -dMsiPath="target\installer\ThorCash-1.0.13.msi" ^
    -dVersion=1.0.13 ^
    -dIconPath="src\main\resources\icon.ico" ^
    src\main\installer\wix\Bundle.wxs

"%WIX%\light.exe" -nologo ^
    -out target\bootstrapper-output\ThorCash-Setup-1.0.13.exe ^
    -ext WixBalExtension -ext WixUtilExtension ^
    target\bootstrapper\Bundle.wixobj
```

Output: `target\bootstrapper-output\ThorCash-Setup-1.0.13.exe`

---

## Version Bump Checklist

When releasing a new version, update these files **before** running `build-installer.bat`:

| File | What to change |
|------|----------------|
| `pom.xml` | `<version>X.Y.Z</version>` (line 9) |
| `build-installer.bat` | `set "APP_VERSION=X.Y.Z"` (line 5) |
| `src\main\java\com\schaccs\ui\layout\Sidebar.java` | `"Version X.Y.Z"` (line 91) |

The bootstrapper EXE reads the version from the MSI filename automatically — no manual change needed in `Bundle.wxs` or `build-bootstrapper.bat`.

---

## Smoke Test (verify before shipping)

1. Extract the portable ZIP anywhere (e.g. `D:\ThorCash`).
2. Double-click `ThorCash.exe` — splash screen then main window must appear.
3. Prove the bundled JVM works with **no system Java**:
   ```powershell
   $env:PATH = "C:\Windows\System32;C:\Windows"
   Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
   .\ThorCash.exe
   ```
   The app must still launch.
4. Check database: after first launch a `database\schaccs.db` folder must appear next to the app. Settings > Database Location shows the path and can open/copy/export it.

---

## Which File to Give to Clients

| Scenario | File |
|----------|------|
| Normal client — just needs it to work | `ThorCash-Portable-<ver>.zip` |
| Client wants a proper Windows installer | `ThorCash-Setup-<ver>.exe` |
| Enterprise / IT department deploying to many PCs | `ThorCash-<ver>.msi` |

**The portable ZIP is the most reliable.** If an installed EXE fails to launch but the ZIP works on the same machine, it is usually a stale installer or antivirus interference — not a packaging bug. Use the ZIP.

---

## Windows Compatibility

- **Required:** Windows 10/11 (64-bit)
- **Not supported:** Windows 7/8/8.1 or 32-bit Windows (JDK 26 runtime cannot load)
- **Impossible:** 32-bit support requires a full rewrite — OpenJFX has no 32-bit Windows build
- **Any 64-bit PC with 4 GB RAM** works fine (used desktops cost ~$80)

Check if a PC is 64-bit capable:
```
wmic cpu get AddressWidth
```
- `64` = hardware is 64-bit, just install 64-bit Windows
- `32` = pre-2006 hardware, no modern software runs there

---

**Thor Technologies**
