# ThorCash v1.0.1

**Vendor:** Thor Technologies

---

## For End Users: Installing ThorCash

### System Requirements

- Windows 10 or later (64-bit)
- 1 GB free disk space
- 4 GB RAM

### How to Install

1. Get the file `ThorCash-Setup-1.0.1.exe` from the developer.
2. **Double-click** it to launch the installer.
3. Read the License Agreement and check **"I accept"**, then click **Install**.
4. Choose where to install (or leave the default).
5. Wait for the progress bar to finish.
6. Click **Finish** — ThorCash is ready.

That's it. No Java, no other dependencies needed.

### How to Launch

- **Desktop shortcut:** Double-click the **ThorCash** icon.
- **Start Menu:** Find **ThorCash** under **Thor Technologies**.

### How to Uninstall

Open **Settings > Apps > Installed apps**, search for **ThorCash**, click **Uninstall**.

---

## For Developers: How to Build the Installer

You only need to run **one script**. It produces a single `.exe` file you can share.

### Prerequisites

| Tool | Where to get it |
|------|----------------|
| **JDK 21+** (26.0.2 used) | https://adoptium.net — install and set `JAVA_HOME` |
| **Maven 3.9+** | https://maven.apache.org/download.cgi — add `bin/` to PATH |
| **Inno Setup 6** | `winget install JRSoftware.InnoSetup` — or https://jrsoftware.org/isdl.php |

Verify they are installed:

```
"C:\Program Files\Java\jdk-26.0.2\bin\java" -version
mvn -version
"C:\Program Files (x86)\Inno Setup 6\ISCC.exe" /?
```

### Build the installer

From the project root folder, run:

```
build-installer.bat
```

The script runs **8 stages**:

1. **Verify** environment (JDK, Maven, Inno Setup)
2. **Test** — full unit test suite
3. **Package** — compile and produce `thorcash-1.0.13.jar` with dependencies
4. **jlink** — create a custom JVM runtime (fixes "Failed to launch JVM")
5. **Assemble input** — JARs + extracted JavaFX native DLLs
6. **App image** — `jpackage --type app-image` produces the self-contained `ThorCash\` folder
7. **Setup wizard** — Inno Setup wraps the app image into `ThorCash_Setup_v1.0.13.exe`
   (classic wizard: EULA → folder picker → progress → Completed Setup screen with a
   checked **Launch ThorCash** box)
8. **Checksums** — SHA-256 of EXE and portable ZIP

### Output

| File | Path | Purpose |
|------|------|---------|
| `ThorCash_Setup_v1.0.13.exe` | `target\installer-output\` | **Shareable native installer** |
| `ThorCash-Portable-1.0.13.zip` | `target\dist\` | Portable package (no install) |

Silent deployment: `ThorCash_Setup_v1.0.13.exe /VERYSILENT /NORESTART`

### What the installer includes

- Desktop shortcut named **ThorCash**
- Start Menu entry under **Thor Technologies**
- Appears in **Installed Apps** as **ThorCash** with the app icon
- Custom **splash screen** on launch
- Desktop and taskbar **icon**
- Full **uninstall** support via Add/Remove Programs
- Bundled Java runtime — no separate Java install needed

---

**Thor Technologies**
