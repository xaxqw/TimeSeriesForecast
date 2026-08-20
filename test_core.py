"""核心算法单元测试：验证与 Java 版语义一致的关键数值行为。

运行：
    python -m unittest test_core -v
或：
    python test_core.py
"""

from __future__ import annotations

import math
import unittest

from forecast_core import (
    Arima,
    HoltWinters,
    acf,
    detect_period,
    difference,
    invert_difference,
    mae,
    mape,
    nelder_mead_minimize,
    rmse,
    seasonal_strength,
    smape,
)
from service import ForecastService


def make_seasonal(n: int = 120, m: int = 12) -> list[float]:
    """趋势 + 季节 + 噪声的合成序列，与 CLI 内置数据同源。"""
    import random

    random.seed(42)
    return [
        100 + 0.5 * t + 10 * math.sin(2 * math.pi * t / m) + (random.random() - 0.5) * 4
        for t in range(n)
    ]


def make_pure_seasonal(n: int = 120, m: int = 12) -> list[float]:
    """无趋势纯季节序列：季节强度 ≈ 1，auto 应稳定选中 Holt-Winters。"""
    return [100.0 + 10.0 * math.sin(2 * math.pi * t / m) for t in range(n)]


class TestTimeSeriesTools(unittest.TestCase):
    def test_difference(self):
        self.assertEqual(difference([1, 3, 6, 10], 1), [2, 3, 4])
        self.assertEqual(difference([1, 3, 6, 10], 2), [1, 1])
        with self.assertRaises(ValueError):
            difference([1, 2], -1)

    def test_invert_difference_roundtrip(self):
        """d_forecast 语义：d 阶差分序列的「未来一阶差分」。

        传入 0 表示未来差分保持最后值 → 一阶差分下即线性外推。
        y = [10,20,15,30,25]，d1 = [10,-5,15,-5]，保持最后增量 -5：
        预测 = [25-5, 20-5, 15-5] = [20, 15, 10]
        """
        y = [10.0, 20.0, 15.0, 30.0, 25.0]
        out = invert_difference([0.0, 0.0, 0.0], y, 1)
        self.assertEqual(out, [20.0, 15.0, 10.0])
        # 语义验证：增量非零时，out[0] = y[-1] + d1[-1] + delta
        out2 = invert_difference([2.0, 0.0], y, 1)
        self.assertAlmostEqual(out2[0], 25.0 + (-5.0) + 2.0, places=9)

    def test_invert_difference_d2(self):
        """y = [2,4,8,16]，d2 = [2,4]。d_forecast=[0] 表示二阶差分保持最后值 4：
        Δy 未来 = 8+4 = 12 → y 未来 = 16+12 = 28
        """
        y = [2.0, 4.0, 8.0, 16.0]
        out = invert_difference([0.0], y, 2)
        self.assertAlmostEqual(out[0], 28.0, places=9)


class TestMetrics(unittest.TestCase):
    def test_mae_rmse_mape_smape(self):
        a = [1.0, 2.0, 3.0, 4.0]
        b = [1.0, 2.0, 3.0, 4.0]
        self.assertEqual(mae(a, b), 0.0)
        self.assertEqual(rmse(a, b), 0.0)
        self.assertEqual(mape(a, b), 0.0)
        self.assertEqual(smape(a, b), 0.0)
        b2 = [2.0, 2.0, 2.0, 2.0]
        self.assertAlmostEqual(mae(a, b2), 1.0)
        # MAPE = (|1-2|/1 + 0 + |3-2|/3 + |4-2|/4) / 4 * 100 = 45.83%
        self.assertAlmostEqual(mape(a, b2), 100.0 * (1.0 + 0.0 + 1 / 3 + 0.5) / 4, places=9)

    def test_length_mismatch(self):
        with self.assertRaises(ValueError):
            mae([1.0], [1.0, 2.0])


class TestNelderMead(unittest.TestCase):
    def test_quadratic(self):
        # 最小化 f(x,y) = (x-3)^2 + (y+2)^2，最优 (3, -2)
        best = nelder_mead_minimize(
            lambda p: (p[0] - 3) ** 2 + (p[1] + 2) ** 2,
            [0.0, 0.0],
            max_iter=5000,
        )
        self.assertLess(abs(best[0] - 3.0), 1e-3)
        self.assertLess(abs(best[1] + 2.0), 1e-3)


