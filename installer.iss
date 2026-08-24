; =============================================================================
; ThorCash Finance System — Inno Setup installer script
; =============================================================================
; Packages the self-contained jpackage app-image (target\app-image\ThorCash)
; into a native Windows setup wizard:
;
;   welcome -> EULA -> install folder -> progress -> "Completed Setup" screen
;   with a checked "Launch ThorCash" checkbox that opens the app on Finish.
;
; Build from the project root (version injected by build-installer.bat):
;   ISCC.exe installer.iss /DAppVersion=1.0.17
; =============================================================================

#ifndef AppVersion
#define AppVersion "1.0.17"
#endif

#define AppName "ThorCash"
#define AppDisplayName "ThorCash Finance System"
#define AppPublisher "Thor Technologies"
#define AppExe "ThorCash.exe"

[Setup]
; Stable identity for Add/Remove Programs and upgrade detection — never change.
AppId={{7E3F5A2C-9B41-4D8E-A6C0-1F2D3B4C5A6B}
AppName={#AppDisplayName}
AppVersion={#AppVersion}
AppVerName={#AppDisplayName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL=https://github.com/Thorium234/kikaifinTech
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppDisplayName}
UninstallDisplayName={#AppDisplayName}
UninstallDisplayIcon={app}\{#AppExe}
OutputDir=target\installer-output
OutputBaseFilename=ThorCash_Setup_v{#AppVersion}
SetupIconFile=src\main\resources\icon.ico
LicenseFile=src\main\installer\eula.rtf
WizardStyle=modern
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
Compression=lzma2/max
SolidCompression=yes
LZMANumBlockThreads=4
CloseApplications=yes
DisableDirPage=no
DisableProgramGroupPage=yes
ShowLanguageDialog=no

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; \
    GroupDescription: "{cm:AdditionalIcons}"

[Files]
; The complete jpackage app-image: application JARs, dependency JARs,
; JavaFX native DLLs and the bundled JRE (runtime\) — end users need no Java.
Source: "target\app-image\{#AppName}\*"; \
    DestDir: "{app}"; \
    Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppDisplayName}"; Filename: "{app}\{#AppExe}"
Name: "{group}\Uninstall {#AppDisplayName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppDisplayName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
; Creates the final "Completed Setup" screen with the launch checkbox
; (checked by default). Finish starts the JavaFX dashboard immediately.
Filename: "{app}\{#AppExe}"; \
    Description: "{cm:LaunchProgram,{#AppDisplayName}}"; \
    Flags: nowait postinstall skipifsilent

; NOTE: Uninstall preserves user data — the SQLite database lives under
; %APPDATA% and is intentionally left in place when the app is removed.
