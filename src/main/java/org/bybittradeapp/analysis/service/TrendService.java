package org.bybittradeapp.analysis.service;

import org.bybittradeapp.marketData.domain.MarketKlineEntry;
import org.bybittradeapp.marketData.service.MarketDataService;

import java.util.List;

public class TrendService {
    public enum Trend { UP, NEUTRAL, DOWN }

    private final List<MarketKlineEntry> marketData;
    private final VolatilityService volatilityService;

    public TrendService(List<MarketKlineEntry> marketData, VolatilityService volatilityService) {
        this.marketData = marketData;
        this.volatilityService = volatilityService;
    }

    public Trend getTrend(int fromElement) {
        //TODO
        return Trend.NEUTRAL;
    }

    public Trend getTrend(MarketKlineEntry entry) {
        //TODO
        return Trend.NEUTRAL;
    }
}
