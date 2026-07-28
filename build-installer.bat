@echo off
setlocal

echo ============================================
echo   SCHACCS v1.0.0 - Windows Installer Build
echo   Friends School Kikai Boys Secondary School
echo ============================================
echo.

:: Check Java is available
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java not found. Install JDK 21+ and add to PATH.
    exit /b 1
)

:: Check Maven is available
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Maven not found. Install Maven 3.9+ and add to PATH.
    exit /b 1
)

:: Check WiX is available (needed for .exe installer)
where candle >nul 2>&1
if %errorlevel% neq 0 (
    echo WARNING: WiX Toolset v3 not found in PATH.
    echo Attempting to add WiX to PATH...
    if exist "C:\Program Files (x86)\WiX Toolset v3.14\bin" (
        set "PATH=%PATH%;C:\Program Files (x86)\WiX Toolset v3.14\bin"
        echo WiX added to PATH.
    ) else (
        echo ERROR: WiX Toolset v3 not installed.
        echo Install it with: winget install WiXToolset.WiXToolset
        exit /b 1
    )
)

echo.
echo [1/4] Running tests...
call mvn clean test
if %errorlevel% neq 0 (
    echo ERROR: Tests failed. Fix issues before building installer.
    exit /b 1
)

echo.
echo [2/4] Building fat JAR...
call mvn package -DskipTests
if %errorlevel% neq 0 (
    echo ERROR: Package build failed.
    exit /b 1
)

echo.
echo [3/4] Preparing installer input...
if exist target\installer-input rmdir /s /q target\installer-input
mkdir target\installer-input
copy target\schaccs-1.0.0.jar target\installer-input\

:: Extract JavaFX native DLLs from win JARs
echo Extracting JavaFX native DLLs...
set "REPO=%USERPROFILE%\.m2\repository\org\openjfx"

:: Use PowerShell to extract DLLs from the platform-specific JARs
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Add-Type -AssemblyName System.IO.Compression.FileSystem; ^
     $inputDir = 'target\installer-input'; ^
     $jars = @('javafx-graphics','javafx-media','javafx-web','javafx-swing'); ^
     foreach ($name in $jars) { ^
         $jar = Join-Path $env:USERPROFILE '.m2\repository\org\openjfx' $name '21.0.6' ('$name-21.0.6-win.jar'); ^
         if (Test-Path $jar) { ^
             Write-Host ('  Extracting from ' + $name); ^
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
echo [4/4] Building Windows installer with jpackage...
if exist target\installer-output rmdir /s /q target\installer-output
mkdir target\installer-output

jpackage ^
    --type exe ^
    --dest target\installer-output ^
    --name "SCHACCS" ^
    --app-version "1.0.0" ^
    --input target\installer-input ^
    --main-jar schaccs-1.0.0.jar ^
    --main-class com.schaccs.Launcher ^
    --icon src\main\resources\app-icon.ico ^
    --vendor "Friends School Kikai Boys" ^
    --win-shortcut ^
    --win-menu ^
    --win-dir-chooser ^
    --java-options "-Xmx512m" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Djava.library.path=."

if %errorlevel% neq 0 (
    echo ERROR: jpackage failed.
    exit /b 1
)

echo.
echo ============================================
echo   BUILD SUCCESSFUL
echo ============================================
echo   Installer: target\installer-output\SCHACCS-1.0.0.exe
echo.
echo   Features:
echo     - Self-contained (no Java install needed)
echo     - Desktop shortcut
echo     - Start Menu entry
echo     - Custom install directory
echo     - Uninstaller included
echo ============================================

endlocal
