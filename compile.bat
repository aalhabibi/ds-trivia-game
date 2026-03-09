@echo off
echo ========================================
echo   Compiling Trivia Game...
echo ========================================
echo.

if not exist bin mkdir bin

javac -d bin src/model/*.java src/server/*.java src/client/*.java

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Compilation successful!
    echo.
    echo To run the server:  run_server.bat
    echo To run a client:    run_client.bat
) else (
    echo.
    echo Compilation failed!
)
pause
