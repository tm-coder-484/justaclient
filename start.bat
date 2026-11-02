@echo off
setlocal enabledelayedexpansion
title Justaclient
cd /d "%~dp0"

echo.
echo   Justaclient - visuals for Minecraft (Fabric + Sodium)
echo   ----------------------------------------------------
echo.
echo   Which Minecraft version?
echo   [1] 1.21.11  ^(needs Java 21 or newer^)
echo   [2] 26.3     ^(needs Java 25 or newer^)
echo.
choice /c 12 /n /m "  Pick 1 or 2: "
if errorlevel 2 (
    set MC=26.3
    set NEED=25
    set PROJECT=-p mc26.3
    set LIBS=mc26.3\build\libs
) else (
    set MC=1.21.11
    set NEED=21
    set PROJECT=
    set LIBS=build\libs
)

rem Find the Java major version, e.g. "25.0.1" -> 25 (old "1.8.0" style reports 1, which is too old anyway).
set JMAJ=0
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JV=%%~v
if defined JV for /f "delims=." %%m in ("!JV!") do set JMAJ=%%m
if !JMAJ! LSS !NEED! (
    echo.
    echo   Minecraft !MC! needs Java !NEED! or newer, and this PC has: !JV!
    echo   Install Java !NEED! ^(Temurin^) from:
    echo   https://adoptium.net/temurin/releases/?version=!NEED!
    echo   then run this file again.
    echo.
    pause
    exit /b 1
)

echo.
echo   [1] Play  - launch Minecraft !MC! with Justaclient ^(dev client, offline name^)
echo   [2] Build - make the mod .jar for your own launcher
echo.
choice /c 12 /n /m "  Pick 1 or 2: "
if errorlevel 2 goto build

echo.
echo   Starting... the first launch downloads Minecraft and takes a few minutes.
call gradlew.bat !PROJECT! runClient
goto end

:build
echo.
call gradlew.bat !PROJECT! build
if errorlevel 1 goto end
echo.
echo   Done. Copy this into your .minecraft\mods folder:
dir /b !LIBS!\justaclient-*+!MC!.jar
echo     plus Fabric API and Sodium for !MC! from Modrinth.
explorer !LIBS!

:end
echo.
pause
