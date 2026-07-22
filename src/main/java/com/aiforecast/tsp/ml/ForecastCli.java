package com.aiforecast.tsp.ml;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令行演示：无需 Maven / Spring，可直接用 {@code javac} + {@code java} 运行，
 * 用于快速验证本地 ML 核心（ARIMA / Holt-Winters）是否正常工作。
 *
 * <pre>
 *   javac -d out src/main/java/com/aiforecast/tsp/ml/*.java
 *   java  -cp out com.aiforecast.tsp.ml.ForecastCli [data.csv]
 * </pre>
 *
 * 读取 CSV 的规则与 Web 前端保持一致：取每一行的「最后一个可解析为数字的值」，
 * 因此表头（如 month,sales）和索引列都会被自动忽略，单/多列 CSV 都能正确解析。
 */
public final class ForecastCli {

    public static void main(String[] args) throws Exception {
        double[] data;
        if (args.length > 0) {
            data = readCsv(args[0]);
        } else {
            data = synthetic(120);
            System.out.println("(未提供数据文件，使用内置合成数据：趋势 + 季节 + 噪声)");
        }

        int h = Math.min(12, data.length / 4);
        int trainN = data.length - h;
        double[] train = new double[trainN];
        double[] test = new double[h];
        System.arraycopy(data, 0, train, 0, trainN);
        System.arraycopy(data, trainN, test, 0, h);

        System.out.println("样本量=" + data.length + "  训练=" + trainN + "  测试(h)=" + h);
        System.out.println();

        // ---- ARIMA ----
        try {
            Arima arima = new Arima(train, 2, 1, 1);
            arima.fit();
            double[] fc = arima.forecast(h);
            System.out.println("=== ARIMA(2,1,1) ===");
            System.out.printf("  phi=%.3f,%.3f  theta=%.3f  mu=%.3f  sigma=%.3f%n",
                    arima.phi()[0], arima.phi()[1], arima.theta()[0], arima.mu(), arima.sigma());
            System.out.printf("  MAPE=%.2f%%  RMSE=%.3f  MAE=%.3f%n",
                    Metrics.mape(test, fc), Metrics.rmse(test, fc), Metrics.mae(test, fc));
        } catch (Exception e) {
            System.out.println("ARIMA 失败: " + e.getMessage());
        }

        // ---- Holt-Winters（自动检测季节周期）----
        try {
            int period = detectPeriod(train);
            HoltWinters hw = new HoltWinters(train, period, HoltWinters.Type.ADDITIVE);
            hw.fit();
            double[] fc = hw.forecast(h);
            System.out.println("=== Holt-Winters(加性, m=" + period + " 自动检测) ===");
            System.out.printf("  alpha=%.2f  beta=%.2f  gamma=%.2f%n", hw.alpha(), hw.beta(), hw.gamma());
            System.out.printf("  MAPE=%.2f%%  RMSE=%.3f  MAE=%.3f%n",
                    Metrics.mape(test, fc), Metrics.rmse(test, fc), Metrics.mae(test, fc));
        } catch (Exception e) {
            System.out.println("Holt-Winters 失败: " + e.getMessage());
        }

        System.out.println();
        System.out.println("前 " + h + " 个真实值 : " + fmt(test));
        Arima a = new Arima(train, 2, 1, 1);
        a.fit();
        System.out.println("ARIMA 预测值    : " + fmt(a.forecast(h)));
    }

    private static String fmt(double[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("%.1f", v[i]));
        }
        return sb.toString();
    }

    /** 在常见周期 {24,12,7,4} 中，用自相关(ACF)挑选最显著的季节周期。 */
    private static int detectPeriod(double[] v) {
        int best = 12;
        double bestAcf = acf(v, 12);
        for (int cand : new int[]{24, 7, 4}) {
            if (v.length >= 2 * cand) {
                double a = acf(v, cand);
                if (a > bestAcf + 0.05) {
                    bestAcf = a;
                    best = cand;
                }
            }
        }
        return best;
    }

    private static double acf(double[] v, int lag) {
        if (lag <= 0 || lag >= v.length) {
            return 0.0;
        }
        double m = mean(v);
        double den = 0.0;
        for (double x : v) {
            den += (x - m) * (x - m);
        }
        if (den == 0.0) {
            return 0.0;
        }
        double num = 0.0;
        for (int i = lag; i < v.length; i++) {
            num += (v[i] - m) * (v[i - lag] - m);
        }
        return num / den;
    }

    private static double mean(double[] a) {
        double s = 0.0;
        for (double v : a) {
            s += v;
        }
        return s / a.length;
    }

    private static double[] synthetic(int n) {
        double[] v = new double[n];
        for (int t = 0; t < n; t++) {
            double trend = 0.5 * t;
            double season = 10 * Math.sin(2 * Math.PI * t / 12.0);
            double noise = (Math.random() - 0.5) * 4;
            v[t] = 100 + trend + season + noise;
        }
        return v;
    }

    /**
     * 读取 CSV：取每一行的「最后一个可解析为数字的值」。
     * 表头（如 month,sales）和索引列会被自动忽略，兼容单/多列 CSV。
     */
    private static double[] readCsv(String path) throws Exception {
        List<Double> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] cells = line.split("[,\\s;]+");
                // 从右往左找第一个数字（即最后一列），忽略其余（索引列/表头）
                for (int i = cells.length - 1; i >= 0; i--) {
                    String tok = cells[i].trim();
                    if (tok.isEmpty()) {
                        continue;
                    }
                    try {
                        list.add(Double.parseDouble(tok));
                        break;
                    } catch (NumberFormatException ignore) {
                        // 非数字 token（表头等），继续向前找
                    }
                }
            }
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("文件无有效数值: " + path);
        }
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
