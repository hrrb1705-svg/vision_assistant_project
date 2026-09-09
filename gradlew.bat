@echo off
REM Self-contained Gradle wrapper bootstrap (no gradle-wrapper.jar required).
setlocal enabledelayedexpansion

set DIRNAME=%~dp0
set PROP_FILE=%DIRNAME%gradle\wrapper\gradle-wrapper.properties
set DISTRIBUTION_URL=https://services.gradle.org/distributions/gradle-8.7-bin.zip

if exist "%PROP_FILE%" (
    for /f "tokens=1,* delims==" %%a in ('type "%PROP_FILE%" ^| findstr /b "distributionUrl="') do (
        set DISTRIBUTION_URL=%%b
    )
)

set DIST_NAME=%DISTRIBUTION_URL:/= !%
for %%f in ("%DISTRIBUTION_URL%") do set DIST_NAME=%%~nxf
set DIST_ZIP_NAME=%DIST_NAME:.zip=%
set EXTRACT_DIR=%USERPROFILE%\.gradle\wrapper\dists\%DIST_ZIP_NAME%

where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
    gradle %*
    goto :eof
)

if exist "%EXTRACT_DIR%\%DIST_ZIP_NAME%\bin\gradle.bat" (
    "%EXTRACT_DIR%\%DIST_ZIP_NAME%\bin\gradle.bat" %*
    goto :eof
)

echo Downloading Gradle distribution: %DISTRIBUTION_URL%
if not exist "%EXTRACT_DIR%" mkdir "%EXTRACT_DIR%"
set ZIP_PATH=%EXTRACT_DIR%\%DIST_NAME%
curl -fSL --retry 3 -o "%ZIP_PATH%" "%DISTRIBUTION_URL%"
if not %ERRORLEVEL%==0 (
    echo ERROR: Failed to download Gradle distribution. >&2
    exit /b 1
)
powershell -NoProfile -Command "Expand-Archive -Force '%ZIP_PATH%' '%EXTRACT_DIR%'"
del "%ZIP_PATH%"

if exist "%EXTRACT_DIR%\%DIST_ZIP_NAME%\bin\gradle.bat" (
    "%EXTRACT_DIR%\%DIST_ZIP_NAME%\bin\gradle.bat" %*
) else (
    echo ERROR: Gradle was downloaded but not found. >&2
    exit /b 1
)
