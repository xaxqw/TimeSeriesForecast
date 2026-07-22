package com.aiforecast.tsp.api;

import java.util.List;

/** 回测（滚动评估）请求。 */
public class BacktestRequest {

    private List<Double> values;
    private String model = "auto";
    private Integer p;
    private Integer d;
    private Integer q;
    private Integer m;
    private String seasonal = "additive";
    private Integer horizon = 12;   // 每次外推步数
    private Integer step = 1;        // 滚动步长（1=逐点滚动）

    public List<Double> getValues() {
        return values;
    }

    public void setValues(List<Double> values) {
        this.values = values;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getP() {
        return p;
    }

    public void setP(Integer p) {
        this.p = p;
    }

    public Integer getD() {
        return d;
    }

    public void setD(Integer d) {
        this.d = d;
    }

    public Integer getQ() {
        return q;
    }

    public void setQ(Integer q) {
        this.q = q;
    }

    public Integer getM() {
        return m;
    }

    public void setM(Integer m) {
        this.m = m;
    }

    public String getSeasonal() {
        return seasonal;
    }

    public void setSeasonal(String seasonal) {
        this.seasonal = seasonal;
    }

    public Integer getHorizon() {
        return horizon;
    }

    public void setHorizon(Integer horizon) {
        this.horizon = horizon;
    }

    public Integer getStep() {
        return step;
    }

    public void setStep(Integer step) {
        this.step = step;
    }
}
