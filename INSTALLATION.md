# SCHACCS v1.0.0 — Installation Guide

**School:** Friends School Kikai Boys Secondary School
**Ministry:** Republic of Kenya, Ministry of Education

## System Requirements

- Windows 10 or later (64-bit)
- 512 MB free disk space
- 4 GB RAM minimum

## Installing

1. Locate the installer file `SCHACCS-1.0.0.msi` on the USB drive or download location.
2. Double-click `SCHACCS-1.0.0.msi` to launch the setup wizard.
3. Read and accept the **End User License Agreement (EULA)** when prompted.
4. Choose the install location (default: `C:\Program Files\SCHACCS`) or click **Browse** to pick a different folder.
5. Confirm that a **Desktop Shortcut** and **Start Menu entry** are created.
6. Click **Install** and wait for the progress bar to complete.
7. Click **Finish** to close the wizard.

## Launching the Application

- **Desktop:** Double-click the **SCHACCS** shortcut on the desktop.
- **Start Menu:** Open the Start Menu, find the **SCHACCS** folder (under **Friends School Kikai Boys**), and click **SCHACCS**.

On first launch, the application will create its database at `C:\Users\<YourName>\.schaccs\schaccs.db` and load sample school data.

## Uninstalling

1. Open **Settings > Apps > Installed apps** (Windows 10/11).
2. Search for **SCHACCS**.
3. Click **Uninstall** and follow the prompts. The MSI installer will cleanly remove all application files while preserving user data in `~/.schaccs/`.

Alternatively, run the uninstaller from **Add or Remove Programs** in the Control Panel.

## Database Location

The application stores all data in a single SQLite file:

```
C:\Users\<YourName>\.schaccs\schaccs.db
```

Back up this file regularly. Copying it to another computer gives you a full data transfer.

## Default Login

The application opens directly to the dashboard. No login is required in V1.

## Features (V1)

| Module | What It Does |
|--------|-------------|
| **Dashboard** | Summary KPIs — collections, outstanding fees, student count, receipts |
| **Students** | Student registry — add, edit, search, import from CSV/XLSX |
| **Fee Structure** | Set vote heads and amounts per term/year |
| **Receipting** | Search student, receive payment, auto-allocate to vote heads |
| **Reports** | Fee balances, defaulters, daily collection, student statements |
| **Settings** | School profile, academic year, receipt numbering |

## Troubleshooting

| Problem | Solution |
|---------|----------|
| App won't open | Ensure you are on Windows 10+ (64-bit). Check that antivirus is not blocking the app. |
| Black screen on launch | Update your graphics drivers. The app requires DirectX 9+ support. |
| "Database is locked" error | Close any other instances of SCHACCS, then reopen. |
| Data missing after reinstall | Copy your backup of `schaccs.db` back to `C:\Users\<YourName>\.schaccs\` |

## Support

Contact the school bursar or IT administrator for assistance.

## Building the Installer (for Developers)

To build the MSI installer from source:

1. Ensure JDK 21+, Maven 3.9+, and WiX Toolset v3 are installed.
2. Run the automated build script from the project root:

```
build-installer.bat
```

The script runs tests, packages the application, extracts native libraries, and produces:

```
target\installer\SCHACCS-1.0.0.msi
```

**Friends School Kikai Boys Secondary School**
P.O. Box 345-50202, Chwele
