@echo off
prompt $
cls
title Bank Planning Assistant
setlocal enabledelayedexpansion

:menu
cls
color 0A
echo ========================================
echo   Bank Planning Assistant - Launcher
echo ========================================
echo.
echo   [1] Start all services (with consoles)
echo   [2] Start all services (hidden)
echo   [3] Stop all services
echo   [4] Install all dependencies
echo   [5] Exit
echo.
set /p choice="Select action: "

if "%choice%"=="1" goto start_debug
if "%choice%"=="2" goto start_silent
if "%choice%"=="3" goto stop_services
if "%choice%"=="4" goto install_deps
if "%choice%"=="5" goto exit_prompt
echo Invalid input.
timeout /t 1 /nobreak >nul
goto menu

:exit_prompt
cls
echo ========================================
echo   EXIT OPTIONS
echo ========================================
echo.
echo   [1] Stop all services and exit
echo   [2] Exit without stopping services
echo.
set /p exit_choice="Select option: "

if "%exit_choice%"=="1" goto stop_and_exit
if "%exit_choice%"=="2" goto exit_script
echo Invalid input.
timeout /t 1 /nobreak >nul
goto exit_prompt

:install_deps
cls
echo ========================================
echo   INSTALLING ALL DEPENDENCIES
echo ========================================
echo.
echo This will install:
echo   - Backend: Gradle will download dependencies on first build
echo   - Python: pip install -r requirements.txt
echo   - Frontend: npm install
echo.
echo Press any key to start installation...
pause >nul

echo.
echo [1/3] Installing Python dependencies...
cd python-service
pip install -r requirements.txt
if errorlevel 1 (
    echo ERROR: Python dependencies installation failed.
    echo Make sure Python is installed and pip is available.
    cd ..
    pause
    goto menu
)
cd ..
echo Python dependencies installed!

echo.
echo [2/3] Installing Frontend dependencies...
cd frontend
call npm install
if errorlevel 1 (
    echo ERROR: Frontend dependencies installation failed.
    echo Make sure Node.js and npm are installed.
    cd ..
    pause
    goto menu
)
cd ..
echo Frontend dependencies installed!

echo.
echo [3/3] Backend dependencies will be downloaded automatically on first build.
echo No manual installation required.
echo.

echo ========================================
echo   ALL DEPENDENCIES INSTALLED!
echo ========================================
echo.
echo Press any key to return to menu...
pause >nul
goto menu

:start_debug
cls
echo Starting with debug consoles...
set BACKEND_CMD=start "Backend" cmd /k "cd backend && gradlew bootRun"
set PYTHON_CMD=start "Python Service" cmd /k "cd python-service && python app.py"
set FRONTEND_CMD=start "Frontend" cmd /k "cd frontend && npm run dev"
goto start_services

:start_silent
cls
echo Starting silently...
set BACKEND_CMD=start /min "Backend" cmd /c "cd backend && gradlew bootRun"
set PYTHON_CMD=start /min "Python Service" cmd /c "cd python-service && python app.py"
set FRONTEND_CMD=start /min "Frontend" cmd /c "cd frontend && npm run dev"
goto start_services

:start_services
echo.
echo [1/4] Starting Backend...
%BACKEND_CMD%

echo Waiting for backend...
set attempts=0
:wait_backend
timeout /t 1 /nobreak >nul
netstat -ano | findstr :8080 >nul
if not errorlevel 1 goto backend_ready
set /a attempts+=1
if !attempts! geq 30 (
    echo ERROR: Backend failed to start.
    pause
    goto menu
)
goto wait_backend
:backend_ready
echo Backend ready!

echo.
echo [2/4] Starting Python...
%PYTHON_CMD%

echo Waiting for Python...
set attempts=0
:wait_python
timeout /t 1 /nobreak >nul
netstat -ano | findstr :5000 >nul
if not errorlevel 1 goto python_ready
set /a attempts+=1
if !attempts! geq 20 (
    echo ERROR: Python service failed to start.
    pause
    goto menu
)
goto wait_python
:python_ready
echo Python ready!

echo.
echo [3/4] Starting Frontend...
%FRONTEND_CMD%

echo Waiting for frontend...
set attempts=0
:wait_frontend
timeout /t 1 /nobreak >nul
netstat -ano | findstr :5173 >nul
if not errorlevel 1 goto frontend_ready
set /a attempts+=1
if !attempts! geq 20 (
    echo ERROR: Frontend failed to start.
    pause
    goto menu
)
goto wait_frontend
:frontend_ready
echo Frontend ready!

echo.
echo [4/4] Opening browser...
start http://localhost:5173

echo.
echo ========================================
echo   ALL SERVICES STARTED!
echo   Backend:  http://localhost:8080
echo   Python:   http://localhost:5000
echo   Frontend: http://localhost:5173
echo ========================================
echo.
echo Press any key to return to menu...
pause >nul
goto menu

:stop_services
cls
echo Stopping all services...

taskkill /F /IM java.exe 2>nul
taskkill /F /IM python.exe 2>nul
taskkill /F /IM node.exe 2>nul

echo All services stopped.
timeout /t 1 /nobreak >nul
pause
goto menu

:stop_and_exit
cls
echo Stopping all services...

taskkill /F /IM java.exe 2>nul
taskkill /F /IM python.exe 2>nul
taskkill /F /IM node.exe 2>nul

echo All services stopped.
timeout /t 1 /nobreak >nul
goto exit_script

:exit_script
cls
echo Goodbye!
exit