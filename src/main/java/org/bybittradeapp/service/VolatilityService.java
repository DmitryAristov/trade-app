package org.bybittradeapp.service;

import org.bybittradeapp.domain.MarketKlineEntry;

import java.util.LinkedList;

public class VolatilityService {


    public static double calculateVolatility(LinkedList<MarketKlineEntry> marketData) {
        if (marketData == null || marketData.size() < 2) {
            return 0.;
        }

        return 0.;
    }
}
