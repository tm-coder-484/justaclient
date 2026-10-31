@echo off
setlocal
title Justaclient
cd /d "%~dp0"

echo.
echo   Justaclient - Minecraft 1.21.11 (Fabric + Sodium)
echo   ------------------------------------------------
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo   Java was not found. Install Java 21 ^(Temurin^) from:
    echo   https://adoptium.net/temurin/releases/?version=21
    echo   then run this file again.
    echo.
    pause
    exit /b 1
)

echo   [1] Play  - launch Minecraft with Justaclient ^(dev client, offline name^)
echo   [2] Build - make the mod .jar for your own launcher
echo.
choice /c 12 /n /m "  Pick 1 or 2: "
if errorlevel 2 goto build

echo.
echo   Starting... the first launch downloads Minecraft and takes a few minutes.
call gradlew.bat runClient
goto end

:build
echo.
call gradlew.bat build
if errorlevel 1 goto end
echo.
echo   Done. Copy these into your .minecraft\mods folder:
dir /b build\libs\justaclient-*.jar
echo     plus Fabric API and Sodium for 1.21.11 from Modrinth.
explorer build\libs

:end
echo.
pause
