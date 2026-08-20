"""FastAPI 服务：把纯 Python 时序预测核心暴露为 REST API + 可视化控制台。

运行：
    pip install -r requirements.txt
    uvicorn api:app --reload --port 8000
    # 浏览器打开 http://localhost:8000
"""

from __future__ import annotations

import os
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from service import ForecastService

app = FastAPI(title="智能时序预测平台 (Python)", version="1.0.0")
_service = ForecastService()
_BASE = os.path.dirname(os.path.abspath(__file__))


class ForecastRequest(BaseModel):
    values: List[float]
    model: str = "auto"           # auto | arima | holt-winters
    p: Optional[int] = None
    d: Optional[int] = None
    q: Optional[int] = None
    m: Optional[int] = None      # 季节周期
    seasonal: str = "additive"   # additive | multiplicative
    horizon: int = 12


class BacktestRequest(BaseModel):
    values: List[float]
    model: str = "auto"
    p: Optional[int] = None
    d: Optional[int] = None
    q: Optional[int] = None
    m: Optional[int] = None
    seasonal: str = "additive"
    horizon: int = 12
    step: int = 1


@app.get("/api/health")
def health():
    return {"status": "ok"}


@app.post("/api/forecast")
def forecast(req: ForecastRequest):
    try:
        return _service.forecast(
            req.values, req.model, req.p, req.d, req.q, req.m, req.seasonal, req.horizon
        )
    except Exception as e:  # noqa: BLE001
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/api/backtest")
def backtest(req: BacktestRequest):
    try:
        return _service.backtest(
            req.values, req.model, req.p, req.d, req.q, req.m, req.seasonal, req.horizon, req.step
        )
    except Exception as e:  # noqa: BLE001
        raise HTTPException(status_code=400, detail=str(e))


# 静态资源（仅暴露前端所需文件，不暴露 .py 源码）
@app.get("/")
def index():
    return FileResponse(os.path.join(_BASE, "index.html"))


@app.get("/style.css")
def style_css():
    return FileResponse(os.path.join(_BASE, "style.css"))


@app.get("/app.js")
def app_js():
    return FileResponse(os.path.join(_BASE, "app.js"))


app.mount("/vendor", StaticFiles(directory=os.path.join(_BASE, "vendor")), name="vendor")
app.mount("/sample-data", StaticFiles(directory=os.path.join(_BASE, "sample-data")), name="sample-data")


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
