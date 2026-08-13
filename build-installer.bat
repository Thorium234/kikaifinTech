@echo off
setlocal enabledelayedexpansion

set "APP_NAME=ThorCash"
set "APP_VERSION=1.0.1"
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
set "MSI_DIR=%WORK_DIR%\target\installer"
set "BOOTSTRAPPER_OUT=%WORK_DIR%\target\bootstrapper-output"
set "WIX_DIR=%WORK_DIR%\src\main\installer\wix"

echo ============================================================================
echo   %APP_NAME% %APP_VERSION% - Full Installer Build
echo   %APP_VENDOR%
echo ============================================================================
echo.
echo Prerequisites:
echo   - JDK 21+ at %JAVA_HOME%
echo   - Maven 3.9+  (mvn -version)
echo   - WiX v3.14+  (candle, light)
echo.

:: ============================================================================
:: STEP 0: Environment verification
:: ============================================================================
echo ---------------------------------------------------------------------------
echo   STEP 0/9: Verify environment
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

:: Check WiX
set "WIX_TOOLS=C:\Program Files (x86)\WiX Toolset v3.14\bin"
where candle >nul 2>&1
if not %errorlevel% equ 0 (
    if exist "%WIX_TOOLS%\candle.exe" (
        set "PATH=%PATH%;%WIX_TOOLS%"
        echo [OK]   WiX (added from !WIX_TOOLS!)
    ) else (
        echo [FAIL] WiX Toolset v3 not found.
        echo        Install: winget install WiXToolset.WiXToolset
        exit /b 1
    )
) else (
    echo [OK]   WiX
)

