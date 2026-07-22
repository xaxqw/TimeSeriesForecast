package com.aiforecast.tsp.ml;

/**
 * Nelder-Mead（下山单纯形）无梯度优化器。
 * 用于在无需解析梯度的情况下最小化任意目标函数，用于 ARIMA 条件最小二乘参数估计。
 * 纯 JDK 实现。
 */
final class NelderMead {

    @FunctionalInterface
    interface Objective {
        double f(double[] x);
    }

    private NelderMead() {
    }

    static double[] minimize(Objective obj, double[] x0, double simplexSize, int maxIter, double tol) {
        int n = x0.length;
        double[][] simplex = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            simplex[i] = x0.clone();
            if (i > 0) {
                simplex[i][i - 1] += simplexSize;
            }
        }
        double[] fvals = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            fvals[i] = obj.f(simplex[i]);
        }

        double alpha = 1.0, gamma = 2.0, rho = 0.5, sigma = 0.5;

        Integer[] idx = new Integer[n + 1];
        for (int iter = 0; iter < maxIter; iter++) {
            for (int i = 0; i <= n; i++) {
                idx[i] = i;
            }
            java.util.Arrays.sort(idx, (a, b) -> Double.compare(fvals[a], fvals[b]));

            if (Math.abs(fvals[idx[n]] - fvals[idx[0]]) < tol) {
                break;
            }

            // 前 n 个最好点的质心
            double[] centroid = new double[n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    centroid[j] += simplex[idx[i]][j];
                }
            }
            for (int j = 0; j < n; j++) {
                centroid[j] /= n;
            }

            // 反射
            double[] xr = new double[n];
            for (int j = 0; j < n; j++) {
                xr[j] = centroid[j] + alpha * (centroid[j] - simplex[idx[n]][j]);
            }
            double fr = obj.f(xr);

            if (fr < fvals[idx[0]]) {
                // 扩张
                double[] xe = new double[n];
                for (int j = 0; j < n; j++) {
                    xe[j] = centroid[j] + gamma * (xr[j] - centroid[j]);
                }
                double fe = obj.f(xe);
                if (fe < fr) {
                    setPoint(simplex, fvals, idx[n], xe, fe);
                } else {
                    setPoint(simplex, fvals, idx[n], xr, fr);
                }
            } else if (fr < fvals[idx[n - 1]]) {
                setPoint(simplex, fvals, idx[n], xr, fr);
            } else {
                // 收缩
                double[] xc = new double[n];
                for (int j = 0; j < n; j++) {
                    xc[j] = centroid[j] + rho * (simplex[idx[n]][j] - centroid[j]);
                }
                double fc = obj.f(xc);
                if (fc < fvals[idx[n]]) {
                    setPoint(simplex, fvals, idx[n], xc, fc);
                } else {
                    // 收缩全体（除最好点外）
                    for (int i = 1; i <= n; i++) {
                        for (int j = 0; j < n; j++) {
                            simplex[idx[i]][j] = sigma * (simplex[idx[i]][j] + simplex[idx[0]][j]);
                        }
                        fvals[idx[i]] = obj.f(simplex[idx[i]]);
                    }
                }
            }
        }

        // 返回当前最好点
        int best = 0;
        for (int i = 1; i <= n; i++) {
            if (fvals[i] < fvals[best]) {
                best = i;
            }
        }
        return simplex[best].clone();
    }

    private static void setPoint(double[][] simplex, double[] fvals, int pos, double[] p, double fv) {
        simplex[pos] = p.clone();
        fvals[pos] = fv;
    }
}
