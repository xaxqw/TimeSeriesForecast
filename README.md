# 智能时序预测平台 (Time-Series Forecast Platform)

> 一个 **Spring Boot + 纯 Java 本地 ML** 的时序预测系统，零外部 ML 依赖、可离线运行、可单步调试。
> **零外部 ML 依赖**：ARIMA、Holt-Winters、优化器、评估指标全部手写实现，离线可跑、可单步调试、可讲清原理。

---

## 一、项目亮点

| 维度 | 体现 |
| --- | --- |
| **AI / 算法功底** | 从零实现 ARIMA(p,d,q) 条件最小二乘估计 + Nelder-Mead 无梯度优化；Holt-Winters 三次指数平滑 + 网格搜索自动调参；非文本类 ML（区别于 NLP 项目） |
| **Java 工程能力** | Spring Boot 3 分层架构（controller / service / api）、Maven、RESTful API、前后端分离控制台 |
| **工程素养** | 滚动回测(walk-forward)、MAPE/RMSE/MAE 评估、置信区间、模型自动选择、UTF-8 工程规范 |
| **可演示性** | 内置可视化 Web 控制台（Chart.js），载入示例即可出图，效果直观易懂 |

---

## 二、架构

```
浏览器 (static/index.html + Chart.js)
        │  HTTP / JSON
        ▼
ForecastController  (@RestController, /api/forecast, /api/backtest)
        │
        ▼
ForecastService    (模型选择 / 训练 / 预测 / 回测 / 置信区间)
        │
        ├── Arima          ARIMA(p,d,q)：差分 + 条件最小二乘 + Nelder-Mead
        ├── HoltWinters    三次指数平滑：加性/乘性 + 网格搜索调参
        ├── TimeSeries     差分/积分工具
        └── Metrics        MAE / RMSE / MAPE / SMAPE
```

## 三、目录结构

```
D:\TimeSeriesForecast\
├── pom.xml                      # Maven 构建（Spring Boot 3.2, Java 17）
├── run.bat                      # 一键启动（需 Maven）
├── README.md
├── sample-data\                 # 示例 CSV（月度销量 / 小时流量）
└── src\main\
    ├── java\com\aiforecast\tsp\
    │   ├── TimeSeriesForecastApplication.java   # 启动类
    │   ├── controller\ForecastController.java
    │   ├── service\ForecastService.java
    │   ├── api\                # 请求/响应 DTO
    │   └── ml\                 # 本地 ML 核心（纯 Java，可独立运行）
    │       ├── TimeSeries.java
    │       ├── Metrics.java
    │       ├── NelderMead.java
    │       ├── Arima.java
    │       ├── HoltWinters.java
    │       └── ForecastCli.java   # 免 Maven 命令行演示
    └── resources\
        ├── application.properties
        └── static\             # Web 控制台
            ├── index.html / app.js / style.css
            ├── vendor\chart.umd.js
            └── sample-data\
```

## 四、环境要求

- **JDK 17+**（Spring Boot 3 硬性要求）—— 唯一必须安装的依赖
- **Maven 可选**：项目已内置 **Maven Wrapper**（`mvnw` / `mvnw.cmd`），首次运行会自动下载 Maven 3.9.9，**无需本机单独安装 Maven**；当然也可以用本机已装的 Maven 3.8+
- 也可用 IDE（IntelliJ / Eclipse）直接运行 `TimeSeriesForecastApplication`

## 五、运行方式

### 方式 A：完整 Web 应用（推荐演示用）

**零安装（最省事）** —— 用内置 Maven Wrapper，自动下载 Maven，本机不用装 Maven：

```bash
cd D:\TimeSeriesForecast
.\mvnw.cmd spring-boot:run        # Windows
# 或 ./mvnw spring-boot:run       # macOS / Linux
# 浏览器打开 http://localhost:8080
```

**本机已有 Maven** —— 也可以直接用：

```bash
cd D:\TimeSeriesForecast
mvn spring-boot:run
```

**一键启动** —— 双击 `run.bat`（自动校验 JDK 版本，优先使用 `mvnw`）。

点击「载入示例·月度销量」→「运行预测」，即可看到预测曲线 + 置信区间 + 评估指标。
也可点「滚动回测」查看模型在不同预测步长下的误差衰减。

### 方式 B：快速验证 ML 核心（无需 Maven / Spring）

`ml` 包是纯 Java、零依赖，可直接用 `javac` + `java` 运行，验证算法正确性：

```bash
cd D:\TimeSeriesForecast
javac -encoding UTF-8 -d out src/main/java/com/aiforecast/tsp/ml/*.java
java  -cp out com.aiforecast.tsp.ml.ForecastCli
java  -cp out com.aiforecast.tsp.ml.ForecastCli sample-data/monthly_sales.csv
```

> 此方式已在 JDK 8 上验证通过：Holt-Winters 在带季节数据上 MAPE≈0.7%，ARIMA≈4.9%。

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

## 七、算法要点

1. **ARIMA(p,d,q)**
   - 对序列做 `d` 阶差分得到平稳序列 `z`；
   - 在 `z` 上用**条件最小二乘**估计 AR 系数 φ、MA 系数 θ 与均值 μ；
   - 目标函数最小化由 **Nelder-Mead 单纯形法**求解（无需解析梯度，对初值鲁棒）；
   - 对 AR 系数加稳定性惩罚，避免发散；递归外推后在差分尺度上逆向积分回原始尺度。
2. **Holt-Winters 三次指数平滑**
   - 同时建模**水平 / 趋势 / 季节**三个分量，支持加性与乘性季节；
   - `alpha/beta/gamma` 由**网格搜索**在训练集单步误差上自动挑选。
3. **模型自动选择**：用「各季节位置均值方差 / 总体方差」衡量季节强度，>0.3 用 Holt-Winters，否则 ARIMA。
4. **评估**：滚动回测(walk-forward) 计算 MAPE/RMSE/MAE；置信区间按回测残差 ±1.96σ 给出。

## 八、可扩展方向

- 接入 Prophet 式加法分解（趋势+季节+节假日）；
- 多变量 /  exogenous 回归（ARIMAX）；
- 用 Spring Cache 缓存模型、用 WebSocket 做实时流预测；
- 容器化（Docker）+ Actuator 监控，体现工程化。

---
*本工程 ML 核心已在 JDK 8 实测可编译可运行；Web 层为 Spring Boot 3 / Java 17。项目内置 Maven Wrapper（`mvnw` / `mvnw.cmd`），首次运行自动下载 Maven 3.9.9，**无需本机单独安装 Maven** 即可 `.\mvnw.cmd spring-boot:run`。*