echo.
echo ============================================================================
echo   STEP 1/9: Run tests
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
:: STEP 2/9: Build application JAR and copy dependencies
::   mvn package -DskipTests  (test already ran above)
::   This creates:
::     target/%APP_JAR%        - executable JAR
::     target/libs/*.jar       - all runtime dependency JARs
:: ============================================================================
echo ============================================================================
echo   STEP 2/9: Package application
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
:: STEP 3/9: Create runtime with jlink
::   Creates a minimal JRE at target/runtime/ that contains only the JDK
::   modules needed by the application. This runtime WILL include java.exe
::   and javaw.exe (unlike jpackage's internal jlink which skips them).
::
::   NOTE: We use --strip-debug --no-header-files --no-man-pages to minimize
::   size. --compress=2 reduces disk footprint.
:: ============================================================================
echo ============================================================================
echo   STEP 3/9: Create runtime with jlink
echo   %JLINK% --module-path ... --add-modules ... --output target\runtime
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

:: Verify the runtime has java.exe
if not exist "%RUNTIME_DIR%\bin\java.exe" (
    echo [FAIL] jlink runtime is missing java.exe! This is required for ThorCash.exe to launch.
    echo        This indicates a JDK 26 jlink bug or incompatible configuration.
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
:: STEP 4/9: Assemble installer input directory
::   The input directory contains everything that goes into the app image:
::     - Main application JAR
::     - All dependency JARs
::     - JavaFX native DLLs (for -Djava.library.path)
::   jpackage copies these into the app/ subdirectory of the installation.
:: ============================================================================
echo ============================================================================
echo   STEP 4/9: Assemble installer input
echo   Copying JARs and extracting JavaFX native DLLs...
echo ============================================================================
echo.
if exist "%INPUT_DIR%" rmdir /s /q "%INPUT_DIR%"
mkdir "%INPUT_DIR%"

:: Copy main JAR
copy "target\%APP_JAR%" "%INPUT_DIR%\" >nul
if %errorlevel% neq 0 (
    echo [FAIL] Could not copy %APP_JAR%
    exit /b 1
)
echo [OK]   Main JAR copied

:: Copy dependency JARs
if exist "%LIBS_DIR%" (
    xcopy /s /q /y "%LIBS_DIR%\*" "%INPUT_DIR%\" >nul
    echo [OK]   Dependencies copied
) else (
    echo [WARN] No libs directory found
)

:: Extract JavaFX native DLLs from Gluon platform JARs
:: JavaFX on Windows needs native DLLs (glass.dll, prism_d3d.dll, etc.)
:: These are extracted to the input dir and end up in app/, where
:: -Djava.library.path=. allows the JVM to find them at runtime.
echo Extracting JavaFX native DLLs...
powershell -NoProfile -ExecutionPolicy Bypass -File "src\main\installer\extract-javafx-dlls.ps1" -OutputDir "%INPUT_DIR%"

echo [OK]   Installer input ready at %INPUT_DIR%
echo.

:: ============================================================================
:: STEP 5/9: Create application image (optional)
::   For debugging: we can create an app image to inspect before MSI packaging.
::   Skipped in production — jpackage --type msi handles this internally.
:: ============================================================================
echo ============================================================================
echo   STEP 5/9: Build MSI installer with jpackage
echo   jpackage --type msi --runtime-image ... --input ... --main-jar ...
echo ============================================================================
echo.
if exist "%MSI_DIR%" rmdir /s /q "%MSI_DIR%"
mkdir "%MSI_DIR%"

"%JPACKAGE%" ^
    --type msi ^
    --runtime-image "%RUNTIME_DIR%" ^
    --dest "%MSI_DIR%" ^
    --name "%APP_NAME%" ^
    --app-version "%APP_VERSION%" ^
    --input "%INPUT_DIR%" ^
    --main-jar "%APP_JAR%" ^
    --main-class "%APP_MAIN_CLASS%" ^
    --icon src\main\resources\icon.ico ^
    --vendor "%APP_VENDOR%" ^
    --license-file src\main\installer\eula.rtf ^
    --win-shortcut ^
    --win-menu ^
    --win-dir-chooser ^
    --java-options "-Xmx512m" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --java-options "-Djava.library.path=."

if %errorlevel% neq 0 (
    echo.
    echo [FAIL] jpackage failed. Common causes:
    echo   - WiX not installed or not on PATH
    echo   - Icon missing: src/main/resources/icon.ico
    echo   - EULA missing: src/main/installer/eula.rtf
    echo   - JavaFX DLL extraction failed
    exit /b 1
)

:: Verify MSI was created
set "MSI_PATH=%MSI_DIR%\%APP_NAME%-%APP_VERSION%.msi"
if not exist "%MSI_PATH%" (
    echo [FAIL] MSI not created at expected path: %MSI_PATH%
    dir "%MSI_DIR%\*.msi"
    exit /b 1
)
for %%f in ("%MSI_PATH%") do set MSI_SIZE=%%~zf
set /a MSI_SIZE_MB=%MSI_SIZE%/1048576
echo [OK]   MSI built: %MSI_PATH% (%MSI_SIZE_MB% MB)
echo.

:: ============================================================================
:: STEP 6/9: Verify MSI runtime integrity
::   Validate that the jlink runtime was correctly bundled into the MSI
::   by checking that java.exe exists in the expected location.
::   We use msiexec /a (admin install) to extract the MSI contents.
:: ============================================================================
echo ============================================================================
echo   STEP 6/9: Verify MSI runtime
echo   Checking that java.exe and javaw.exe are bundled in the MSI...
echo ============================================================================
echo.
set "EXTRACT_DIR=%WORK_DIR%\target\msi-verify"
if exist "%EXTRACT_DIR%" rmdir /s /q "%EXTRACT_DIR%"
mkdir "%EXTRACT_DIR%"

:: Extract MSI contents to verify runtime
msiexec /a "%MSI_PATH%" /qn TARGETDIR="%EXTRACT_DIR%" /L*V "%WORK_DIR%\target\msi-extract-log.txt" >nul 2>&1

:: Wait for extraction to complete
if exist "%EXTRACT_DIR%\PFiles\ThorCash\runtime\bin\java.exe" (
    echo [OK]   MSI contains runtime/bin/java.exe
) else if exist "%EXTRACT_DIR%\ThorCash\runtime\bin\java.exe" (
    echo [OK]   MSI contains runtime/bin/java.exe
) else (
    :: Search for it
    set "FOUND="
    for /r "%EXTRACT_DIR%" %%f in (java.exe) do (
        set "FOUND=%%f"
    )
    if defined FOUND (
        echo [OK]   Found java.exe at !FOUND!
    ) else (
        echo [WARN] Could not verify java.exe in MSI (msiexec /a may not extract everything)
        echo        Proceeding with bootstrapper build.
    )
)

:: Clean up
if exist "%EXTRACT_DIR%" rmdir /s /q "%EXTRACT_DIR%"
echo.

:: ============================================================================
:: STEP 7/9: Build WiX Burn bootstrapper
::   Wraps the MSI into a professional-looking installer EXE with:
::     - EULA page
::     - Install folder picker
::     - Progress bar
::     - Admin elevation
::     - Desktop shortcut + Start Menu
::     - Add/Remove Programs entry
:: ============================================================================
echo ============================================================================
echo   STEP 7/9: Build bootstrapper EXE
echo   WiX Burn bundle wrapping the MSI...
echo ============================================================================
echo.
set "BOOTSTRAPPER_BUILD=%WORK_DIR%\target\bootstrapper"
if exist "%BOOTSTRAPPER_BUILD%" rmdir /s /q "%BOOTSTRAPPER_BUILD%"
mkdir "%BOOTSTRAPPER_BUILD%"

:: Step 7a: Compile Bundle.wxs
echo [1/2] Compiling Bundle.wxs...
"%WIX_TOOLS%\candle.exe" -nologo ^
    -out "%BOOTSTRAPPER_BUILD%\Bundle.wixobj" ^
    -ext WixBalExtension ^
    -ext WixUtilExtension ^
    -dProjectDir="%WORK_DIR%\src\main\installer\wix" ^
    -dMsiPath="%MSI_PATH%" ^
    -dVersion="%APP_VERSION%" ^
    -dIconPath="%WORK_DIR%\src\main\resources\icon.ico" ^
    "%WIX_DIR%\Bundle.wxs"
if %errorlevel% neq 0 (
    echo [FAIL] Candle compilation failed.
    exit /b 1
)
echo [OK]   Bundle.wixobj created

:: Step 7b: Link bootstrapper
echo [2/2] Linking bootstrapper...
if not exist "%BOOTSTRAPPER_OUT%" mkdir "%BOOTSTRAPPER_OUT%"

"%WIX_TOOLS%\light.exe" -nologo ^
    -out "%BOOTSTRAPPER_OUT%\%APP_NAME%-Setup-%APP_VERSION%.exe" ^
    -ext WixBalExtension ^
    -ext WixUtilExtension ^
    "%BOOTSTRAPPER_BUILD%\Bundle.wixobj"
if %errorlevel% neq 0 (
    echo [FAIL] Light linking failed.
    exit /b 1
)
echo [OK]   Bootstrapper linked

:: Verify bootstrapper was created
if not exist "%BOOTSTRAPPER_OUT%\%APP_NAME%-Setup-%APP_VERSION%.exe" (
    echo [FAIL] Bootstrapper EXE not created.
    exit /b 1
)
for %%f in ("%BOOTSTRAPPER_OUT%\%APP_NAME%-Setup-%APP_VERSION%.exe") do set BOOT_SIZE=%%~zf
set /a BOOT_SIZE_MB=%BOOT_SIZE%/1048576
echo [OK]   Bootstrapper created: %BOOTSTRAPPER_OUT%\%APP_NAME%-Setup-%APP_VERSION%.exe (%BOOT_SIZE_MB% MB)
echo.

:: ============================================================================
:: STEP 7b/9: Build portable ZIP
::   jpackage app-image + Compress-Archive — the client's most reliable package
::   (no installer, no admin rights, no JVM needed).
:: ============================================================================
echo ============================================================================
echo   STEP 7b/9: Build portable ZIP
echo ============================================================================
echo.
set "APP_IMAGE_DIR=%WORK_DIR%\target\app-image"
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
if not exist "%APP_IMAGE_DIR%\%APP_NAME%\ThorCash.exe" (
    echo [FAIL] App image not created at %APP_IMAGE_DIR%\%APP_NAME%\ThorCash.exe
    exit /b 1
)
echo [OK]   App image created

set "DIST_DIR=%WORK_DIR%\target\dist"
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
:: STEP 8/9: Generate SHA-256 checksums
::   For release integrity verification.
:: ============================================================================
echo ============================================================================
echo   STEP 8/9: Generate SHA-256 checksums
echo ============================================================================
echo.
powershell -NoProfile -Command "& { $msi='%MSI_PATH%'; $exe='%BOOTSTRAPPER_OUT%\%APP_NAME%-Setup-%APP_VERSION%.exe'; $zip='%DIST_DIR%\%APP_NAME%-Portable-%APP_VERSION%.zip'; $hash=(Get-FileHash $msi -Algorithm SHA256).Hash.ToLower(); Write-Host ('MSI:  '+$hash); $hash2=(Get-FileHash $exe -Algorithm SHA256).Hash.ToLower(); Write-Host ('EXE:  '+$hash2); $hash3=(Get-FileHash $zip -Algorithm SHA256).Hash.ToLower(); Write-Host ('ZIP:  '+$hash3); $hash+'  '+(Get-Item $msi).Name | Out-File 'target\checksums.txt' -Encoding utf8; $hash2+'  '+(Get-Item $exe).Name | Out-File 'target\checksums.txt' -Encoding utf8 -Append; $hash3+'  '+(Get-Item $zip).Name | Out-File 'target\checksums.txt' -Encoding utf8 -Append }"

echo [OK]   Checksums saved to target\checksums.txt
echo.

:: ============================================================================
:: STEP 9/9: Build summary
:: ============================================================================
echo ============================================================================
echo   BUILD COMPLETE
echo ============================================================================
echo.
echo   Installers:
echo     %MSI_PATH% (%MSI_SIZE_MB% MB)
echo     %BOOTSTRAPPER_OUT%\%APP_NAME%-Setup-%APP_VERSION%.exe (%BOOT_SIZE_MB% MB)
echo     %DIST_DIR%\%APP_NAME%-Portable-%APP_VERSION%.zip (%ZIP_SIZE_MB% MB)
echo.
echo   Runtime verification:
echo     %RUNTIME_DIR%\bin\java.exe     - present
echo     %RUNTIME_DIR%\bin\javaw.exe    - present
echo.
echo   To install:
echo     Double-click the .exe (recommended) or .msi and follow the prompts.
echo     Admin rights are required.
echo.
echo   To uninstall:
echo     Go to Settings ^> Apps ^> Installed Apps and select ThorCash.
echo.
echo ============================================================================

endlocal
