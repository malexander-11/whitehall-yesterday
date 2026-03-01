@rem Gradle wrapper script for Windows.
@if "%DEBUG%"=="" @echo off
@rem Set local scope for variables with Windows NT shell
if "%OS%"=="Windows_NT" setlocal

set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if not exist "%CLASSPATH%" (
    echo ERROR: Gradle wrapper JAR not found at %CLASSPATH%
    echo Bootstrap it with: gradle wrapper --gradle-version 8.11.1
    echo Or build via Docker: docker-compose up --build
    exit /b 1
)

if defined JAVA_HOME (
    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
    set JAVA_EXE=java.exe
)

%JAVA_EXE% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
if "%OS%"=="Windows_NT" endlocal
