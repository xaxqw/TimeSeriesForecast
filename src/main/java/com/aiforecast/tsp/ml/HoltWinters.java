package com.aiforecast.tsp.ml;

/**
 * Holt-Winters 三次指数平滑（加性 / 乘性季节），用于带季节性的时序预测。
 *
 * <p>特点：内置网格搜索自动调参（alpha/beta/gamma），纯 Java 实现，无第三方依赖。
 */
public final class HoltWinters {

    public enum Type {
        ADDITIVE,
        MULTIPLICATIVE
    }

    private final double[] y;
    private final int m; // 季节周期
    private final Type type;

    private double alpha;
    private double beta;
    private double gamma;
    private double level;
    private double trend;
    private double[] seasonals; // 长度 m，保存最近一个周期的季节性分量
    private double[] fitted;    // 训练集单步拟合值（与 y 等长，前 m 个为 NaN 占位）

    public HoltWinters(double[] y, int m, Type type) {
        if (y == null || y.length < 2 * m) {
            throw new IllegalArgumentException("Holt-Winters 需要样本量 >= 2 倍季节周期(" + (2 * m) + ")");
        }
        if (m < 2) {
            throw new IllegalArgumentException("季节周期 m 必须 >= 2");
        }
        if (type == Type.MULTIPLICATIVE) {
            for (double v : y) {
                if (v <= 0.0) {
                    throw new IllegalArgumentException("乘性(Multiplicative) Holt-Winters 要求序列严格为正（含 0 或负值会除零），请改用加性或对数变换后输入");
                }
            }
        }
        this.y = y.clone();
        this.m = m;
        this.type = type;
    }

    /** 网格搜索自动调参并拟合。 */
    public void fit() {
        double bestSse = Double.POSITIVE_INFINITY;
        double[] best = new double[]{0.3, 0.1, 0.3};
        double[] grid = {0.1, 0.3, 0.5, 0.7, 0.9};
        for (double a : grid) {
            for (double b : grid) {
                for (double g : grid) {
                    double sse = trainSSE(a, b, g);
                    if (sse < bestSse) {
                        bestSse = sse;
                        best[0] = a;
                        best[1] = b;
                        best[2] = g;
                    }
                }
            }
        }
        alpha = best[0];
        beta = best[1];
        gamma = best[2];
        trainState(alpha, beta, gamma);
    }

    /** 用指定参数拟合（供网格搜索复用）。 */
    private void trainState(double a, double b, double g) {
        int n = y.length;
        double[] season = new double[m];

        double l0 = 0.0;
        for (int i = 0; i < m; i++) {
            l0 += y[i];
        }
        l0 /= m;

        double b0;
        if (n >= 2 * m) {
            double s2 = 0.0;
            for (int i = m; i < 2 * m; i++) {
                s2 += y[i];
            }
            b0 = (s2 / m - l0) / m;
        } else {
            b0 = 0.0;
        }

        double level = l0;
        double trend = b0;
        if (type == Type.ADDITIVE) {
            for (int i = 0; i < m; i++) {
                season[i] = y[i] - l0;
            }
        } else {
            for (int i = 0; i < m; i++) {
                season[i] = y[i] / l0;
            }
        }

        double[] fitted = new double[n];
        for (int t = m; t < n; t++) {
            double prevLevel = level;
            int idx = t % m;
            if (type == Type.ADDITIVE) {
                double s = season[idx];
                fitted[t] = level + trend + s;
                level = a * (y[t] - s) + (1 - a) * (level + trend);
                trend = b * (level - prevLevel) + (1 - b) * trend;
                season[idx] = g * (y[t] - level) + (1 - g) * s;
            } else {
                double s = season[idx];
                fitted[t] = (level + trend) * s;
                level = a * (y[t] / s) + (1 - a) * (level + trend);
                trend = b * (level - prevLevel) + (1 - b) * trend;
                season[idx] = g * (y[t] / level) + (1 - g) * s;
            }
        }

        this.level = level;
        this.trend = trend;
        this.seasonals = season;
        this.fitted = fitted;
    }

    private double trainSSE(double a, double b, double g) {
        trainState(a, b, g);
        double sse = 0.0;
        int cnt = 0;
        for (int t = m; t < y.length; t++) {
            double e = y[t] - fitted[t];
            sse += e * e;
            cnt++;
        }
        return cnt == 0 ? Double.POSITIVE_INFINITY : sse / cnt;
    }

    /** 预测未来 h 步。 */
    public double[] forecast(int h) {
        if (seasonals == null) {
            throw new IllegalStateException("请先调用 fit()");
        }
        double[] out = new double[h];
        for (int k = 1; k <= h; k++) {
            int idx = (y.length - 1 + k) % m;
            if (type == Type.ADDITIVE) {
                out[k - 1] = level + k * trend + seasonals[idx];
            } else {
                out[k - 1] = (level + k * trend) * seasonals[idx];
            }
        }
        return out;
    }

    /** 训练集单步拟合值（用于误差评估）。 */
    public double[] fitted() {
        return fitted.clone();
    }

    public double alpha() {
        return alpha;
    }

    public double beta() {
        return beta;
    }

    public double gamma() {
        return gamma;
    }

    public Type type() {
        return type;
    }
}
