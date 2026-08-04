# Deployment / Packaging — ThorCash

This document describes how to build a production-ready Windows installer using JDK 21+, OpenJFX, jlink and jpackage. The steps are based strictly on OpenJDK/OpenJFX/JDK tools and official Maven plugins.

Prerequisites (build machine)
- Windows 10/11 (64-bit) build VM recommended.
- JDK 21+ (the JDK must include jlink/jpackage). Set JAVA_HOME to the JDK installation.
- Maven 3.9+
- WiX Toolset v3.14+ installed and added to PATH (for MSI creation).
- Ensure your build machine has the Windows platform (we build a Windows installer).

Key points
- The pom includes platform classifier for JavaFX and unpacks javafx-jmods for jlink.
- We create a custom runtime image via maven-jlink-plugin that contains JavaFX modules and native libraries.
- jpackage is invoked from Maven (profile `windows-package`) and uses the runtime image.

Build & package (recommended)
1. On a Windows build host set:
   - JAVA_HOME to the JDK 21 installation (JDK must provide jpackage.exe).
   - PATH should include WiX binaries (or be installed to default location).

2. Run full package (this will run jlink and jpackage):
   mvn -Pwindows-package -DskipTests=false clean package

   Output locations:
   - runtime image: target/runtime
   - installer (MSI): target/installer/<AppName>-<version>.msi
   - installer input (jars): target/installer-input
   - if you need an EXE bootstrapper built separately, follow your bootstrapper step (not required here)

Verify runtime image (important)
- Confirm target/runtime/bin/javaw.exe exists.
- Confirm target/runtime contains JavaFX native libraries (DLLs) and JavaFX module jars (inside lib).

Install & test on clean VM
1. Copy the produced MSI to a clean Windows VM (no Java installed).
2. Run the MSI and install (recommend per-user install for testing).
3. Launch the app:
   - From Start Menu or Desktop shortcut, or from cmd:
     cd "C:\Program Files\ThorCash" (or install path)
     .\ThorCash.exe

4. If the app fails to start:
   - Inspect per-user startup log: %USERPROFILE%\.thorcash\logs\startup.log
   - Inspect Windows Event Viewer → Windows Logs → Application for any crash reports.
   - If you ran the EXE from a console, capture stdout/stderr redirected earlier.

Troubleshooting — common failures & how to fix
- Problem: UnsatisfiedLinkError for glass.dll/prism_d3d.dll
  Cause: JavaFX native libraries not present in runtime image.
  Fix: Ensure javafx-jmods were unpacked and included in maven-jlink-plugin modulePaths; rebuild with -Pwindows-package.

- Problem: NoClassDefFoundError for javafx classes
  Cause: JavaFX modules were not included into runtime image.
  Fix: Add needed javafx modules (javafx.controls/javafx.fxml/javafx.graphics) in maven-jlink-plugin addModules.

- Problem: EXE silently exits on double-click but console shows error when run manually
  Action: Always run installed EXE from a command prompt to capture stdout/stderr.

- Problem: Installer cannot find jpackage or WiX errors
  Check: JAVA_HOME and WiX on PATH on the build host. jpackage is executed from ${env.JAVA_HOME}\bin\jpackage.exe.

Release checklist
- Build and verify installer on a clean Windows VM (no JDK/JRE).
- Confirm runtime bin/javaw.exe and native libraries present in runtime image.
- Run the app and confirm resources (icons, splash screen, CSS) load, database created and app starts.
- Generate and store SHA256 checksums for created MSI/EXE.

Notes about cross-platform builds
- JavaFX platform artifacts are classifier-specific. Only build for the platform you are on (win/linux/mac) or set up CI jobs per platform with appropriate classifier and jmods for that platform.
