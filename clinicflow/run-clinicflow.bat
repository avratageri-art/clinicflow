@echo off
setlocal EnableDelayedExpansion
title ClinicFlow - Smart Clinic Appointment and Queue Management System

cd /d "%~dp0"

echo ===============================================================================
echo    CLINICFLOW: ML-Based Patient No-Show Prediction and Smart Queue System
echo ===============================================================================
echo.

:: 1. Check Java Availability (PATH, JAVA_HOME, or bundled ..\tools)
if not "%JAVA_HOME%"=="" (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
    where java >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        set "JAVA_EXE=java"
    ) else if exist "..\tools\jdk-17.0.12+7\bin\java.exe" (
        set "JAVA_HOME=%CD%\..\tools\jdk-17.0.12+7"
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    ) else (
        set "JAVA_EXE=java"
    )
)

"%JAVA_EXE%" -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java 17 was not found in PATH, JAVA_HOME, or ..\tools.
    echo Please install Java 17 LTS or set JAVA_HOME to a valid JDK directory.
    pause
    exit /b 1
)

echo [OK] Java runtime detected successfully.
echo.

:: 2. Locate Packaged JAR or Run via Maven
set "JAR_PATH=target\clinicflow-1.0.0-SNAPSHOT.jar"

if exist "%JAR_PATH%" (
    echo [INFO] Found packaged application: %JAR_PATH%
    echo [INFO] Launching ClinicFlow Desktop Application...
    echo.
    "%JAVA_EXE%" -jar "%JAR_PATH%" %*
) else (
    set "MVN_EXE=mvn"
    where mvn >nul 2>&1
    if %ERRORLEVEL% neq 0 (
        if exist "..\tools\apache-maven-3.9.6\bin\mvn.cmd" (
            set "MVN_EXE=..\tools\apache-maven-3.9.6\bin\mvn.cmd"
        )
    )
    echo [INFO] Application JAR not found. Launching via Maven exec:java...
    echo.
    call "%MVN_EXE%" exec:java -Dexec.args="%*"
)

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] ClinicFlow terminated with an error code: %ERRORLEVEL%
    pause
    exit /b %ERRORLEVEL%
)

endlocal
