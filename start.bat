@echo off
chcp 65001 >nul
cd /d D:\TimeSeriesForecast

echo ===============================================
echo   智能时序预测平台 (Python 版)
echo   项目目录: %CD%
echo   服务地址: http://localhost:8000
echo   关闭此窗口即可停止服务
echo ===============================================
echo.

set "VENV_PY=C:\Users\xuanx\.workbuddy\binaries\python\envs\default\Scripts\python.exe"

if not exist "%VENV_PY%" (
    echo [错误] venv python 不存在: %VENV_PY%
    echo [提示] 请先执行:
    echo        C:\Users\xuanx\.workbuddy\binaries\python\versions\3.13.12\python.exe -m venv C:\Users\xuanx\.workbuddy\binaries\python\envs\default
    echo        "%VENV_PY%" -m pip install -r requirements.txt
    goto END
)

echo [1/3] 检查依赖...
"%VENV_PY%" -c "import fastapi, uvicorn" 2>nul
if errorlevel 1 (
    echo [2/3] 首次运行，正在安装依赖（可能需要 30 秒）...
    "%VENV_PY%" -m pip install -r requirements.txt
    if errorlevel 1 goto ERR_PIP
) else (
    echo [2/3] 依赖已就绪
)

echo [3/3] 启动 FastAPI 服务...
echo       浏览器请手动打开: http://localhost:8000
echo.
start "" "http://localhost:8000"
"%VENV_PY%" -m uvicorn api:app --reload --host 0.0.0.0 --port 8000
goto END

:ERR_PIP
echo [错误] 依赖安装失败，请检查网络或手动执行 pip install -r requirements.txt
goto END

:END
echo.
echo 服务已退出
pause
