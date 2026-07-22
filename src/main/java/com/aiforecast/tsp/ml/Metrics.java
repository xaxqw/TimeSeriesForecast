package com.aiforecast.tsp.ml;

/**
 * 预测误差指标：MAE / RMSE / MAPE / SMAPE。
 * 纯 JDK 实现。
 */
public final class Metrics {

    private Metrics() {
    }

    public static double mae(double[] actual, double[] pred) {
        requireSameLength(actual, pred);
        double s = 0.0;
        for (int i = 0; i < actual.length; i++) {
            s += Math.abs(actual[i] - pred[i]);
        }
        return s / actual.length;
    }

    public static double rmse(double[] actual, double[] pred) {
        requireSameLength(actual, pred);
        double s = 0.0;
        for (int i = 0; i < actual.length; i++) {
            double e = actual[i] - pred[i];
            s += e * e;
        }
        return Math.sqrt(s / actual.length);
    }

    /** 平均绝对百分比误差（百分比为 0~100）。分母接近 0 时做保护。 */
    public static double mape(double[] actual, double[] pred) {
        requireSameLength(actual, pred);
        double s = 0.0;
        int n = 0;
        for (int i = 0; i < actual.length; i++) {
            double denom = Math.abs(actual[i]);
            if (denom < 1e-9) {
                continue;
            }
            s += Math.abs(actual[i] - pred[i]) / denom;
            n++;
        }
        return n == 0 ? 0.0 : (s / n) * 100.0;
    }

    public static double smape(double[] actual, double[] pred) {
        requireSameLength(actual, pred);
        double s = 0.0;
        for (int i = 0; i < actual.length; i++) {
            double denom = Math.abs(actual[i]) + Math.abs(pred[i]);
            if (denom < 1e-9) {
                continue;
            }
            s += Math.abs(actual[i] - pred[i]) / (denom / 2.0);
        }
        return (s / actual.length) * 100.0;
    }

    private static void requireSameLength(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) {
            throw new IllegalArgumentException("待比较的两个序列长度必须一致");
        }
    }
}
