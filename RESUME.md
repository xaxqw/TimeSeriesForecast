**智能时序预测平台**

- **技术栈**：Python 3.13 · FastAPI · 纯标准库手写 ML 核心 · Nelder-Mead · 网格搜索 · Pydantic · Chart.js
- **解决的问题**：预测强依赖重型 ML 框架难离线部署；选模型靠人工；缺可解释回测。
- **解决"依赖外部 ML 库、难离线部署"**：从零手写 ARIMA（条件最小二乘 + Nelder-Mead）与 Holt-Winters（网格搜索调参 α/β/γ），ML 核心仅依赖 Python 标准库、可离线、零安装、单步可调试；Web 层仅需 `pip install fastapi uvicorn`。
- **解决"选模型靠人工、效果无保障"**：滚动回测 + 4 指标 (MAE/RMSE/MAPE/SMAPE) + 置信区间 (残差 ±1.96σ) + 自动选模型（季节强度 >0.3 切 Holt-Winters）；20 个单元测试覆盖差分逆积分往返 / 优化器收敛 / 季节检测 / 回测；月度销量 (n=48, 训练 36/测试 12, Python 3.13 实测) HW MAPE=11.2%, RMSE=37.6 vs ARIMA 19.5%, RMSE=73.7；小时流量 (n=168) HW 28.5% vs ARIMA 40.0%。
- **Github 链接**：https://github.com/xaxqw/TimeSeriesForecast
