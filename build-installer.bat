@echo off
setlocal enabledelayedexpansion

set "APP_NAME=ThorCash"
set "APP_VERSION=1.0.16"
set "APP_VENDOR=Thor Technologies"
set "APP_MAIN_CLASS=com.schaccs.Launcher"
set "APP_JAR=thorcash-%APP_VERSION%.jar"

set "JAVA_HOME=C:\Program Files\Java\jdk-26.0.2"
set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
set "JLINK=%JAVA_HOME%\bin\jlink.exe"

set "WORK_DIR=%CD%"
set "INPUT_DIR=%WORK_DIR%\target\installer-input"
set "LIBS_DIR=%WORK_DIR%\target\libs"
set "RUNTIME_DIR=%WORK_DIR%\target\runtime"
set "APP_IMAGE_DIR=%WORK_DIR%\target\app-image"
set "DIST_DIR=%WORK_DIR%\target\dist"
set "SETUP_OUT=%WORK_DIR%\target\installer-output"
set "INNO_SCRIPT=%WORK_DIR%\installer.iss"

echo ============================================================================
echo   %APP_NAME% %APP_VERSION% - Full Installer Build
echo   %APP_VENDOR%
echo   Native setup wizard via jpackage app-image + Inno Setup
echo ============================================================================
echo.
echo Prerequisites:
echo   - JDK 21+ at %JAVA_HOME%
echo   - Maven 3.9+  (mvn -version)
echo   - Inno Setup 6 (ISCC.exe)
echo.

:: ============================================================================
:: STEP 0/8: Environment verification
:: ============================================================================
echo ---------------------------------------------------------------------------
echo   STEP 0/8: Verify environment
echo ---------------------------------------------------------------------------
echo.

:: Check Java
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [FAIL] JDK not found at %JAVA_HOME%
    echo        Set JAVA_HOME to a JDK 21+ installation.
    exit /b 1
)
echo [OK]   Java: %JAVA_HOME%

:: Check jlink
if not exist "%JLINK%" (
    echo [FAIL] jlink not found at %JLINK%
    exit /b 1
)
echo [OK]   jlink: %JLINK%

:: Check jpackage
if not exist "%JPACKAGE%" (
    echo [FAIL] jpackage not found at %JPACKAGE%
    exit /b 1
)
echo [OK]   jpackage: %JPACKAGE%

:: Check Maven
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo [FAIL] Maven not found. Install from https://maven.apache.org
    exit /b 1
)
echo [OK]   Maven

