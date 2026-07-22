package com.aiforecast.tsp.api;

import java.util.List;

/** 回测（滚动评估）响应。 */
public class BacktestResponse {

    private List<Integer> horizonSteps; // 1..h
    private List<Double> mape;          // 各步 MAPE(%)
    private List<Double> rmse;          // 各步 RMSE
    private List<Double> mae;           // 各步 MAE
    private Double overallMape;
    private Double overallRmse;
    private Double overallMae;
    private String model;
    private String note;

    public List<Integer> getHorizonSteps() {
        return horizonSteps;
    }

    public void setHorizonSteps(List<Integer> horizonSteps) {
        this.horizonSteps = horizonSteps;
    }

    public List<Double> getMape() {
        return mape;
    }

    public void setMape(List<Double> mape) {
        this.mape = mape;
    }

    public List<Double> getRmse() {
        return rmse;
    }

    public void setRmse(List<Double> rmse) {
        this.rmse = rmse;
    }

    public List<Double> getMae() {
        return mae;
    }

    public void setMae(List<Double> mae) {
        this.mae = mae;
    }

    public Double getOverallMape() {
        return overallMape;
    }

    public void setOverallMape(Double overallMape) {
        this.overallMape = overallMape;
    }

    public Double getOverallRmse() {
        return overallRmse;
    }

    public void setOverallRmse(Double overallRmse) {
        this.overallRmse = overallRmse;
    }

    public Double getOverallMae() {
        return overallMae;
    }

    public void setOverallMae(Double overallMae) {
        this.overallMae = overallMae;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
