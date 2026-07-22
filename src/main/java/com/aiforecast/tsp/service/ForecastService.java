package com.aiforecast.tsp.service;

import com.aiforecast.tsp.api.BacktestRequest;
import com.aiforecast.tsp.api.BacktestResponse;
import com.aiforecast.tsp.api.ForecastRequest;
import com.aiforecast.tsp.api.ForecastResponse;
import com.aiforecast.tsp.ml.Arima;
import com.aiforecast.tsp.ml.HoltWinters;
import com.aiforecast.tsp.ml.Metrics;
import com.aiforecast.tsp.ml.TimeSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预测编排服务：选择模型、训练、预测、回测评估、置信区间。
 * 纯 Java（仅依赖 JDK + 本项目 ml 包），可在 Spring 之外独立测试。
 */
public final class ForecastService {

    private static final int MAX_WINDOWS = 40; // 控制回测耗时

    public ForecastResponse forecast(ForecastRequest req) {
        double[] v = toArray(req.getValues());
        if (v.length < 8) {
            throw new IllegalArgumentException("数据量过少，至少需要 8 个点");
        }
        int h = req.getHorizon() == null ? 12 : Math.max(1, req.getHorizon());

        ResolvedModel cfg = resolve(req, v);
        BacktestResult bt = backtestRaw(v, cfg, h, Math.max(1, h)); // 非重叠窗口，快速

        ForecastResponse resp = new ForecastResponse();
        resp.setHistory(toList(v));
        try {
            double[] fc = predict(v, cfg, h);
            double sigma = Math.max(bt.sigma, 1e-6);
            double[] lower = new double[h];
            double[] upper = new double[h];
            for (int i = 0; i < h; i++) {
                lower[i] = fc[i] - 1.96 * sigma;
                upper[i] = fc[i] + 1.96 * sigma;
            }
            resp.setForecast(toList(fc));
            resp.setLower(toList(lower));
            resp.setUpper(toList(upper));
        } catch (Exception e) {
            resp.setNote("预测失败：" + e.getMessage() + "（已回退为历史均值外推）");
            double[] fc = new double[h];
            Arrays.fill(fc, mean(v));
            resp.setForecast(toList(fc));
            resp.setLower(toList(fc));
            resp.setUpper(toList(fc));
        }
        resp.setModel(cfg.model);
        resp.setParams(cfg.params);
        resp.setMape(bt.overallMape);
        resp.setRmse(bt.overallRmse);
        resp.setMae(bt.overallMae);
        if (resp.getNote() == null) {
            resp.setNote(bt.note + "；置信区间按" + (bt.windows == 0 ? "样本内残差" : "回测残差")
                    + " ±1.96σ 给出");
        }
        return resp;
    }

    public BacktestResponse backtest(BacktestRequest req) {
        double[] v = toArray(req.getValues());
        if (v.length < 8) {
            throw new IllegalArgumentException("数据量过少，至少需要 8 个点");
        }
        int h = req.getHorizon() == null ? 12 : Math.max(1, req.getHorizon());
        int step = req.getStep() == null ? 1 : Math.max(1, req.getStep());

        ResolvedModel cfg = resolve(toRequest(req), v);
        BacktestResult bt = backtestRaw(v, cfg, h, step);

        BacktestResponse resp = new BacktestResponse();
        resp.setHorizonSteps(bt.steps);
        resp.setMape(bt.perStepMape);
        resp.setRmse(bt.perStepRmse);
        resp.setMae(bt.perStepMae);
        resp.setOverallMape(bt.overallMape);
        resp.setOverallRmse(bt.overallRmse);
        resp.setOverallMae(bt.overallMae);
        resp.setModel(cfg.model);
        resp.setNote(bt.note + "，步长 " + step + "，外推 " + h + " 步");
        return resp;
    }

    // ---- 模型选择 / 解析 ----

    private static class ResolvedModel {
        String model;          // arima | holt-winters
        int p, d, q, m;
        HoltWinters.Type type;
        Map<String, Object> params = new LinkedHashMap<>();
    }

    private ResolvedModel resolve(ForecastRequest req, double[] v) {
        ResolvedModel cfg = new ResolvedModel();
        String model = req.getModel() == null ? "auto" : req.getModel().toLowerCase();
        int m = req.getM() == null ? 12 : Math.max(2, req.getM());
        HoltWinters.Type type = "multiplicative".equalsIgnoreCase(req.getSeasonal())
                ? HoltWinters.Type.MULTIPLICATIVE : HoltWinters.Type.ADDITIVE;

        if ("auto".equals(model)) {
            model = seasonalStrength(v, m) > 0.3 ? "holt-winters" : "arima";
        }
        cfg.model = model;
        cfg.m = m;
        cfg.type = type;
        cfg.p = req.getP() == null ? 2 : req.getP();
        cfg.d = req.getD() == null ? 1 : req.getD();
        cfg.q = req.getQ() == null ? 1 : req.getQ();
        return cfg;
    }