:: Check Inno Setup Compiler (ISCC)
set "ISCC="
where iscc >nul 2>&1 && set "ISCC=iscc"
if not defined ISCC (
    for %%P in ("%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe" "%ProgramFiles%\Inno Setup 6\ISCC.exe") do (
        if exist %%P set "ISCC=%%~P"
    )
)
if not defined ISCC (
    echo [FAIL] Inno Setup 6 not found.
    echo        Install it with:  winget install JRSoftware.InnoSetup
    echo        (or from https://jrsoftware.org/isdl.php^) and re-run this script.
    exit /b 1
)
echo [OK]   Inno Setup: !ISCC!

:: Check required assets
if not exist "src\main\resources\icon.ico" (
    echo [FAIL] Icon missing: src\main\resources\icon.ico
    exit /b 1
)
if not exist "src\main\installer\eula.rtf" (
    echo [FAIL] EULA missing: src\main\installer\eula.rtf
    exit /b 1
)
echo [OK]   Icon and EULA present

echo.
echo ============================================================================
echo   STEP 1/8: Run tests
echo   mvn clean test
echo ============================================================================
echo.
call mvn clean test
if %errorlevel% neq 0 (
    echo [FAIL] Tests failed. Fix issues before building installer.
    exit /b 1
)
echo [OK]   All tests passed
echo.

:: ============================================================================
:: STEP 2/8: Build application JAR and copy dependencies
::   Creates:
::     target/%APP_JAR%   - executable JAR
::     target/libs/*.jar  - all runtime dependency JARs
:: ============================================================================
echo ============================================================================
echo   STEP 2/8: Package application
echo   mvn package -DskipTests
echo ============================================================================
echo.
call mvn package -DskipTests
if %errorlevel% neq 0 (
    echo [FAIL] Package build failed.
    exit /b 1
)
if not exist "target\%APP_JAR%" (
    echo [FAIL] %APP_JAR% not created.
    exit /b 1
)
echo [OK]   Application packaged
echo.

:: ============================================================================
:: STEP 3/8: Create runtime with jlink
::   Minimal JRE containing only the modules the app needs. Explicitly built
::   (instead of relying on jpackage's internal jlink) because some JDK
::   versions generate a runtime missing java.exe/javaw.exe, which breaks
::   application launching.
:: ============================================================================
echo ============================================================================
echo   STEP 3/8: Create runtime with jlink
echo ============================================================================
echo.
if exist "%RUNTIME_DIR%" rmdir /s /q "%RUNTIME_DIR%"

"%JLINK%" ^
    --module-path "%JAVA_HOME%\jmods" ^
    --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.security.jgss,java.sql,java.xml,java.xml.crypto,jdk.jfr,jdk.unsupported,jdk.zipfs,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.management,jdk.management.agent ^
    --no-header-files ^
    --no-man-pages ^
    --strip-debug ^
    --compress=2 ^
    --output "%RUNTIME_DIR%"

if %errorlevel% neq 0 (
    echo [FAIL] jlink failed.
    exit /b 1
)

if not exist "%RUNTIME_DIR%\bin\java.exe" (
    echo [FAIL] jlink runtime is missing java.exe!
    exit /b 1
)
if not exist "%RUNTIME_DIR%\bin\javaw.exe" (
    echo [FAIL] jlink runtime is missing javaw.exe!
    exit /b 1
)
echo [OK]   Runtime created: %RUNTIME_DIR%
for /f "tokens=3" %%a in ('dir /s /a-d /-c "%RUNTIME_DIR%" 2^>nul ^| find "File(s)"') do set RUNTIME_SIZE_BYTES=%%a
if defined RUNTIME_SIZE_BYTES set /a RUNTIME_SIZE_MB=RUNTIME_SIZE_BYTES/1048576
if defined RUNTIME_SIZE_MB (echo        Size: !RUNTIME_SIZE_MB! MB) else (echo        Size: unknown)
echo.

:: ============================================================================
:: STEP 4/8: Assemble installer input directory
::   Main JAR + dependency JARs + JavaFX native DLLs. jpackage copies these
::   into the app image's app\ subdirectory.
:: ============================================================================
echo ============================================================================
echo   STEP 4/8: Assemble installer input
echo ============================================================================
echo.
if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
mkdir "%INPUT_DIR%"

copy "target\%APP_JAR%" "%INPUT_DIR%\" >nul
if %errorlevel% neq 0 (
    echo [FAIL] Could not copy %APP_JAR%
    exit /b 1
)
echo [OK]   Main JAR copied

if exist "%LIBS_DIR%" (
    xcopy /s /q /y "%LIBS_DIR%\*" "%INPUT_DIR%\" >nul
    echo [OK]   Dependencies copied
) else (
    echo [WARN] No libs directory found
)

:: Extract JavaFX native DLLs (glass.dll, prism_d3d.dll, ...) so that
:: -Djava.library.path=. resolves them at runtime.
echo Extracting JavaFX native DLLs...
powershell -NoProfile -ExecutionPolicy Bypass -File "src\main\installer\extract-javafx-dlls.ps1" -OutputDir "%INPUT_DIR%"

echo [OK]   Installer input ready at %INPUT_DIR%
echo.

:: ============================================================================
:: STEP 5/8: Create self-contained app image with jpackage
::   One app image feeds BOTH distribution artifacts:
::     - the portable ZIP (STEP 6)
::     - the native setup EXE compiled by Inno Setup (STEP 7)
:: ============================================================================
echo ============================================================================
echo   STEP 5/8: Build app image with jpackage
echo ============================================================================
echo.
if exist "%APP_IMAGE_DIR%" rmdir /s /q "%APP_IMAGE_DIR%"

"%JPACKAGE%" ^
    --type app-image ^
    --runtime-image "%RUNTIME_DIR%" ^
    --dest "%APP_IMAGE_DIR%" ^
    --name "%APP_NAME%" ^
    --app-version "%APP_VERSION%" ^
    --input "%INPUT_DIR%" ^
    --main-jar "%APP_JAR%" ^
    --main-class "%APP_MAIN_CLASS%" ^
    --icon src\main\resources\icon.ico ^
    --vendor "%APP_VENDOR%" ^
    --java-options "-Xmx512m" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Djava.library.path=."

if %errorlevel% neq 0 (
    echo [FAIL] jpackage app-image failed.
    exit /b 1
)
if not exist "%APP_IMAGE_DIR%\%APP_NAME%\%APP_NAME%.exe" (
    echo [FAIL] App image not created at %APP_IMAGE_DIR%\%APP_NAME%\%APP_NAME%.exe
    exit /b 1
)
echo [OK]   App image created: %APP_IMAGE_DIR%\%APP_NAME%\
echo.

:: ============================================================================
:: STEP 6/8: Package portable ZIP
::   No installer, no admin rights, no JVM needed — unzip anywhere.
:: ============================================================================
echo ============================================================================
echo   STEP 6/8: Build portable ZIP
echo ============================================================================
echo.
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
powershell -NoProfile -Command "Compress-Archive -Path '%APP_IMAGE_DIR%\%APP_NAME%\*' -DestinationPath '%DIST_DIR%\%APP_NAME%-Portable-%APP_VERSION%.zip' -Force"
if not exist "%DIST_DIR%\%APP_NAME%-Portable-%APP_VERSION%.zip" (
    echo [FAIL] Portable ZIP not created.
    exit /b 1
)
for %%f in ("%DIST_DIR%\%APP_NAME%-Portable-%APP_VERSION%.zip") do set ZIP_SIZE=%%~zf
set /a ZIP_SIZE_MB=%ZIP_SIZE%/1048576
echo [OK]   Portable ZIP created: %DIST_DIR%\%APP_NAME%-Portable-%APP_VERSION%.zip (%ZIP_SIZE_MB% MB)
echo.

:: ============================================================================
:: STEP 7/8: Compile native setup wizard with Inno Setup
::   Produces ThorCash_Setup_v<version>.exe: welcome -> EULA -> folder picker
::   -> progress -> Completed Setup screen with "Launch ThorCash" checkbox.
:: ============================================================================
echo ============================================================================
echo   STEP 7/8: Compile setup wizard (Inno Setup)
echo ============================================================================
echo.
if not exist "%SETUP_OUT%" mkdir "%SETUP_OUT%"

"!ISCC!" /Q /DAppVersion=%APP_VERSION% "%INNO_SCRIPT%"
if %errorlevel% neq 0 (
    echo [FAIL] Inno Setup compilation failed.
    exit /b 1
)

set "SETUP_EXE=%SETUP_OUT%\ThorCash_Setup_v%APP_VERSION%.exe"
if not exist "%SETUP_EXE%" (
    echo [FAIL] Setup EXE not created at expected path: %SETUP_EXE%
    dir "%SETUP_OUT%"
    exit /b 1
)
for %%f in ("%SETUP_EXE%") do set SETUP_SIZE=%%~zf
set /a SETUP_SIZE_MB=%SETUP_SIZE%/1048576
echo [OK]   Setup wizard created: %SETUP_EXE% (%SETUP_SIZE_MB% MB)
echo.

:: ============================================================================
:: STEP 8/8: Generate SHA-256 checksums
:: ============================================================================
echo ============================================================================
echo   STEP 8/8: Generate SHA-256 checksums
echo ============================================================================
echo.
powershell -NoProfile -Command "& { $exe='%SETUP_EXE%'; $zip='%DIST_DIR%\%APP_NAME%-Portable-%APP_VERSION%.zip'; $h=(Get-FileHash $exe -Algorithm SHA256).Hash.ToLower(); Write-Host ('EXE:  '+$h); $h2=(Get-FileHash $zip -Algorithm SHA256).Hash.ToLower(); Write-Host ('ZIP:  '+$h2); $h+'  '+(Get-Item $exe).Name | Out-File 'target\checksums.txt' -Encoding utf8; $h2+'  '+(Get-Item $zip).Name | Out-File 'target\checksums.txt' -Encoding utf8 -Append }"

echo [OK]   Checksums saved to target\checksums.txt
echo.

:: ============================================================================
:: Summary
:: ============================================================================
echo ============================================================================
echo   BUILD COMPLETE
echo ============================================================================
echo.
echo   Artifacts:
echo     %SETUP_EXE% (%SETUP_SIZE_MB% MB)
echo     %DIST_DIR%\%APP_NAME%-Portable-%APP_VERSION%.zip (%ZIP_SIZE_MB% MB)
echo.
echo   To install:
echo     Double-click ThorCash_Setup_v%APP_VERSION%.exe and follow the wizard.
echo     The final screen offers "Launch ThorCash" (checked by default).
echo     Admin rights are required; installs to C:\Program Files\ThorCash.
echo.
echo   Silent install:
echo     ThorCash_Setup_v%APP_VERSION%.exe /VERYSILENT /NORESTART
echo.
echo   To uninstall:
echo     Settings ^> Apps ^> Installed Apps, or Start Menu ^> Uninstall ThorCash.
echo     User data in %%APPDATA%% is preserved.
echo ============================================================================

endlocal
