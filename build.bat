@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle not found. Install JDK 21 and Gradle 8.x, then run this file again.
  pause
  exit /b 1
)
gradle clean build
pause
