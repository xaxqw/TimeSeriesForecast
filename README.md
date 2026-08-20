# 智能时序预测平台 (Time-Series Forecast Platform)

> 一个 **FastAPI + 纯 Python 本地 ML** 的时序预测系统，**零外部 ML 依赖、可离线运行、可单步调试**。
> ARIMA、Holt-Winters、无梯度优化器、评估指标全部手写实现（仅 Python 标准库），面试现场可逐行讲原理。

---

## 一、项目亮点

| 维度 | 体现 |
| --- | --- |
| **AI / 算法功底** | 从零实现 ARIMA(p,d,q) 条件最小二乘估计 + Nelder-Mead 无梯度优化；Holt-Winters 三次指数平滑 + 网格搜索自动调参；非文本类 ML（区别于 NLP 项目） |
| **Python 工程能力** | FastAPI 分层（api / service / core）、Pydantic 请求校验、unittest 测试、RESTful API、前后端分离控制台 |
| **工程素养** | 滚动回测(walk-forward)、MAPE/RMSE/MAE 评估、置信区间、模型自动选择、UTF-8 工程规范 |
| **可演示性** | 内置可视化 Web 控制台（Chart.js），载入示例即可出图，效果直观易懂 |

---

## 二、架构

```
浏览器 (index.html + Chart.js)
        │  HTTP / JSON
        ▼
api.py        FastAPI：/api/forecast /api/backtest /api/health
        │
        ▼
service.py    ForecastService（模型选择 / 训练 / 预测 / 回测 / 置信区间）
        │
        ├── forecast_core.Arima       ARIMA(p,d,q)：差分 + 条件最小二乘 + Nelder-Mead
        ├── forecast_core.HoltWinters 三次指数平滑：加性/乘性 + 网格搜索调参
        ├── forecast_core 差分/积分工具
        └── forecast_core 指标        MAE / RMSE / MAPE / SMAPE
```

## 三、目录结构

```
D:\TimeSeriesForecast\
├── forecast_core.py     # ML 核心：差分/逆积分、指标、Nelder-Mead、ARIMA、Holt-Winters、季节检测（仅标准库）
├── service.py           # ForecastService：auto 模型选择 / 训练 / 预测 / 回测 / 置信区间
├── api.py               # FastAPI：REST API + Web 控制台（Pydantic 校验）
├── cli.py               # 命令行演示（无 Web 框架也能跑）
├── test_core.py         # 20 个单元测试（unittest，零第三方依赖）
├── requirements.txt     # fastapi + uvicorn（仅 Web 层需要）
├── index.html           # Web 控制台（Chart.js 可视化）
├── app.js / style.css
├── vendor\chart.umd.js  # Chart.js 本地库（离线可用）
├── sample-data\         # 示例 CSV（月度销量 / 小时流量）
└── README.md
```

## 四、环境要求

- **Python 3.10+**（唯一必须安装的运行时）
- **Web 层**可选依赖：`pip install -r requirements.txt`（fastapi / uvicorn）
- **CLI / 测试 / ML 核心**零依赖，标准库即可运行

## 五、运行方式

### 方式 A：快速验证 ML 核心（无需安装任何依赖）

```bash
python cli.py                          # 内置合成数据（趋势+季节+噪声）
python cli.py sample-data/monthly_sales.csv   # 读取 CSV
```

### 方式 B：单元测试（零第三方依赖）

```bash
python test_core.py
```

### 方式 C：完整 Web 应用（推荐演示用）

```bash
pip install -r requirements.txt
uvicorn api:app --reload --port 8000
# 浏览器打开 http://localhost:8000
```

页面操作：点击「载入示例·月度销量」→「运行预测」，查看预测曲线 + 置信区间 + 评估指标；点「滚动回测」查看模型在不同预测步长下的误差衰减。

## 六、API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/forecast` | 提交时序 + 模型参数，返回预测值、置信区间、评估指标、拟合参数 |
| POST | `/api/backtest` | 滚动回测，返回各预测步长的 MAPE/RMSE/MAE |
| GET | `/api/health` | 健康检查 |

请求示例：
```json
{
  "values": [100.0, 102.3, 99.8, 105.1, 110.4],
  "model": "auto",
  "horizon": 12,
  "m": 12,
  "seasonal": "additive"
}
```

响应示例：
```json
{
  "history": [...],
  "forecast": [...],
  "lower": [...],
  "upper": [...],
  "model": "arima",
  "params": {"p": 2, "d": 1, "q": 1, "phi": [...], "theta": [...], "mu": 1.6, "sigma": 2.78},
  "mape": 20.7, "rmse": 55.7, "mae": 51.2,
  "note": "回测窗口 1 个；置信区间按回测残差 ±1.96σ 给出"
}
```

## 七、算法要点

1. **ARIMA(p,d,q)**
   - 对序列做 `d` 阶差分得到平稳序列 `z`；
   - 在 `z` 上用**条件最小二乘**估计 AR 系数 φ、MA 系数 θ 与均值 μ；
   - 目标函数由 **Nelder-Mead 下山单纯形法**最小化（无需解析梯度，对初值鲁棒）；
   - 对 AR 系数加稳定性惩罚避免发散；递归外推后在差分尺度上逆向积分回原始尺度。
2. **Holt-Winters 三次指数平滑**
   - 同时建模**水平 / 趋势 / 季节**三个分量，支持加性与乘性季节；
   - `alpha/beta/gamma` 由**网格搜索**在训练集单步误差上自动挑选。
3. **模型自动选择**：用「各季节位置均值方差 / 总体方差」衡量季节强度，>0.3 用 Holt-Winters，否则 ARIMA。
4. **评估**：滚动回测(walk-forward) 计算 MAPE/RMSE/MAE；置信区间按回测残差 ±1.96σ 给出。

## 八、验证结果

合成数据 120 点（趋势 + 季节 + 噪声）滚动外推：

| 模型 | MAPE | RMSE | MAE |
| --- | --- | --- | --- |
| ARIMA(2,1,1) | 3.89% | 6.71 | 6.09 |
| Holt-Winters(加性, m=12) | 2.82% | 4.93 | 4.37 |

单元测试 20 项全部通过，覆盖：差分/逆积分往返、四项指标、Nelder-Mead 收敛、ARIMA 线性外推、Holt-Winters 加性/乘性、自动模型选择（季节强度→Holt-Winters / 趋势→ARIMA）、回测、边界条件。

## 九、可扩展方向

- 接入 Prophet 式加法分解（趋势+季节+节假日）；
- 多变量 / exogenous 回归（ARIMAX）；
- 用 FastAPI 缓存 + WebSocket 做实时流预测；
- 容器化（Docker）+ 接口文档（FastAPI 自带 Swagger：`/docs`），体现工程化。

---
*本工程 ML 核心仅使用 Python 标准库，离线可跑、可单步调试；Web 层为 FastAPI。*
