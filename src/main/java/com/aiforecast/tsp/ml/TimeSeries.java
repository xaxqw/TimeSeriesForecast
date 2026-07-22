package com.aiforecast.tsp.ml;

/**
 * 时序数据结构与差分/积分工具。
 * 纯 JDK 实现，无任何第三方依赖，可在 JDK 8+ 上独立编译运行。
 */
public final class TimeSeries {

    private final double[] values;

    public TimeSeries(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("时序数据不能为空");
        }
        this.values = values.clone();
    }

    public int length() {
        return values.length;
    }

    public double get(int i) {
        return values[i];
    }

    public double[] toArray() {
        return values.clone();
    }

    public double mean() {
        double s = 0.0;
        for (double v : values) {
            s += v;
        }
        return s / values.length;
    }

    /** 计算 d 阶差分后的新序列（长度 = length - d）。 */
    public TimeSeries difference(int d) {
        if (d < 0) {
            throw new IllegalArgumentException("差分阶数 d 必须 >= 0");
        }
        double[] cur = values;
        for (int k = 0; k < d; k++) {
            double[] out = new double[cur.length - 1];
            for (int i = 0; i < out.length; i++) {
                out[i] = cur[i + 1] - cur[i];
            }
            cur = out;
        }
        return new TimeSeries(cur);
    }

    /**
     * 将 d 阶差分尺度上的「未来增量」序列逆向积分回原始尺度。
     *
     * @param dForecast 长度为 h，是 d 阶差分尺度上的未来增量（即每一步的新 d 阶差分）
     * @param original  原始序列（用于取尾部作为积分起点）
     * @param d         差分阶数
     * @return 长度为 h 的原始尺度预测值
     */
    public static double[] invertDifference(double[] dForecast, double[] original, int d) {
        int h = dForecast.length;
        double[] result = new double[h];

        // lastVal[o] 保存第 o 阶差分序列的「最后一个值」，o = 0..d
        // lastVal[0] 即原始序列最后一个值
        double[] lastVal = new double[d + 1];
        lastVal[0] = original[original.length - 1];

        double[] prev = original.clone();
        for (int o = 1; o <= d; o++) {
            double[] cur = new double[prev.length - 1];
            for (int i = 0; i < cur.length; i++) {
                cur[i] = prev[i + 1] - prev[i];
            }
            lastVal[o] = cur[cur.length - 1];
            prev = cur;
        }

        for (int k = 0; k < h; k++) {
            double v = lastVal[d] + dForecast[k];
            lastVal[d] = v;
            for (int o = d - 1; o >= 1; o--) {
                double nv = lastVal[o] + v;
                v = nv;
                lastVal[o] = v;
            }
            // v 此时为「新的 1 阶差分」；原始值 = 上一原始值 + 新的 1 阶差分
            double orig = lastVal[0] + v;
            lastVal[0] = orig;
            result[k] = orig;
        }
        return result;
    }
}
