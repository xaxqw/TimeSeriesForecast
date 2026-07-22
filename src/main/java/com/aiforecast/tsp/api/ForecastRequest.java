package com.aiforecast.tsp.api;

import java.util.List;
import java.util.Map;

/** 预测请求。 */
public class ForecastRequest {

    private List<Double> values;
    private String model = "auto";   // auto | arima | holt-winters
    private Integer p;
    private Integer d;
    private Integer q;
    private Integer m;               // 季节周期（Holt-Winters 用）
    private String seasonal = "additive"; // additive | multiplicative
    private Integer horizon = 12;

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
}