    private ForecastRequest toRequest(BacktestRequest b) {
        ForecastRequest r = new ForecastRequest();
        r.setModel(b.getModel());
        r.setP(b.getP());
        r.setD(b.getD());
        r.setQ(b.getQ());
        r.setM(b.getM());
        r.setSeasonal(b.getSeasonal());
        return r;
    }

    /** 季节强度：各季节位置均值方差 / 总体方差。 */
    private double seasonalStrength(double[] v, int m) {
        if (v.length < 2 * m) {
            return 0.0;
        }
        double[] seasonMean = new double[m];
        int[] cnt = new int[m];
        for (int i = 0; i < v.length; i++) {
            seasonMean[i % m] += v[i];
            cnt[i % m]++;
        }
        for (int k = 0; k < m; k++) {
            seasonMean[k] /= Math.max(1, cnt[k]);
        }
        return var(seasonMean) / Math.max(1e-9, var(v));
    }

    // ---- 预测 ----

    private double[] predict(double[] series, ResolvedModel cfg, int h) {
        if ("holt-winters".equals(cfg.model)) {
            HoltWinters hw = new HoltWinters(series, cfg.m, cfg.type);
            hw.fit();
            cfg.params.put("alpha", round(hw.alpha()));
            cfg.params.put("beta", round(hw.beta()));
            cfg.params.put("gamma", round(hw.gamma()));
            cfg.params.put("type", cfg.type.name());
            cfg.params.put("period", cfg.m);
            return hw.forecast(h);
        }
        Arima arima = new Arima(series, cfg.p, cfg.d, cfg.q);
        arima.fit();
        double[] phi = arima.phi();
        double[] theta = arima.theta();
        cfg.params.put("p", cfg.p);
        cfg.params.put("d", cfg.d);
        cfg.params.put("q", cfg.q);
        cfg.params.put("phi", Arrays.toString(phi));
        cfg.params.put("theta", theta.length == 0 ? "[]" : Arrays.toString(theta));
        cfg.params.put("mu", round(arima.mu()));
        cfg.params.put("sigma", round(arima.sigma()));
        return arima.forecast(h);
    }

    // ---- 回测 ----

    private static class BacktestResult {
        List<Integer> steps;
        List<Double> perStepMape;
        List<Double> perStepRmse;
        List<Double> perStepMae;
        double overallMape;
        double overallRmse;
        double overallMae;
        double sigma;
        int windows;
        String note;
    }

    private BacktestResult backtestRaw(double[] v, ResolvedModel cfg, int h, int step) {
        int n = v.length;
        // 回测起点按模型区分：Holt-Winters 需要 >= 2 个完整周期才有意义；
        // ARIMA 本身无季节约束，固定从 8 个点起，避免“数据 < 2*m 却按 m 起算导致 0 窗口”的假完美 bug
        int start = "holt-winters".equals(cfg.model) ? Math.max(cfg.m * 2, 8) : 8;
        double[] stepMaeSum = new double[h];
        double[] stepRmsSum = new double[h];
        double[] stepMapSum = new double[h];
        int[] stepCnt = new int[h];
        double allErrAbs = 0, allErrSq = 0, allMap = 0;
        int allCnt = 0;
        int windows = 0;

        for (int s = start; s + h <= n; s += step) {
            if (windows >= MAX_WINDOWS) {
                break;
            }
            double[] train = Arrays.copyOfRange(v, 0, s);
            double[] actual = Arrays.copyOfRange(v, s, s + h);
            double[] pred;
            try {
                pred = predict(train, cfg, h);
            } catch (Exception e) {
                continue;
            }
            for (int k = 0; k < h; k++) {
                double e = actual[k] - pred[k];
                stepMaeSum[k] += Math.abs(e);
                stepRmsSum[k] += e * e;
                double denom = Math.abs(actual[k]);
                if (denom > 1e-9) {
                    stepMapSum[k] += Math.abs(e) / denom;
                }
                stepCnt[k]++;
                allErrAbs += Math.abs(e);
                allErrSq += e * e;
                if (denom > 1e-9) {
                    allMap += Math.abs(e) / denom;
                }
                allCnt++;
            }
            windows++;
        }

        BacktestResult bt = new BacktestResult();
        bt.steps = new ArrayList<>();
        bt.perStepMape = new ArrayList<>();
        bt.perStepRmse = new ArrayList<>();
        bt.perStepMae = new ArrayList<>();
        for (int k = 0; k < h; k++) {
            bt.steps.add(k + 1);
            int c = Math.max(1, stepCnt[k]);
            bt.perStepMae.add(round(stepMaeSum[k] / c));
            bt.perStepRmse.add(round(Math.sqrt(stepRmsSum[k] / c)));
            bt.perStepMape.add(round(stepMapSum[k] / c * 100.0));
        }
        bt.windows = windows;
        bt.overallMae = round(allErrAbs / Math.max(1, allCnt));
        bt.overallRmse = round(Math.sqrt(allErrSq / Math.max(1, allCnt)));
        bt.overallMape = round(allMap / Math.max(1, allCnt) * 100.0);
        double var = 0;
        if (allCnt > 1) {
            var = allErrSq / allCnt - Math.pow(allErrAbs / allCnt, 2);
        }
        bt.sigma = Math.sqrt(Math.max(0, var));

        // 兜底：当样本不足以形成任何外推窗口（如 n < start + h）时，
        // 用样本内一步拟合误差替代，避免前端显示误导性的“MAPE=0% 完美”
        if (windows == 0) {
            double[] ins = inSampleMetrics(v, cfg);
            if (ins != null) {
                bt.overallMape = round(ins[0]);
                bt.overallRmse = round(ins[1]);
                bt.overallMae = round(ins[2]);
                bt.sigma = ins[3];
                for (int k = 0; k < h; k++) {
                    bt.perStepMape.set(k, bt.overallMape);
                    bt.perStepRmse.set(k, bt.overallRmse);
                    bt.perStepMae.set(k, bt.overallMae);
                }
                bt.note = "样本不足，指标为样本内拟合误差（外推回测需 >= " + (start + h) + " 个点）";
            } else {
                bt.note = "样本量不足以评估（请增大数据量或减小外推步数）";
            }
        } else {
            bt.note = "回测窗口 " + windows + " 个";
        }
        return bt;
    }

