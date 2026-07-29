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
| **JDK 21+** | https://adoptium.net — install and add to PATH |
| **Maven 3.9+** | https://maven.apache.org/download.cgi — add `bin/` to PATH |
| **WiX Toolset v3** | `winget install WiXToolset.WiXToolset` — or download from https://wixtoolset.org |

Verify they are installed:

```
java -version
mvn -version
candle -?
```

### Build the installer

From the project root folder, run:

```
build-installer.bat
```

The script will:

1. **Run all 80 tests** to make sure nothing is broken.
2. **Compile and package** the application into a JAR with dependencies.
3. **Extract JavaFX native DLLs** needed for Windows.
4. **Build an MSI** via jpackage (Windows Installer format).
5. **Wrap it into a professional EXE** bootstrapper with a custom UI.

### Output

After the script finishes, your shareable file is at:

```
target\bootstrapper-output\ThorCash-Setup-1.0.0.exe
```

That's **the only file you need** to share. Send it to your friend — they double-click and install.

An intermediate `.msi` file is also produced at `target\installer\ThorCash-1.0.0.msi`, but you don't need to share it.

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
