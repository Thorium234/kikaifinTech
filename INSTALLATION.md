# SCHACCS v1.0.0 — Installation Guide

**School:** Friends School Kikai Boys Secondary School
**Ministry:** Republic of Kenya, Ministry of Education

## System Requirements

- Windows 10 or later (64-bit)
- 512 MB free disk space
- 4 GB RAM minimum

## Installing

1. Locate the installer file `SCHACCS-1.0.0.exe` on the USB drive or download location.
2. Double-click `SCHACCS-1.0.0.exe` to launch the setup wizard.
3. Follow the on-screen prompts:
   - Accept the default install location or choose a different folder.
   - Select **Create Desktop Shortcut** for easy access.
   - The installer will add SCHACCS to the Start Menu under **SCHACCS**.
4. Click **Install** and wait for the progress bar to complete.
5. Click **Finish** to close the wizard.

## Launching the Application

- **Desktop:** Double-click the **SCHACCS** shortcut on the desktop.
- **Start Menu:** Open the Start Menu, find the **SCHACCS** folder, and click **SCHACCS**.

On first launch, the application will create its database at `C:\Users\<YourName>\.schaccs\schaccs.db` and load sample data for the school.

## Uninstalling

1. Open **Settings > Apps > Installed apps** (Windows 10/11).
2. Search for **SCHACCS**.
3. Click **Uninstall** and follow the prompts.

Alternatively, run the uninstaller from the install directory or from the Start Menu shortcut.

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

**Friends School Kikai Boys Secondary School**
P.O. Box 345-50202, Chwele
