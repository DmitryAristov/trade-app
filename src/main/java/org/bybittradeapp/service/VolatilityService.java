package org.bybittradeapp.service;

import org.bybittradeapp.domain.MarketKlineEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class VolatilityService {


    public static double calculateVolatility(TreeMap<Long, MarketKlineEntry> marketData) {
        if (marketData == null || marketData.size() < 2) {
            throw new IllegalArgumentException("Not enough data to calculate volatility.");
        }

        List<Double> returns = new ArrayList<>();
        Double previousClose = null;

        for (Map.Entry<Long, MarketKlineEntry> entry : marketData.entrySet()) {
            MarketKlineEntry klineEntry = entry.getValue();
            if (previousClose != null) {
                double dailyReturn = (klineEntry.getClosePrice() - previousClose) / previousClose;
                returns.add(dailyReturn);
            }
            previousClose = klineEntry.getClosePrice();
        }

        // Calculate the mean of the returns
        double meanReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // Calculate the variance
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - meanReturn, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }
}
