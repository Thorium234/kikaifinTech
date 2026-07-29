@echo off
setlocal ENABLEDELAYEDEXPANSION

echo ============================================================================
echo   ThorCash - Professional Bootstrapper Build (WiX v3.14)
echo ============================================================================
echo.

:: Change to project root (3 levels up from this script's dir)
pushd "%~dp0..\..\.." || exit /b 1

:: WiX toolset
set WIX_TOOLS=C:\Program Files (x86)\WiX Toolset v3.14\bin
set PATH=%PATH%;%WIX_TOOLS%

:: Project paths (relative to project root)
set WIX_DIR=src\main\installer\wix
set MSI_DIR=target\installer
set OUTPUT_DIR=target\bootstrapper-output
set BUILD_DIR=target\bootstrapper
set ICON_PATH=src\main\resources\icon.ico

:: Find MSI
set MSI_PATH=%MSI_DIR%\ThorCash-1.0.0.msi
if not exist "%MSI_PATH%" (
    echo Searching for MSI in %CD%\%MSI_DIR%...
    for %%f in ("%MSI_DIR%\*.msi") do set MSI_PATH=%%f
)
if not exist "!MSI_PATH!" (
    echo ERROR: No MSI found in %CD%\%MSI_DIR%.
    popd
    exit /b 1
)
echo MSI: !MSI_PATH!

:: Parse version
set APP_VERSION=1.0.0
for /f "tokens=2 delims=-" %%a in ("!MSI_PATH!") do set APP_VERSION=%%~na
echo Version: !APP_VERSION!
echo.

:: Clean
if exist "!BUILD_DIR!" rmdir /s /q "!BUILD_DIR!"
mkdir "!BUILD_DIR!"

:: Step 1: Compile
echo [1/2] Compiling Bundle.wxs...
"%WIX_TOOLS%\candle.exe" -nologo -out "!BUILD_DIR!\Bundle.wixobj" -ext WixBalExtension -ext WixUtilExtension -dProjectDir="%CD%\!WIX_DIR!" -dMsiPath="%CD%\!MSI_PATH!" -dVersion="!APP_VERSION!" -dIconPath="%CD%\!ICON_PATH!" "!WIX_DIR!\Bundle.wxs"
if errorlevel 1 (
    echo ERROR: Candle failed.
    popd
    exit /b 1
)

:: Step 2: Link
echo [2/2] Linking ThorCash-Setup-!APP_VERSION!.exe...
"%WIX_TOOLS%\light.exe" -nologo -out "!BUILD_DIR!\ThorCash-Setup-!APP_VERSION!.exe" -ext WixBalExtension -ext WixUtilExtension "!BUILD_DIR!\Bundle.wixobj"
if errorlevel 1 (
    echo ERROR: Light failed.
    popd
    exit /b 1
)

:: Copy to output
if not exist "!OUTPUT_DIR!" mkdir "!OUTPUT_DIR!"
copy /y "!BUILD_DIR!\ThorCash-Setup-!APP_VERSION!.exe" "!OUTPUT_DIR!\" >nul

:: Report
for %%f in ("!OUTPUT_DIR!\ThorCash-Setup-!APP_VERSION!.exe") do set SIZE=%%~zf
set /a SIZE_MB=!SIZE!/1048576
echo.
echo ============================================================================
echo   SUCCESS: ThorCash-Setup-!APP_VERSION!.exe (!SIZE_MB! MB)
echo   Location: %CD%\!OUTPUT_DIR!
echo ============================================================================

popd
endlocal
