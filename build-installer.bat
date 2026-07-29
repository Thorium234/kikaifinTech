@echo off
setlocal

echo ============================================================================
echo   ThorCash Financial System - Full Installer Build
echo   Thor Technologies
echo ============================================================================
echo.
echo This script produces a ready-to-share Windows installer (.exe).
echo You ONLY need to share the final .exe file — nothing else.
echo.
echo Prerequisites:
echo   - JDK 21+         (java -version)
echo   - Maven 3.9+      (mvn -version)
echo   - WiX Toolset v3  (candle)
echo.
echo ============================================================================
echo.

:: Check Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [FAIL] Java not found. Install JDK 21+ from https://adoptium.net
    exit /b 1
) else (
    echo [OK]   Java found
)

:: Check Maven
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [FAIL] Maven not found. Install from https://maven.apache.org
    exit /b 1
) else (
    echo [OK]   Maven found
)

:: Check WiX
set "WIX_PATH=C:\Program Files (x86)\WiX Toolset v3.14\bin"
where candle >nul 2>&1
if %errorlevel% neq 0 (
    if exist "%WIX_PATH%\candle.exe" (
        set "PATH=%PATH%;%WIX_PATH%"
        echo [OK]   WiX found (added to PATH)
    ) else (
        echo [FAIL] WiX Toolset v3 not found.
        echo        Install with: winget install WiXToolset.WiXToolset
        exit /b 1
    )
) else (
    echo [OK]   WiX found
)

set "APP_JAR=thorcash-1.0.0.jar"
set "MSI_DIR=target\installer"
set "INPUT_DIR=target\installer-input"
set "LIBS_DIR=target\libs"
set "BOOTSTRAPPER_SCRIPT=src\main\installer\build-bootstrapper.bat"

echo.
echo ============================================================================
echo   STEP 1/5: Run tests
echo   Verifying all 80 tests pass before building...
echo ============================================================================
echo.
call mvn clean test
if %errorlevel% neq 0 (
    echo [FAIL] Tests failed. Fix issues before building installer.
    exit /b 1
)
echo [OK]   All tests passed

echo.
echo ============================================================================
echo   STEP 2/5: Package application
echo   Compiling source and bundling dependencies...
echo ============================================================================
echo.
call mvn package -DskipTests
if %errorlevel% neq 0 (
    echo [FAIL] Package build failed.
    exit /b 1
)
echo [OK]   Application packaged (target\%APP_JAR%)

echo.
echo ============================================================================
echo   STEP 3/5: Assemble installer input
echo   Copying JAR, libraries, and JavaFX native DLLs into one folder...
echo ============================================================================
echo.
if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
mkdir "%INPUT_DIR%"

copy "target\%APP_JAR%" "%INPUT_DIR%\" >nul
if exist "%LIBS_DIR%" (
    xcopy /s /q /y "%LIBS_DIR%\*" "%INPUT_DIR%\" >nul
)

echo Extracting JavaFX native DLLs (glass, prism, etc.)...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Add-Type -AssemblyName System.IO.Compression.FileSystem; ^
     $inputDir = '%INPUT_DIR%'; ^
     $jars = @('javafx-graphics','javafx-media','javafx-web','javafx-swing'); ^
     foreach ($name in $jars) { ^
         $jar = Join-Path $env:USERPROFILE '.m2\repository\org\openjfx' $name '21.0.6' ('$name-21.0.6-win.jar'); ^
         if (Test-Path $jar) { ^
             $zip = [System.IO.Compression.ZipFile]::OpenRead($jar); ^
             foreach ($entry in $zip.Entries) { ^
                 if ($entry.Name -like '*.dll') { ^
                     $dest = Join-Path $inputDir $entry.Name; ^
                     if (-not (Test-Path $dest)) { ^
                         [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true) ^
                     } ^
                 } ^
             }; ^
             $zip.Dispose() ^
         } ^
     }"
echo [OK]   Installer input ready

echo.
echo ============================================================================
echo   STEP 4/5: Build MSI with jpackage
echo   Creating Windows Installer (.msi) with shortcuts, icon, splash screen...
echo ============================================================================
echo.
if exist "%MSI_DIR%" rmdir /s /q "%MSI_DIR%"
mkdir "%MSI_DIR%"

jpackage ^
    --type msi ^
    --dest "%MSI_DIR%" ^
    --name "ThorCash" ^
    --app-version "1.0.0" ^
    --input "%INPUT_DIR%" ^
    --main-jar "%APP_JAR%" ^
    --main-class com.schaccs.Launcher ^
    --icon src/main/resources/icon.ico ^
    --vendor "Thor Technologies" ^
    --license-file src/main/installer/eula.rtf ^
    --win-shortcut ^
    --win-menu ^
    --win-dir-chooser ^
    --java-options "-Xmx512m" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Djava.library.path=." ^
    --splash src/main/resources/Splashscreen.png

if %errorlevel% neq 0 (
    echo.
    echo [FAIL] jpackage failed. Common causes:
    echo   - WiX Toolset v3 not installed or not on PATH
    echo   - Icon file missing at src/main/resources/icon.ico
    echo   - EULA file missing at src/main/installer/eula.rtf
    echo   - JavaFX DLL extraction failed (missing DLLs)
    exit /b 1
)
echo [OK]   MSI built: %MSI_DIR%\ThorCash-1.0.0.msi

echo.
echo ============================================================================
echo   STEP 5/5: Build bootstrapper EXE (the file you share)
echo   Wrapping the MSI into a professional installer with:
echo     - Custom Fluent Design theme
echo     - License agreement page
echo     - Folder picker
echo     - Progress bar
echo     - Launch-on-finish checkbox
echo ============================================================================
echo.
call "%BOOTSTRAPPER_SCRIPT%"
if %errorlevel% neq 0 (
    echo [FAIL] Bootstrapper build failed.
    exit /b 1
)

echo.
echo ============================================================================
echo   DONE! Your installer is ready to share.
echo ============================================================================
echo.
echo   SHARE THIS FILE:
echo     target\bootstrapper-output\ThorCash-Setup-1.0.0.exe
echo.
echo   Your friend double-clicks it and installs like any normal program.
echo   No Java, no Maven, no WiX needed on their computer.
echo.
echo   Features:
echo     - Desktop shortcut named "ThorCash"
echo     - Start Menu entry under "Thor Technologies"
echo     - Shows in "Installed Apps" as "ThorCash"
echo     - Custom splash screen on launch
echo     - Uninstallable via Add/Remove Programs
echo     - Self-contained (bundles its own Java runtime)
echo.
echo   Note: The MSI at target\installer\ThorCash-1.0.0.msi
echo         is an intermediate file. You only need the .exe.
echo.
echo ============================================================================

endlocal
