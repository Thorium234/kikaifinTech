# ThorCash Release Guide

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| JDK 21+ | 26.0.2 | `java -version` |
| Maven 3.9+ | any | `mvn -version` |
| WiX Toolset v3 | 3.14 | `candle -?` |
| PowerShell | 5.1+ | `$PSVersionTable` |

## Step 1 — Build the installer

From the project root:

```
build-installer.bat
```

The script runs 9 stages automatically. On success, output files appear in `target/`:

| File | Description |
|------|-------------|
| `target\dist\ThorCash-Portable-1.0.1.zip` | **Portable package** — unzip and run, no install, no admin |
| `target\installer\ThorCash-1.0.1.msi` | Windows Installer package (private, not shared) |
| `target\bootstrapper-output\ThorCash-Setup-1.0.1.exe` | **Shareable installer** — single EXE with admin elevation |

Intermediate artifacts:

| Path | Purpose |
|------|---------|
| `target\runtime\` | Bundled JVM (produced by `jlink`) |
| `target\installer-input\` | JARs + JavaFX native DLLs fed to `jpackage` |

## Step 2 — Generate checksums

```
certutil -hashfile target\installer\ThorCash-1.0.1.msi SHA256
certutil -hashfile target\bootstrapper-output\ThorCash-Setup-1.0.1.exe SHA256
```

Or in PowerShell:

```powershell
Get-FileHash target\installer\ThorCash-1.0.1.msi -Algorithm SHA256
Get-FileHash target\bootstrapper-output\ThorCash-Setup-1.0.1.exe -Algorithm SHA256
```

Save each hash to a `.sha256` file:

```powershell
(Get-FileHash target\installer\ThorCash-1.0.1.msi -Algorithm SHA256).Hash.ToLower() | Out-File target\installer\ThorCash-1.0.1.msi.sha256
(Get-FileHash target\bootstrapper-output\ThorCash-Setup-1.0.1.exe -Algorithm SHA256).Hash.ToLower() | Out-File target\bootstrapper-output\ThorCash-Setup-1.0.1.exe.sha256
```

## Step 3 — Tag the release

```bash
git tag -a v1.0.1 -m "ThorCash v1.0.1"
git push origin v1.0.1
```

## Step 4 — Create a GitHub Release

1. Go to https://github.com/Thorium234/kikaifinTech/releases/new
2. Select the `v1.0.1` tag
3. Title: `ThorCash v1.0.1`
4. Description (paste):

```markdown
## ThorCash v1.0.1

### What's New
- Database lives in a `database` folder next to the app (portable/installed builds) and Settings shows its location with Open Folder / Copy Path / Export a copy for safe-keeping
- Complete financial management system
- Receipt scanning and processing
- Procurement management with IQC inspection
- PDF export with structured data
- Automatic update checking via GitHub Releases

### Installation
1. Download **ThorCash-Setup-1.0.1.exe** (bootstrapper)
2. Run as Administrator
3. Follow the installation wizard

### System Requirements
- Windows 10/11 64-bit
- 4 GB RAM (8 GB recommended)
- Java Runtime bundled (no separate installation needed)
```

## Step 5 — Upload assets

Attach these files to the release:

- `target\dist\ThorCash-Portable-1.0.1.zip`
- `target\dist\ThorCash-Portable-1.0.1.zip.sha256`
- `target\installer\ThorCash-1.0.1.msi`
- `target\installer\ThorCash-1.0.1.msi.sha256`
- `target\bootstrapper-output\ThorCash-Setup-1.0.1.exe`
- `target\bootstrapper-output\ThorCash-Setup-1.0.1.exe.sha256`

## Step 6 — Publish

Click **Publish release**.

The auto-update system in the app will now detect this release and prompt users to update.
