package org.bybittradeapp.analysis.service;

//TODO(2) написать сервис определения тренда
public class TrendService {
    public enum Trend { UP, NEUTRAL, DOWN }

    private final VolatilityService volatilityService;

    public TrendService(VolatilityService volatilityService) {
        this.volatilityService = volatilityService;
    }

    public Trend getTrend(int fromElement) {
        return Trend.NEUTRAL;
    }
}
