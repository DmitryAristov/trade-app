package org.bybittradeapp.analysis.service;

import java.util.TreeMap;

public class TrendService {
    public enum Trend { UP, NEUTRAL, DOWN }

    private final TreeMap<Long, Double> marketData;
    private final VolatilityService volatilityService;

    public TrendService(TreeMap<Long, Double> marketData, VolatilityService volatilityService) {
        this.marketData = marketData;
        this.volatilityService = volatilityService;
    }

    public Trend getTrend(int fromElement) {
        //TODO
        return Trend.NEUTRAL;
    }
}
