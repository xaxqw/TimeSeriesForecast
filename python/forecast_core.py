"""时序预测核心算法（纯标准库实现，零外部 ML 依赖）。

对应原 Java 工程 `src/main/java/com/aiforecast/tsp/ml/` 包：
TimeSeries / Metrics / NelderMead / Arima / HoltWinters。

设计目标：
- 不依赖 numpy / scipy / sklearn，全部手写，可离线运行、可单步调试、可讲清原理；
- ARIMA(p,d,q)：条件最小二乘估计 + Nelder-Mead 无梯度优化；
- Holt-Winters 三次指数平滑：加性/乘性季节 + 网格搜索自动调参；
- 与 Java 版数值逻辑保持一致，便于横向对比与面试复现。
"""

from __future__ import annotations

import math
from typing import Callable, List, Optional, Tuple


# ---------------------------------------------------------------------------
# 时序工具：差分 / 逆向积分
# ---------------------------------------------------------------------------
def difference(values: List[float], d: int) -> List[float]:
    """对序列做 d 阶差分，返回长度 = len - d 的新序列。"""
    if d < 0:
        raise ValueError("差分阶数 d 必须 >= 0")
    cur = list(values)
    for _ in range(d):
        cur = [cur[i + 1] - cur[i] for i in range(len(cur) - 1)]
    return cur


def invert_difference(d_forecast: List[float], original: List[float], d: int) -> List[float]:
    """将 d 阶差分尺度上的「未来增量」逆向积分回原始尺度。

    d_forecast: 长度为 h，d 阶差分尺度上的未来增量。
    original:   原始序列，用于取积分起点。
    返回:       长度为 h 的原始尺度预测值。
    """
    h = len(d_forecast)
    last_val = [0.0] * (d + 1)
    last_val[ 0] = original[-1]
    prev = list(original)
    for o in range(1, d + 1):
        cur = [prev[i + 1] - prev[i] for i in range(len(prev) - 1)]
        last_val[o] = cur[-1]
        prev = cur
    result = []
    for k in range(h):
        v = last_val[d] + d_forecast[k]
        last_val[d] = v
        for o in range(d - 1, 0, -1):
            nv = last_val[o] + v
            v = nv
            last_val[o] = v
        orig = last_val[0] + v
        last_val[0] = orig
        result.append(orig)
    return result


def _mean(a: List[float]) -> float:
    return sum(a) / len(a) if a else 0.0


def _var(a: List[float]) -> float:
    m = _mean(a)
    return sum((x - m) ** 2 for x in a) / len(a)


# ---------------------------------------------------------------------------
# 误差指标：MAE / RMSE / MAPE / SMAPE
# ---------------------------------------------------------------------------
def mae(actual: List[float], pred: List[float]) -> float:
    if len(actual) != len(pred):
        raise ValueError("序列长度必须一致")
    return sum(abs(a - b) for a, b in zip(actual, pred)) / len(actual)


def rmse(actual: List[float], pred: List[float]) -> float:
    if len(actual) != len(pred):
        raise ValueError("序列长度必须一致")
    return math.sqrt(sum((a - b) ** 2 for a, b in zip(actual, pred)) / len(actual))


def mape(actual: List[float], pred: List[float]) -> float:
    if len(actual) != len(pred):
        raise ValueError("序列长度必须一致")
    s, n = 0.0, 0
    for a, b in zip(actual, pred):
        if abs(a) > 1e-9:
            s += abs(a - b) / abs(a)
            n += 1
    return (s / n * 100.0) if n else 0.0


def smape(actual: List[float], pred: List[float]) -> float:
    if len(actual) != len(pred):
        raise ValueError("序列长度必须一致")
    s = 0.0
    for a, b in zip(actual, pred):
        denom = abs(a) + abs(b)
        if denom > 1e-9:
            s += abs(a - b) / (denom / 2.0)
    return s / len(actual) * 100.0


# ---------------------------------------------------------------------------
# Nelder-Mead（下山单纯形）无梯度优化器
# ---------------------------------------------------------------------------
def nelder_mead_minimize(
    obj: Callable[[List[float]], float],
    x0: List[float],
    simplex_size: float = 0.5,
    max_iter: int = 8000,
    tol: float = 1e-7,
) -> List[float]:
    n = len(x0)
    simplex = [list(x0) for _ in range(n + 1)]
    for i in range(1, n + 1):
        simplex[i][i - 1] += simplex_size
    fvals = [obj(p) for p in simplex]
    alpha, gamma, rho, sigma = 1.0, 2.0, 0.5, 0.5

    for _ in range(max_iter):
        idx = sorted(range(n + 1), key=lambda i: fvals[i])
        if abs(fvals[idx[n]] - fvals[idx[0]]) < tol:
            break

        centroid = [0.0] * n
        for i in range(n):
            for j in range(n):
                centroid[j] += simplex[idx[i]][j]
        for j in range(n):
            centroid[j] /= n

        xr = [centroid[j] + alpha * (centroid[j] - simplex[idx[n]][j]) for j in range(n)]
        fr = obj(xr)

        if fr < fvals[idx[0]]:
            xe = [centroid[j] + gamma * (xr[j] - centroid[j]) for j in range(n)]
            fe = obj(xe)
            if fe < fr:
                simplex[idx[n]], fvals[idx[n]] = list(xe), fe
            else:
                simplex[idx[n]], fvals[idx[n]] = list(xr), fr
        elif fr < fvals[idx[n - 1]]:
            simplex[idx[n]], fvals[idx[n]] = list(xr), fr
        else:
            xc = [centroid[j] + rho * (simplex[idx[n]][j] - centroid[j]) for j in range(n)]
            fc = obj(xc)
            if fc < fvals[idx[n]]:
                simplex[idx[n]], fvals[idx[n]] = list(xc), fc
            else:
                for i in range(1, n + 1):
                    for j in range(n):
                        simplex[idx[i]][j] = sigma * (simplex[idx[i]][j] + simplex[idx[0]][j])
                    fvals[idx[i]] = obj(simplex[idx[i]])

    best = 0
    for i in range(1, n + 1):
        if fvals[i] < fvals[best]:
            best = i
    return list(simplex[best])


