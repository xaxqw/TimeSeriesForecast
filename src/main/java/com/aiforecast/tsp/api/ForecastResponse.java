package com.aiforecast.tsp.api;

import java.util.List;
import java.util.Map;

/** 预测响应。 */
public class ForecastResponse {

    private List<Double> history;
    private List<Double> forecast;
    private List<Double> lower;
    private List<Double> upper;
    private String model;
    private Map<String, Object> params;
    private Double mape;
    private Double rmse;
    private Double mae;
    private String note;

    public List<Double> getHistory() {
        return history;
    }

    public void setHistory(List<Double> history) {
        this.history = history;
    }

    public List<Double> getForecast() {
        return forecast;
    }

    public void setForecast(List<Double> forecast) {
        this.forecast = forecast;
    }

    public List<Double> getLower() {
        return lower;
    }

    public void setLower(List<Double> lower) {
        this.lower = lower;
    }

    public List<Double> getUpper() {
        return upper;
    }

    public void setUpper(List<Double> upper) {
        this.upper = upper;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Double getMape() {
        return mape;
    }

    public void setMape(Double mape) {
        this.mape = mape;
    }

    public Double getRmse() {
        return rmse;
    }

    public void setRmse(Double rmse) {
        this.rmse = rmse;
    }

    public Double getMae() {
        return mae;
    }

    public void setMae(Double mae) {
        this.mae = mae;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
