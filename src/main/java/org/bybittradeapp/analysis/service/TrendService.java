package org.bybittradeapp.analysis.service;

import java.util.TreeMap;

public class TrendService {
    public enum Trend { UP, NEUTRAL, DOWN }

    private final VolatilityService volatilityService;

    public TrendService(VolatilityService volatilityService) {
        this.volatilityService = volatilityService;
    }

    public Trend getTrend(int fromElement) {
        //TODO
        return Trend.NEUTRAL;
    }
}
