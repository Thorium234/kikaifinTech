# ThorCash — Build & Installer Guide

How to produce all distribution artifacts from source.

---

## Artifacts

| File | Location | What it is |
|------|----------|------------|
| `ThorCash-Portable-<ver>.zip` | `target\dist\` | **Portable package** — unzip anywhere, double-click `ThorCash.exe`. No install, no admin, no JVM needed. This is what clients use. |
| `ThorCash_Setup_v<ver>.exe` | `target\installer-output\` | **Native setup wizard** — classic Windows installer built with Inno Setup wrapping the self-contained jpackage app-image: welcome → EULA → install-folder picker → progress bar → Completed Setup screen with a checked **Launch ThorCash** box that opens the app on Finish. Installs to `C:\Program Files\ThorCash` with desktop shortcut and Start Menu entry. |

Both are the same app — same JARs, same bundled JVM, same behavior. Pick based on deployment method.

---

## Prerequisites (build machine)

| Tool | Version | How to verify |
|------|---------|---------------|
| JDK | 26.0.2 | `"C:\Program Files\Java\jdk-26.0.2\bin\java" -version` |
| Maven | 3.9+ | `mvn -version` |
| Inno Setup 6 | 6.x | `"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" /?` |
| PowerShell | 5.1+ | `$PSVersionTable` |

Inno Setup is only needed for the setup wizard EXE. The portable ZIP can be built without it.

Install Inno Setup if missing:
```
winget install JRSoftware.InnoSetup
```

---

## One-Command Build (recommended)

From the project root:

```
build-installer.bat
```

This runs everything automatically:

1. Verify environment (JDK, Maven, Inno Setup)
2. Run tests (`mvn clean test`)
3. Package JAR + dependencies (`mvn package -DskipTests`)
4. Create custom JVM runtime with `jlink`
5. Assemble installer input (JARs + JavaFX native DLLs)
6. Build self-contained app image with `jpackage`
7. Build portable ZIP from the app image
8. Compile the native setup wizard with Inno Setup
9. Generate SHA-256 checksums

On success, both artifacts exist:

```
target\dist\ThorCash-Portable-1.0.13.zip          (~90 MB)
target\installer-output\ThorCash_Setup_v1.0.13.exe (~91 MB)
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

### Step 4b — Native setup wizard (Inno Setup)

Requires Inno Setup 6 (`ISCC.exe` on PATH or in `C:\Program Files (x86)\Inno Setup 6\`).
The wizard wraps the app image built in Step 4a — no separate jpackage run needed.

```
"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" /DAppVersion=1.0.13 installer.iss
```

The script (`installer.iss` in the project root) produces:

- Install to `C:\Program Files\ThorCash` (folder picker included)
- Desktop icon task + Start Menu group
- EULA page (reuses `src\main\installer\eula.rtf`)
- **Completed Setup screen with a checked "Launch ThorCash" checkbox** —
  Finish opens the JavaFX dashboard immediately

Output: `target\installer-output\ThorCash_Setup_v1.0.13.exe`

Silent deployment:

```
ThorCash_Setup_v1.0.13.exe /VERYSILENT /NORESTART
```

---

## Version Bump Checklist

When releasing a new version, update these files **before** running `build-installer.bat`:

| File | What to change |
|------|----------------|
| `pom.xml` | `<version>X.Y.Z</version>` (line 9) |
| `build-installer.bat` | `set "APP_VERSION=X.Y.Z"` (line 5) |
| `src\main\java\com\schaccs\ui\layout\Sidebar.java` | `"Version X.Y.Z"` (line 91) |

The setup wizard reads the version automatically — `build-installer.bat` passes it to
Inno Setup via `/DAppVersion=`. No manual change needed in `installer.iss`.

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
| Client wants a proper Windows installer | `ThorCash_Setup_v<ver>.exe` |

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
