package org.bybittradeapp.service;

import org.bybittradeapp.domain.Trend;

public class TrendService {

    private final MarketDataService marketDataService;

    public TrendService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public Trend getTrend(int fromElement) {
        return null;
    }
}
