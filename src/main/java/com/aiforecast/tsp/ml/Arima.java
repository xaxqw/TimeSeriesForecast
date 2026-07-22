package com.aiforecast.tsp.ml;

/**
 * ARIMA(p, d, q) 预测模型。
 *
 * <p>实现要点（全部纯 Java，无第三方依赖）：
 * <ul>
 *   <li>差分：对原始序列做 d 阶差分得到平稳序列 z；</li>
 *   <li>参数估计：在 z 上用「条件最小二乘」估计 AR 系数 phi、MA 系数 theta 与均值 mu，
 *       目标函数最小化由 Nelder-Mead 单纯形法求解（无需解析梯度）；</li>
 *   <li>预测：在差分尺度上递归外推 h 步，再逆向积分回原始尺度；</li>
 *   <li>对 AR 系数做稳定性惩罚，避免发散。</li>
 * </ul>
 */
public final class Arima {

    private final double[] y;
    private final int p;
    private final int d;
    private final int q;

    private double[] phi;
    private double[] theta;
    private double mu;
    private double sigma; // 差分尺度残差标准差
    private double[] residuals; // 样本内一步残差（fit 后可用）

    public Arima(double[] y, int p, int d, int q) {
        if (y == null || y.length == 0) {
            throw new IllegalArgumentException("序列不能为空");
        }
        if (p < 0 || d < 0 || q < 0) {
            throw new IllegalArgumentException("p/d/q 必须 >= 0");
        }
        this.y = y.clone();
        this.p = p;
        this.d = d;
        this.q = q;
    }

    public void fit() {
        double[] z = new TimeSeries(y).difference(d).toArray();
        int n = z.length;
        int maxLag = Math.max(p, q);
        if (n <= maxLag + 2) {
            throw new IllegalArgumentException("样本量不足以拟合 ARIMA(" + p + "," + d + "," + q + ")，请增大数据或减小阶数");
        }

        int dim = p + q + 1;
        double[] x0 = new double[dim];
        for (int i = 0; i < p; i++) {
            x0[i] = 0.1;
        }
        for (int i = 0; i < q; i++) {
            x0[p + i] = 0.0;
        }
        x0[p + q] = mean(z);

        double[] best = NelderMead.minimize(params -> armaSSE(z, params, p, q), x0, 0.5, 8000, 1e-7);

        phi = new double[p];
        theta = new double[q];
        for (int i = 0; i < p; i++) {
            phi[i] = best[i];
        }
        for (int i = 0; i < q; i++) {
            theta[i] = best[p + i];
        }
        mu = best[p + q];

        double[] res = armaResidualsFull(z, phi, theta, mu, p, q);
        double sse = 0.0;
        for (double r : res) {
            sse += r * r;
        }
        sigma = Math.sqrt(sse / Math.max(1, res.length - dim));
        this.residuals = res;
    }

    /** 样本内一步残差（差分尺度），用于无法外推回测时的兜底指标。 */
    public double[] residuals() {
        if (residuals == null) {
            throw new IllegalStateException("请先调用 fit()");
        }
        return residuals.clone();
    }

    /** 预测未来 h 步（原始尺度）。 */
    public double[] forecast(int h) {
        if (phi == null) {
            throw new IllegalStateException("请先调用 fit()");
        }
        double[] z = new TimeSeries(y).difference(d).toArray();
        int n = z.length;
        double[] aFull = armaResidualsFull(z, phi, theta, mu, p, q);

        double[] zext = new double[n + h];
        System.arraycopy(z, 0, zext, 0, n);
        double[] aext = new double[n + h];
        System.arraycopy(aFull, 0, aext, 0, n);

        if (d == 0) {
            double[] out = new double[h];
            for (int t = n; t < n + h; t++) {
                double pred = mu;
                for (int i = 1; i <= p; i++) {
                    pred += phi[i - 1] * (zext[t - i] - mu);
                }
                double ma = 0.0;
                for (int j = 1; j <= q; j++) {
                    ma += theta[j - 1] * aext[t - j];
                }
                zext[t] = pred + ma;
                aext[t] = 0.0;
                out[t - n] = zext[t];
            }
            return out;
        }

        double[] dForecast = new double[h];
        for (int t = n; t < n + h; t++) {
            double pred = mu;
            for (int i = 1; i <= p; i++) {
                pred += phi[i - 1] * (zext[t - i] - mu);
            }
            double ma = 0.0;
            for (int j = 1; j <= q; j++) {
                ma += theta[j - 1] * aext[t - j];
            }
            double zhat = pred + ma;
            dForecast[t - n] = zhat - zext[t - 1]; // 差分尺度增量
            zext[t] = zhat;
            aext[t] = 0.0;
        }
        return TimeSeries.invertDifference(dForecast, y, d);
    }

    public double sigma() {
        return sigma;
    }

    public double[] phi() {
        return phi == null ? new double[0] : phi.clone();
    }

    public double[] theta() {
        return theta == null ? new double[0] : theta.clone();
    }

    public double mu() {
        return mu;
    }

    // ---- 内部：ARMA(p,q) 残差 ----

    static double armaSSE(double[] z, double[] params, int p, int q) {
        double[] res = armaResidualsFull(z, params, p, q);
        double sse = 0.0;
        for (double r : res) {
            sse += r * r;
        }
        // 稳定性惩罚：AR 系数过大易发散
        double pen = 0.0;
        for (int i = 0; i < p; i++) {
            if (Math.abs(params[i]) > 1.0) {
                pen += (Math.abs(params[i]) - 1.0) * 1000.0;
            }
        }
        return sse + pen;
    }

    private static double[] armaResidualsFull(double[] z, double[] phi, double[] theta, double mu, int p, int q) {
        return armaResidualsFull(z, concat(phi, theta, mu), p, q);
    }

    private static double[] armaResidualsFull(double[] z, double[] params, int p, int q) {
        int n = z.length;
        int maxLag = Math.max(p, q);
        double[] phi = new double[p];
        double[] theta = new double[q];
        for (int i = 0; i < p; i++) {
            phi[i] = params[i];
        }
        for (int i = 0; i < q; i++) {
            theta[i] = params[p + i];
        }
        double mu = params[p + q];

        double[] a = new double[n];
        for (int t = 0; t < n; t++) {
            if (t < maxLag) {
                a[t] = 0.0;
                continue;
            }
            double pred = mu;
            for (int i = 1; i <= p; i++) {
                pred += phi[i - 1] * (z[t - i] - mu);
            }
            double ma = 0.0;
            for (int j = 1; j <= q; j++) {
                int idx = t - j;
                double aval = (idx >= 0 && idx >= maxLag) ? a[idx] : 0.0;
                ma += theta[j - 1] * aval;
            }
            a[t] = z[t] - pred - ma;
        }
        return a;
    }

    private static double[] concat(double[] phi, double[] theta, double mu) {
        double[] out = new double[phi.length + theta.length + 1];
        System.arraycopy(phi, 0, out, 0, phi.length);
        System.arraycopy(theta, 0, out, phi.length, theta.length);
        out[out.length - 1] = mu;
        return out;
    }

    private static double mean(double[] a) {
        double s = 0.0;
        for (double v : a) {
            s += v;
        }
        return s / a.length;
    }
}