# ---------------------------------------------------------------------------
# ARIMA(p, d, q)
# ---------------------------------------------------------------------------
class Arima:
    def __init__(self, y: List[float], p: int, d: int, q: int):
        if not y:
            raise ValueError("序列不能为空")
        if p < 0 or d < 0 or q < 0:
            raise ValueError("p/d/q 必须 >= 0")
        self.y = list(y)
        self.p, self.d, self.q = p, d, q
        self.phi: List[float] = []
        self.theta: List[float] = []
        self.mu: float = 0.0
        self.sigma: float = 0.0
        self.residuals: Optional[List[float]] = None
        self._fitted = False

    def fit(self) -> None:
        z = difference(self.y, self.d)
        n = len(z)
        max_lag = max(self.p, self.q)
        if n <= max_lag + 2:
            raise ValueError(f"样本量不足以拟合 ARIMA({self.p},{self.d},{self.q})")
        dim = self.p + self.q + 1
        x0 = [0.1] * self.p + [0.0] * self.q + [_mean(z)]
        best = nelder_mead_minimize(lambda params: self._arma_sse(z, params), x0, 0.5, 8000, 1e-7)
        self.phi = best[: self.p]
        self.theta = best[self.p : self.p + self.q]
        self.mu = best[self.p + self.q]
        res = self._arma_residuals_full(z, self.phi, self.theta, self.mu, self.p, self.q)
        sse = sum(r * r for r in res)
        self.sigma = math.sqrt(sse / max(1, len(res) - dim))
        self.residuals = res
        self._fitted = True

    def forecast(self, h: int) -> List[float]:
        if not self._fitted:
            raise RuntimeError("请先调用 fit()")
        z = difference(self.y, self.d)
        n = len(z)
        a_full = self._arma_residuals_full(z, self.phi, self.theta, self.mu, self.p, self.q)
        z_ext = list(z) + [0.0] * h
        a_ext = list(a_full) + [0.0] * h
        if self.d == 0:
            out = []
            for t in range(n, n + h):
                pred = self.mu + sum(self.phi[i - 1] * (z_ext[t - i] - self.mu) for i in range(1, self.p + 1))
                ma = sum(self.theta[j - 1] * a_ext[t - j] for j in range(1, self.q + 1))
                z_ext[t] = pred + ma
                a_ext[t] = 0.0
                out.append(z_ext[t])
            return out
        d_forecast = []
        for t in range(n, n + h):
            pred = self.mu + sum(self.phi[i - 1] * (z_ext[t - i] - self.mu) for i in range(1, self.p + 1))
            ma = sum(self.theta[j - 1] * a_ext[t - j] for j in range(1, self.q + 1))
            zhat = pred + ma
            d_forecast.append(zhat - z_ext[t - 1])
            z_ext[t] = zhat
            a_ext[t] = 0.0
        return invert_difference(d_forecast, self.y, self.d)

    def _arma_residuals_full(self, z, phi, theta, mu, p, q):
        n = len(z)
        max_lag = max(p, q)
        a = [0.0] * n
        for t in range(n):
            if t < max_lag:
                a[t] = 0.0
                continue
            pred = mu + sum(phi[i - 1] * (z[t - i] - mu) for i in range(1, p + 1))
            ma = 0.0
            for j in range(1, q + 1):
                idx = t - j
                aval = a[idx] if (idx >= 0 and idx >= max_lag) else 0.0
                ma += theta[j - 1] * aval
            a[t] = z[t] - pred - ma
        return a

    def _arma_sse(self, z, params):
        res = self._arma_residuals_full(
            z, params[: self.p], params[self.p : self.p + self.q], params[self.p + self.q], self.p, self.q
        )
        sse = sum(r * r for r in res)
        pen = 0.0
        for phi in params[: self.p]:
            if abs(phi) > 1.0:
                pen += (abs(phi) - 1.0) * 1000.0
        return sse + pen


