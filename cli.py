"""命令行演示：无需 Web 框架，直接用 Python 运行验证 ML 核心。

用法：
    python cli.py                 # 使用内置合成数据（趋势 + 季节 + 噪声）
    python cli.py data.csv        # 读取 CSV（自动取每行最后一列数值）

读取规则与前端一致：取每一行「最后一个可解析为数字的值」，表头/索引列自动忽略。
"""

from __future__ import annotations

import math
import random
import sys

from forecast_core import (
    Arima,
    HoltWinters,
    mae,
    rmse,
    mape,
    detect_period,
)
from service import ForecastService


def read_csv(path: str) -> list[float]:
    values: list[float] = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            cells = line.split(",")
            for tok in reversed(cells):
                tok = tok.strip()
                if not tok:
                    continue
                try:
                    values.append(float(tok))
                    break
                except ValueError:
                    continue
    if not values:
        raise ValueError(f"文件无有效数值: {path}")
    return values


def synthetic(n: int) -> list[float]:
    random.seed(42)
    return [100 + 0.5 * t + 10 * math.sin(2 * math.pi * t / 12.0) + (random.random() - 0.5) * 4 for t in range(n)]


def fmt(values: list[float]) -> str:
    return ", ".join(f"{x:.1f}" for x in values)


def main() -> None:
    data = read_csv(sys.argv[1]) if len(sys.argv) > 1 else synthetic(120)
    h = min(12, len(data) // 4)
    train = data[: len(data) - h]
    test = data[len(data) - h:]

    print(f"样本量={len(data)}  训练={len(train)}  测试(h)={h}\n")

    try:

        ar = Arima(train, 2, 1, 1)
        ar.fit()
        fc = ar.forecast(h)
        print("=== ARIMA(2,1,1) ===")
        print(f"  phi={ar.phi[0]:.3f},{ar.phi[1]:.3f}  theta={ar.theta[0]:.3f}  mu={ar.mu:.3f}  sigma={ar.sigma:.3f}")
        print(f"  MAPE={mape(test, fc):.2f}%  RMSE={rmse(test, fc):.3f}  MAE={mae(test, fc):.3f}")
    except Exception as e:  # noqa: BLE001
        print(f"ARIMA 失败: {e}")

    try:
        period = detect_period(train)
        hw = HoltWinters(train, period, HoltWinters.ADDITIVE)
        hw.fit()
        fc = hw.forecast(h)
        print(f"=== Holt-Winters(加性, m={period} 自动检测) ===")
        print(f"  alpha={hw.alpha:.2f}  beta={hw.beta:.2f}  gamma={hw.gamma:.2f}")
        print(f"  MAPE={mape(test, fc):.2f}%  RMSE={rmse(test, fc):.3f}  MAE={mae(test, fc):.3f}")
    except Exception as e:  # noqa: BLE001
        print(f"Holt-Winters 失败: {e}")

    print("\n前 %d 个真实值 : %s" % (h, fmt(test)))
    a = Arima(train, 2, 1, 1)
    a.fit()
    print("ARIMA 预测值    : %s" % fmt(a.forecast(h)))


if __name__ == "__main__":
    main()
