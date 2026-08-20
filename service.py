"""预测编排服务：模型选择、训练、预测、回测评估、置信区间。

对标 Java 工程 `ForecastService`，仅依赖标准库 + 本模块的 forecast_core。
产出可被 FastAPI / CLI / 任意 Python 程序复用，便于面试现场演示。
"""

from __future__ import annotations

import math
from typing import Any, Dict, List, Optional

from forecast_core import (
    Arima,
    HoltWinters,
    mae as _mae,
    rmse as _rmse,
    mape as _mape,
    seasonal_strength,
    detect_period,
)


MAX_WINDOWS = 40


class ForecastService:
    """与 Java 版语义一致的预测服务（纯 Python）。"""

    def forecast(self, values: List[float], model: str = "auto", p: Optional[int] = None,
                 d: Optional[int] = None, q: Optional[int] = None, m: Optional[int] = None,
                 seasonal: str = "additive", horizon: Optional[int] = None) -> Dict[str, Any]:
        v = list(values)
        if len(v) < 8:
            raise ValueError("数据量过少，至少需要 8 个点")
        h = 12 if horizon is None else max(1, horizon)
        cfg = self._resolve(model, p, d, q, m, seasonal, v)
        bt = self._backtest(v, cfg, h, max(1, h))
        sigma = max(bt["sigma"], 1e-6)
        resp: Dict[str, Any] = {"history": v}
        try:
            fc = self._predict(v, cfg, h)
            lower = [x - 1.96 * sigma for x in fc]
            upper = [x + 1.96 * sigma for x in fc]
            resp["forecast"] = fc
            resp["lower"] = lower
            resp["upper"] = upper
        except Exception as e:  # noqa: BLE001
            resp["note"] = f"预测失败：{e}（已回退为历史均值外推）"
            fc = [sum(v) / len(v)] * h
            resp["forecast"] = fc
            resp["lower"] = fc
            resp["upper"] = fc
        resp["model"] = cfg["model"]
        resp["params"] = cfg["params"]
        resp["mape"] = bt["overall_mape"]
        resp["rmse"] = bt["overall_rmse"]
        resp["mae"] = bt["overall_mae"]
        if resp.get("note") is None:
            resp["note"] = bt["note"] + ("；置信区间按" + ("样本内残差" if bt["windows"] == 0 else "回测残差") + " ±1.96σ 给出")
        return resp

    def backtest(self, values: List[float], model: str = "auto", p: Optional[int] = None,
                 d: Optional[int] = None, q: Optional[int] = None, m: Optional[int] = None,
                 seasonal: str = "additive", horizon: Optional[int] = None, step: Optional[int] = None) -> Dict[str, Any]:
        v = list(values)
        if len(v) < 8:
            raise ValueError("数据量过少，至少需要 8 个点")
        h = 12 if horizon is None else max(1, horizon)
        stp = 1 if step is None else max(1, step)
        cfg = self._resolve(model, p, d, q, m, seasonal, v)
        bt = self._backtest(v, cfg, h, stp)
        return {
            "horizon_steps": list(range(1, h + 1)),
            "mape": bt["per_step_mape"],
            "rmse": bt["per_step_rmse"],
            "mae": bt["per_step_mae"],
            "overall_mape": bt["overall_mape"],
            "overall_rmse": bt["overall_rmse"],
            "overall_mae": bt["overall_mae"],
            "model": cfg["model"],
            "note": bt["note"] + f"，步长 {stp}，外推 {h} 步",
        }

    # ---- 模型选择 ----
    def _resolve(self, model, p, d, q, m, seasonal, v):
        model = (model or "auto").lower()
        m = 12 if m is None else max(2, m)
        typ = HoltWinters.MULTIPLICATIVE if seasonal == "multiplicative" else HoltWinters.ADDITIVE
        if model == "auto":
            model = "holt-winters" if seasonal_strength(v, m) > 0.3 else "arima"
        cfg = {"model": model, "p": 2 if p is None else p, "d": 1 if d is None else d,
               "q": 1 if q is None else q, "m": m, "type": typ, "params": {}}
        return cfg

    # ---- 预测 ----
    def _predict(self, series, cfg, h):
        if cfg["model"] == "holt-winters":
            hw = HoltWinters(series, cfg["m"], cfg["type"])
            hw.fit()
            cfg["params"]["alpha"] = round(hw.alpha, 3)
            cfg["params"]["beta"] = round(hw.beta, 3)
            cfg["params"]["gamma"] = round(hw.gamma, 3)
            cfg["params"]["type"] = cfg["type"]
            cfg["params"]["period"] = cfg["m"]
            return hw.forecast(h)
        ar = Arima(series, cfg["p"], cfg["d"], cfg["q"])
        ar.fit()
        cfg["params"]["p"] = cfg["p"]
        cfg["params"]["d"] = cfg["d"]
        cfg["params"]["q"] = cfg["q"]
        cfg["params"]["phi"] = [round(x, 4) for x in ar.phi]
        cfg["params"]["theta"] = [round(x, 4) for x in ar.theta]
        cfg["params"]["mu"] = round(ar.mu, 3)
        cfg["params"]["sigma"] = round(ar.sigma, 3)
        return ar.forecast(h)

    # ---- 回测 ----
    def _backtest(self, v, cfg, h, step):
        n = len(v)
        start = max(cfg["m"] * 2, 8) if cfg["model"] == "holt-winters" else 8
        step_mae = [0.0] * h
        step_rms = [0.0] * h
        step_map = [0.0] * h
        step_cnt = [0] * h
        all_abs = all_sq = all_map = 0.0
        all_cnt = 0
        windows = 0
        for s in range(start, n - h + 1, step):
            if windows >= MAX_WINDOWS:
                break
            train = v[:s]
            actual = v[s:s + h]
            try:
                pred = self._predict(train, cfg, h)
            except Exception:  # noqa: BLE001
                continue
            for k in range(h):
                e = actual[k] - pred[k]
                step_mae[k] += abs(e)
                step_rms[k] += e * e
                denom = abs(actual[k])
                if denom > 1e-9:
                    step_map[k] += abs(e) / denom
                step_cnt[k] += 1
                all_abs += abs(e)
                all_sq += e * e
                if denom > 1e-9:
                    all_map += abs(e) / denom
                all_cnt += 1
            windows += 1

        per_step_mape = [round(step_map[k] / max(1, step_cnt[k]) * 100.0, 3) for k in range(h)]
        per_step_rmse = [round(math.sqrt(step_rms[k] / max(1, step_cnt[k])), 3) for k in range(h)]
        per_step_mae = [round(step_mae[k] / max(1, step_cnt[k]), 3) for k in range(h)]
        overall_mae = round(all_abs / max(1, all_cnt), 3)
        overall_rmse = round(math.sqrt(all_sq / max(1, all_cnt)), 3)
        overall_mape = round(all_map / max(1, all_cnt) * 100.0, 3)
        var = all_sq / max(1, all_cnt) - (all_abs / max(1, all_cnt)) ** 2
        sigma = math.sqrt(max(0.0, var))

        if windows == 0:
            ins = self._in_sample_metrics(v, cfg)
            if ins is not None:
                overall_mape, overall_rmse, overall_mae, sigma = ins
                for k in range(h):
                    per_step_mape[k] = overall_mape
                    per_step_rmse[k] = overall_rmse
                    per_step_mae[k] = overall_mae
                note = f"样本不足，指标为样本内拟合误差（外推回测需 >= {start + h} 个点）"
            else:
                note = "样本量不足以评估（请增大数据量或减小外推步数）"
        else:
            note = f"回测窗口 {windows} 个"
        return {
            "per_step_mape": per_step_mape, "per_step_rmse": per_step_rmse,
            "per_step_mae": per_step_mae, "overall_mape": overall_mape,
            "overall_rmse": overall_rmse, "overall_mae": overall_mae,
            "sigma": sigma, "windows": windows, "note": note,
        }

    def _in_sample_metrics(self, v, cfg):
        if cfg["model"] == "holt-winters":
            try:
                hw = HoltWinters(v, cfg["m"], cfg["type"])
                hw.fit()
                fitted = hw.fitted
                s_abs = s_sq = s_map = 0.0
                cnt = 0
                for t in range(cfg["m"], len(v)):
                    e = v[t] - fitted[t]
                    s_abs += abs(e); s_sq += e * e
                    if abs(v[t]) > 1e-9:
                        s_map += abs(e) / abs(v[t])
                    cnt += 1
                if cnt == 0:
                    return None
                mae = s_abs / cnt
                rmse = math.sqrt(s_sq / cnt)
                mape = s_map / cnt * 100.0
                var = s_sq / cnt - (s_abs / cnt) ** 2
                return [mape, rmse, mae, math.sqrt(max(0.0, var))]
            except Exception:  # noqa: BLE001
                return None
        try:
            ar = Arima(v, cfg["p"], cfg["d"], cfg["q"])
            ar.fit()
            res = ar.residuals
            max_lag = max(cfg["p"], cfg["q"])
            z = [x - y for x, y in zip(v[1:], v[:-1])] if cfg["d"] == 1 else list(v)
            z_mean = sum(map(abs, z)) / max(1, len(z))
            s_abs = s_sq = 0.0
            cnt = 0
            for t in range(max_lag, len(res)):
                e = res[t]
                s_abs += abs(e); s_sq += e * e; cnt += 1
            if cnt == 0:
                return None
            mae = s_abs / cnt
            rmse = math.sqrt(s_sq / cnt)
            mape = (s_abs / cnt) / z_mean * 100.0 if z_mean > 1e-9 else 0.0
            var = s_sq / cnt - (s_abs / cnt) ** 2
            return [mape, rmse, mae, math.sqrt(max(0.0, var))]
        except Exception:  # noqa: BLE001
            return None


__all__ = ["ForecastService"]