# ---------------------------------------------------------------------------
# Holt-Winters 三次指数平滑（加性 / 乘性季节）
# ---------------------------------------------------------------------------
class HoltWinters:
    ADDITIVE = "additive"
    MULTIPLICATIVE = "multiplicative"

    def __init__(self, y: List[float], m: int, typ: str = ADDITIVE):
        if len(y) < 2 * m:
            raise ValueError(f"Holt-Winters 需要样本量 >= 2 倍季节周期({2 * m})")
        if m < 2:
            raise ValueError("季节周期 m 必须 >= 2")
        if typ == self.MULTIPLICATIVE and any(v <= 0.0 for v in y):
            raise ValueError("乘性 Holt-Winters 要求序列严格为正，请改用加性或先做对数变换")
        self.y = list(y)
        self.m = m
        self.type = typ
        self.alpha = self.beta = self.gamma = 0.0
        self.level = self.trend = 0.0
        self.seasonals: List[float] = []
        self.fitted: List[float] = []
        self._fitted = False

    def fit(self) -> None:
        grid = [0.1, 0.3, 0.5, 0.7, 0.9]
        best = [0.3, 0.1, 0.3]
        best_sse = math.inf
        for a in grid:
            for b in grid:
                for g in grid:
                    sse = self._train_sse(a, b, g)
                    if sse < best_sse:
                        best_sse, best = sse, [a, b, g]
        self.alpha, self.beta, self.gamma = best
        self._train_state(self.alpha, self.beta, self.gamma)
        self._fitted = True

    def _train_state(self, a, b, g):
        y, m, n = self.y, self.m, len(self.y)
        season = [0.0] * m
        l0 = sum(y[:m]) / m
        b0 = (sum(y[m : 2 * m]) / m - l0) / m if n >= 2 * m else 0.0
        level, trend = l0, b0
        if self.type == self.ADDITIVE:
            for i in range(m):
                season[i] = y[i] - l0
        else:
            for i in range(m):
                season[i] = y[i] / l0
        fitted = [0.0] * n
        for t in range(m, n):
            idx = t % m
            if self.type == self.ADDITIVE:
                s = season[idx]
                fitted[t] = level + trend + s
                level = a * (y[t] - s) + (1 - a) * (level + trend)
                trend = b * (level - level) + (1 - b) * trend
                season[idx] = g * (y[t] - level) + (1 - g) * s
            else:
                s = season[idx]
                fitted[t] = (level + trend) * s
                level = a * (y[t] / s) + (1 - a) * (level + trend)
                trend = b * (level - level) + (1 - b) * trend
                season[idx] = g * (y[t] / level) + (1 - g) * s
        self.level, self.trend, self.seasonals, self.fitted = level, trend, season, fitted

    def _train_sse(self, a, b, g):
        self._train_state(a, b, 0.0) if False else self._train_state(a, b, g)
        sse, cnt = 0.0, 0
        for t in range(self.m, len(self.y)):
            e = self.y[t] - self.fitted[t]
            sse += e * e
            cnt += 1
        return sse / cnt if cnt else math.inf

    def forecast(self, h: int) -> List[float]:
        if not self._fitted:
            raise RuntimeError("请先调用 fit()")
        n = len(self.y)
        out = []
        for k in range(1, h + 1):
            idx = (n - 1 + k) % self.m
            if self.type == self.ADDITIVE:
                out.append(self.level + k * self.trend + self.seasonals[idx])
            else:
                out.append((self.level + k * self.trend) * self.seasonals[idx])
        return out


# ---------------------------------------------------------------------------
# 模型选择工具
# ---------------------------------------------------------------------------
def seasonal_strength(v: List[float], m: int) -> float:
    """季节强度：各季节位置均值方差 / 总体方差。"""
    if len(v) < 2 * m:
        return 0.0
    season_mean = [0.0] * m
    cnt = [0] * m
    for i, x in enumerate(v):
        season_mean[i % m] += x
        cnt[i % m] += 1
    season_mean = [s / max(1, c) for s, c in zip(season_mean, cnt)]
    return _var(season_mean) / max(1e-9, _var(v))


def acf(v: List[float], lag: int) -> float:
    if lag <= 0 or lag >= len(v):
        return 0.0
    m = _mean(v)
    den = sum((x - m) ** 2 for x in v)
    if den == 0.0:
        return 0.0
    num = sum((v[i] - m) * (v[i - lag] - m) for i in range(lag, len(v)))
    return num / den


def detect_period(v: List[float]) -> int:
    """在常见周期 {24,12,7,4} 中用 ACF 挑选最显著的季节周期。"""
    best = 12
    best_acf = acf(v, 12)
    for cand in (24, 7, 4):
        if len(v) >= 2 * cand:
            a = acf(v, cand)
            if a > best_acf + 0.05:
                best_acf, best = a, cand
    return best


__all__ = [
    "difference", "invert_difference", "mae", "rmse", "mape", "smape",
    "nelder_mead_minimize", "Arima", "HoltWinters", "seasonal_strength",
    "acf", "detect_period",
]
