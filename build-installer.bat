@echo off
setlocal

echo ============================================
echo   SCHACCS v1.0.0 - Windows Installer Build
echo   Friends School Kikai Boys Secondary School
echo   Republic of Kenya, Ministry of Education
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

:: Check WiX is available (needed for .msi installer)
where candle >nul 2>&1
if %errorlevel% neq 0 (
    where wix >nul 2>&1
    if %errorlevel% neq 0 (
        echo WARNING: WiX Toolset not found in PATH.
        if exist "C:\Program Files (x86)\WiX Toolset v3.14\bin" (
            set "PATH=%PATH%;C:\Program Files (x86)\WiX Toolset v3.14\bin"
            echo WiX added to PATH from Program Files.
        ) else (
            echo ERROR: WiX Toolset v3 not installed.
            echo Install with: winget install WiXToolset.WiXToolset
            exit /b 1
        )
    )
)

set "APP_JAR=schaccs-1.0.0.jar"
set "DEST_DIR=target\installer"
set "INPUT_DIR=target\installer-input"
set "LIBS_DIR=target\libs"

echo.
echo [1/4] Running tests...
call mvn clean test
if %errorlevel% neq 0 (
    echo ERROR: Tests failed. Fix issues before building installer.
    exit /b 1
)

echo.
echo [2/4] Packaging application and dependencies...
call mvn package -DskipTests
if %errorlevel% neq 0 (
    echo ERROR: Package build failed.
    exit /b 1
)

echo.
echo [3/4] Assembling installer input directory...
if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
mkdir "%INPUT_DIR%"

:: Copy app JAR and all dependency JARs into a single flat folder
copy "target\%APP_JAR%" "%INPUT_DIR%\" >nul
if exist "%LIBS_DIR%" (
    xcopy /s /q /y "%LIBS_DIR%\*" "%INPUT_DIR%\" >nul
)

:: Extract JavaFX native DLLs from platform win JARs
echo Extracting JavaFX native libraries...
set "REPO=%USERPROFILE%\.m2\repository\org\openjfx"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Add-Type -AssemblyName System.IO.Compression.FileSystem; ^
     $inputDir = '%INPUT_DIR%'; ^
     $jars = @('javafx-graphics','javafx-media','javafx-web','javafx-swing'); ^
     foreach ($name in $jars) { ^
         $jar = Join-Path $env:USERPROFILE '.m2\repository\org\openjfx' $name '21.0.6' ('$name-21.0.6-win.jar'); ^
         if (Test-Path $jar) { ^
             Write-Host ('  -> ' + $name); ^
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
echo [4/4] Building Windows MSI installer with jpackage...
if exist "%DEST_DIR%" rmdir /s /q "%DEST_DIR%"
mkdir "%DEST_DIR%"

jpackage ^
    --type msi ^
    --dest "%DEST_DIR%" ^
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
echo ============================================
echo   BUILD SUCCESSFUL
echo ============================================
echo   Installer: %DEST_DIR%\SCHACCS-1.0.0.msi
echo.
echo   Features:
echo     - Self-contained (private JRE, no Java install needed)
echo     - Desktop shortcut
echo     - Start Menu entry
echo     - Custom install directory (C:\Program Files\SCHACCS)
echo     - EULA acceptance screen
echo     - Add/Remove Programs uninstaller
echo ============================================

endlocal
