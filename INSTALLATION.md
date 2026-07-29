# ThorCash v1.0.0

**Vendor:** Thor Technologies

---

## For End Users: Installing ThorCash

### System Requirements

- Windows 10 or later (64-bit)
- 1 GB free disk space
- 4 GB RAM

### How to Install

1. Get the file `ThorCash-Setup-1.0.0.exe` from the developer.
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
| **WiX Toolset v3** (3.14) | `winget install WiXToolset.WiXToolset` — or https://wixtoolset.org |

Verify they are installed:

```
"C:\Program Files\Java\jdk-26.0.2\bin\java" -version
mvn -version
candle -?
```

### Build the installer

From the project root folder, run:

```
build-installer.bat
```

The script runs **9 stages**:

1. **Verify** environment (JDK, Maven, WiX)
2. **Test** — 93 unit tests
3. **Package** — compile and produce `thorcash-1.0.0.jar` with dependencies
4. **jlink** — create a custom JVM runtime (fixes "Failed to launch JVM")
5. **Assemble input** — JARs + extracted JavaFX native DLLs
6. **MSI** — `jpackage --runtime-image` produces `ThorCash-1.0.0.msi`
7. **Verify** runtime executables inside the MSI
8. **Bootstrapper** — wrap MSI into `ThorCash-Setup-1.0.0.exe`
9. **Checksums** — SHA-256 of MSI and EXE

### Output

| File | Path | Purpose |
|------|------|---------|
| `ThorCash-Setup-1.0.0.exe` | `target\bootstrapper-output\` | **Shareable installer** |
| `ThorCash-1.0.0.msi` | `target\installer\` | Intermediate MSI |

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