    /** 样本内一步拟合误差（MAPE/RMSE/MAE/sigma）。无法拟合时返回 null。 */
    private double[] inSampleMetrics(double[] v, ResolvedModel cfg) {
        if ("holt-winters".equals(cfg.model)) {
            try {
                HoltWinters hw = new HoltWinters(v, cfg.m, cfg.type);
                hw.fit();
                double[] fitted = hw.fitted();
                int cnt = 0;
                double sAbs = 0, sSq = 0, sMap = 0;
                for (int t = cfg.m; t < v.length; t++) {
                    double e = v[t] - fitted[t];
                    sAbs += Math.abs(e);
                    sSq += e * e;
                    double d = Math.abs(v[t]);
                    if (d > 1e-9) {
                        sMap += Math.abs(e) / d;
                    }
                    cnt++;
                }
                if (cnt == 0) {
                    return null;
                }
                double mae = sAbs / cnt, rmse = Math.sqrt(sSq / cnt), mape = sMap / cnt * 100.0;
                double variance = sSq / cnt - Math.pow(sAbs / cnt, 2);
                return new double[]{mape, rmse, mae, Math.sqrt(Math.max(0, variance))};
            } catch (Exception e) {
                return null;
            }
        } else {
            try {
                Arima ar = new Arima(v, cfg.p, cfg.d, cfg.q);
                ar.fit();
                double[] res = ar.residuals();
                int maxLag = Math.max(cfg.p, cfg.q);
                int cnt = 0;
                double sAbs = 0, sSq = 0;
                double[] z = new TimeSeries(v).difference(cfg.d).toArray();
                double zMean = 0;
                for (double x : z) {
                    zMean += Math.abs(x);
                }
                zMean /= Math.max(1, z.length);
                for (int t = maxLag; t < res.length; t++) {
                    double e = res[t];
                    sAbs += Math.abs(e);
                    sSq += e * e;
                    cnt++;
                }
                if (cnt == 0) {
                    return null;
                }
                double mae = sAbs / cnt, rmse = Math.sqrt(sSq / cnt);
                // ARIMA 残差处于差分尺度，MAPE 用差分序列自身尺度作相对近似
                double mape = zMean > 1e-9 ? (sAbs / cnt) / zMean * 100.0 : 0.0;
                double variance = sSq / cnt - Math.pow(sAbs / cnt, 2);
                return new double[]{mape, rmse, mae, Math.sqrt(Math.max(0, variance))};
            } catch (Exception e) {
                return null;
            }
        }
    }

    // ---- 工具 ----

    private static double[] toArray(List<Double> list) {
        if (list == null) {
            throw new IllegalArgumentException("values 不能为空");
        }
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) {
            Double x = list.get(i);
            out[i] = (x == null) ? 0.0 : x;
        }
        return out;
    }

    private static List<Double> toList(double[] a) {
        List<Double> out = new ArrayList<>(a.length);
        for (double v : a) {
            out.add(v);
        }
        return out;
    }

    private static double mean(double[] a) {
        double s = 0;
        for (double v : a) {
            s += v;
        }
        return s / a.length;
    }

    private static double var(double[] a) {
        double m = mean(a);
        double s = 0;
        for (double v : a) {
            s += (v - m) * (v - m);
        }
        return s / a.length;
    }

    private static double round(double x) {
        return Math.round(x * 1000.0) / 1000.0;
    }
}
