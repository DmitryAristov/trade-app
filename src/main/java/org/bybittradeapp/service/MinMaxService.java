package org.bybittradeapp.service;

import org.bybittradeapp.domain.MarketKlineEntry;
import org.bybittradeapp.domain.Zone;

import java.util.ArrayList;
import java.util.List;

public class MinMaxService {

    private final MarketDataService marketDataService;
    private final List<Zone> zones = new ArrayList<>();

    public MinMaxService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public List<Zone> getZones() {
        ArrayList<MarketKlineEntry> marketData = marketDataService.getMarketData();

        // first step is get MA from marketData for last n elements

        // second step is to find min max on this MA

        // third step find the same min max points on marketData

        // done!
        return zones;
    }
}