class TestArima(unittest.TestCase):
    def test_fit_forecast_linear(self):
        """ARIMA(0,1,0) 拟合纯线性序列，预测应接近线性外推。"""
        y = [float(10 + 2 * t) for t in range(40)]
        ar = Arima(y, 0, 1, 0)
        ar.fit()
        fc = ar.forecast(3)
        self.assertEqual(len(fc), 3)
        for k, v in enumerate(fc, start=1):
            self.assertAlmostEqual(v, 10 + 2 * (40 - 1) + 2 * k, delta=0.5)

    def test_forecast_before_fit_raises(self):
        ar = Arima([1.0, 2.0, 3.0], 0, 1, 0)
        with self.assertRaises(RuntimeError):
            ar.forecast(1)

    def test_insufficient_data(self):
        with self.assertRaises(ValueError):
            Arima([1.0, 2.0, 3.0, 4.0], 2, 1, 1).fit()


class TestHoltWinters(unittest.TestCase):
    def test_additive_seasonal(self):
        y = make_seasonal(120)
        hw = HoltWinters(y, 12, HoltWinters.ADDITIVE)
        hw.fit()
        fc = hw.forecast(12)
        self.assertEqual(len(fc), 12)
        # 对纯季节合成数据，MAPE 应很小
        err = mape(y[-12:], fc)
        self.assertLess(err, 15.0)

    def test_multiplicative_requires_positive(self):
        y = [float(100 + t) for t in range(48)]
        y[5] = -1.0
        with self.assertRaises(ValueError):
            HoltWinters(y, 12, HoltWinters.MULTIPLICATIVE)

    def test_multiplicative_positive(self):
        y = [float(80 + 20 * math.sin(2 * math.pi * t / 12) + t) for t in range(48)]
        y = [max(1.0, v) for v in y]  # 保证严格为正
        hw = HoltWinters(y, 12, HoltWinters.MULTIPLICATIVE)
        hw.fit()
        fc = hw.forecast(6)
        self.assertEqual(len(fc), 6)
        self.assertTrue(all(v > 0 for v in fc))

    def test_insufficient_data(self):
        with self.assertRaises(ValueError):
            HoltWinters([1.0, 2.0, 3.0], 2, HoltWinters.ADDITIVE)


class TestAutoSelection(unittest.TestCase):
    def test_seasonal_strength(self):
        # 无趋势纯季节 → 强度≈1；带趋势（趋势占方差主导）→ 强度低，与 Java 版行为一致
        self.assertGreater(seasonal_strength(make_pure_seasonal(), 12), 0.9)
        self.assertLess(seasonal_strength(make_seasonal(), 12), 0.3)

    def test_acf_peak(self):
        y = make_seasonal(120)
        # 周期 12 的序列在 lag=12 处 ACF 应明显大于 lag=7
        self.assertGreater(acf(y, 12), acf(y, 7))

    def test_detect_period(self):
        y = make_seasonal(120)
        self.assertEqual(detect_period(y), 12)

    def test_service_auto_picks_hw(self):
        svc = ForecastService()
        resp = svc.forecast(make_pure_seasonal(), model="auto", horizon=6)
        self.assertEqual(resp["model"], "holt-winters")
        self.assertEqual(len(resp["forecast"]), 6)
        self.assertEqual(len(resp["lower"]), 6)
        self.assertEqual(len(resp["upper"]), 6)
        self.assertIn("mape", resp)
        self.assertIn("params", resp)

    def test_service_auto_picks_arima_on_trend(self):
        """带趋势序列季节强度低 → auto 应选 ARIMA（与 Java 版一致）。"""
        svc = ForecastService()
        resp = svc.forecast(make_seasonal(), model="auto", horizon=6)
        self.assertEqual(resp["model"], "arima")

    def test_service_arima_and_backtest(self):
        svc = ForecastService()
        y = make_seasonal(96)
        r = svc.forecast(y, model="arima", horizon=4)
        self.assertEqual(r["model"], "arima")
        bt = svc.backtest(y, model="auto", horizon=3, step=3)
        self.assertEqual(len(bt["mape"]), 3)
        self.assertIn("overall_mape", bt)

    def test_service_too_few_points(self):
        svc = ForecastService()
        with self.assertRaises(ValueError):
            svc.forecast([1.0, 2.0, 3.0])


if __name__ == "__main__":
    unittest.main(verbosity=2)
