@echo off
setlocal

echo ============================================================================
echo   SCHACCS Financial System - Full Installer Build
echo   Friends School Kikai Boys Secondary School
echo   Republic of Kenya, Ministry of Education
echo ============================================================================
echo.

:: Check Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java not found. Install JDK 21+ and add to PATH.
    exit /b 1
)

:: Check Maven
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Maven not found. Install Maven 3.9+ and add to PATH.
    exit /b 1
)

:: Check WiX (needed for both MSI and Burn bootstrapper)
set "WIX_PATH=C:\Program Files (x86)\WiX Toolset v3.14\bin"
where candle >nul 2>&1
if %errorlevel% neq 0 (
    if exist "%WIX_PATH%\candle.exe" (
        set "PATH=%PATH%;%WIX_PATH%"
        echo WiX added to PATH.
    ) else (
        echo ERROR: WiX Toolset v3 not installed.
        echo Install with: winget install WiXToolset.WiXToolset
        exit /b 1
    )
)

set "APP_JAR=schaccs-1.0.0.jar"
set "MSI_DIR=target\installer"
set "INPUT_DIR=target\installer-input"
set "LIBS_DIR=target\libs"
set "BOOTSTRAPPER_SCRIPT=src\main\installer\build-bootstrapper.bat"

echo.
echo [1/5] Running tests...
call mvn clean test
if %errorlevel% neq 0 (
    echo ERROR: Tests failed. Fix issues before building installer.
    exit /b 1
)

echo.
echo [2/5] Packaging application and dependencies...
call mvn package -DskipTests
if %errorlevel% neq 0 (
    echo ERROR: Package build failed.
    exit /b 1
)

echo.
echo [3/5] Assembling installer input directory...
if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
mkdir "%INPUT_DIR%"

:: Copy app JAR and dependency JARs
copy "target\%APP_JAR%" "%INPUT_DIR%\" >nul
if exist "%LIBS_DIR%" (
    xcopy /s /q /y "%LIBS_DIR%\*" "%INPUT_DIR%\" >nul
)

:: Extract JavaFX native DLLs
echo Extracting JavaFX native libraries...
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

echo.
echo [4/5] Building Windows MSI installer with jpackage...
if exist "%MSI_DIR%" rmdir /s /q "%MSI_DIR%"
mkdir "%MSI_DIR%"

jpackage ^
    --type msi ^
    --dest "%MSI_DIR%" ^
    --name "SCHACCS" ^
    --app-version "1.0.0" ^
    --input "%INPUT_DIR%" ^
    --main-jar "%APP_JAR%" ^
    --main-class com.schaccs.Launcher ^
    --icon src/main/resources/assets/icon.ico ^
    --vendor "Friends School Kikai Boys" ^
    --license-file src/main/installer/eula.rtf ^
    --win-shortcut ^
    --win-menu ^
    --win-dir-chooser ^
    --java-options "-Xmx512m" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Djava.library.path=."

if %errorlevel% neq 0 (
    echo.
    echo ERROR: jpackage failed. Common causes:
    echo   - WiX Toolset v3 not installed or not on PATH
    echo   - Icon file missing at src/main/resources/assets/icon.ico
    echo   - EULA file missing at src/main/installer/eula.rtf
    exit /b 1
)

echo.
echo [5/5] Building professional bootstrapper EXE...
call "%BOOTSTRAPPER_SCRIPT%"
if %errorlevel% neq 0 (
    echo ERROR: Bootstrapper build failed.
    exit /b 1
)

echo.
echo ============================================================================
echo   FULL BUILD SUCCESSFUL
echo ============================================================================
echo   MSI Installer:       %MSI_DIR%\SCHACCS-1.0.0.msi
echo   Bootstrapper EXE:    target\bootstrapper-output\SCHACCS-Setup-1.0.0.exe
echo.
echo   End-user runs: SCHACCS-Setup-1.0.0.exe
echo   Features:
echo     - Professional Fluent Design UI
echo     - License Agreement with acceptance required
echo     - Custom installation folder with Browse
echo     - Modern progress bar
echo     - Launch on finish option
echo     - Windows version / disk space / admin checks
echo     - Rollback on failure
echo     - Silent install: /quiet /passive /log log.txt
echo     - Add/Remove Programs uninstall support
echo     - Self-contained (private JRE, no Java install needed)
echo ============================================================================

endlocal
