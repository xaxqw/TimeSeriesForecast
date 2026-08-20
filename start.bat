@echo off
chcp 65001 >nul
setlocal
title 智能时序预测平台 (Python 版)

set "VENV_PY=C:\Users\xuanx\.workbuddy\binaries\python\envs\default\Scripts\python.exe"
set "PROJECT=D:\TimeSeriesForecast"

cd /d "%PROJECT%"
if errorlevel 1 (
    echo [错误] 项目目录不存在: %PROJECT%
    pause & exit /b 1
)

echo ===============================================
echo   智能时序预测平台 (Python 版)
echo   端口: http://localhost:8000
echo ===============================================
echo.

if not exist "%VENV_PY%" (
    echo [错误] 未找到 Python venv: %VENV_PY%
    echo [提示] 请先执行:
    echo        C:\Users\xuanx\.workbuddy\binaries\python\versions\3.13.12\python.exe -m venv C:\Users\xuanx\.workbuddy\binaries\python\envs\default
    echo        "%VENV_PY%" -m pip install -r requirements.txt
    pause & exit /b 1
)

echo [检查] 依赖...
"%VENV_PY%" -c "import fastapi, uvicorn" 2>nul
if errorlevel 1 (
    echo [安装] 首次运行，正在安装依赖...
    "%VENV_PY%" -m pip install -r requirements.txt || (
        echo [错误] 依赖安装失败
        pause & exit /b 1
    )
)

echo [启动] 浏览器...
start "" http://localhost:8000

echo [启动] FastAPI 服务（关闭窗口即停止）...
echo.
"%VENV_PY%" -m uvicorn api:app --reload --host 0.0.0.0 --port 8000

echo.
echo [停止] 服务已退出
pause
