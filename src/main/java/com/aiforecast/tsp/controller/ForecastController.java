package com.aiforecast.tsp.controller;

import com.aiforecast.tsp.api.BacktestRequest;
import com.aiforecast.tsp.api.BacktestResponse;
import com.aiforecast.tsp.api.ForecastRequest;
import com.aiforecast.tsp.api.ForecastResponse;
import com.aiforecast.tsp.service.ForecastService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ForecastController {

    private final ForecastService service = new ForecastService();

    @PostMapping("/forecast")
    public ForecastResponse forecast(@RequestBody ForecastRequest req) {
        return service.forecast(req);
    }

    @PostMapping("/backtest")
    public BacktestResponse backtest(@RequestBody BacktestRequest req) {
        return service.backtest(req);
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
