package org.tradeapp.analysis.service;

import org.tradeapp.marketdata.domain.MarketEntry;

import java.util.*;

import static org.tradeapp.backtest.constants.Constants.*;
import static org.tradeapp.marketdata.service.ExchangeRequestService.performBinanceVMarketDataRequest;

public class VolatilityService {

    private final List<VolatilityListener> listeners = new ArrayList<>();
    private long lastUpdateTime = -1L;

    public VolatilityService() {  }

    public void onTick(long currentTime, MarketEntry currentEntry) {
        if (currentTime - lastUpdateTime > UPDATE_TIME_PERIOD_MILLS) {
            double volatility = calculateVolatility(currentTime);
            double average = calculateAverage(currentTime);
            listeners.forEach(listener -> listener.notify(volatility, average, currentTime));
            lastUpdateTime = currentTime;
        }
    }

    /**
     * Метод определяет волатильность актива
     */
    private double calculateVolatility(long currentTime) {
        TreeMap<Long, MarketEntry> marketData = performBinanceVMarketDataRequest("15m",
                currentTime - VOLATILITY_CALCULATE_DAYS_COUNT * 24L * 60L * 60L * 1000L,
                VOLATILITY_CALCULATE_DAYS_COUNT * 24 * 4);

        if (marketData.size() < 2) {
            return 0.;
        }

        List<Double> changes = new ArrayList<>();
        for (MarketEntry marketDatum : marketData.values()) {
            double priceDiff = marketDatum.high() - marketDatum.low();
            double priceAverage = marketDatum.average();
            changes.add(priceDiff / priceAverage);
        }
        return changes.stream().reduce(0., Double::sum) / changes.size();
    }

    /**
     * Метод определяет среднюю цену актива
     */
    private double calculateAverage(long currentTime) {
        TreeMap<Long, MarketEntry> marketData = performBinanceVMarketDataRequest("15m",
                currentTime - AVERAGE_PRICE_CALCULATE_DAYS_COUNT * 24L * 60L * 60L * 1000L,
                AVERAGE_PRICE_CALCULATE_DAYS_COUNT * 24 * 4);

        if (marketData.size() < 2) {
            return 0.;
        }

        double sum = 0;
        for (MarketEntry marketDatum : marketData.values()) {
            double priceAverage = marketDatum.average();
            sum += priceAverage;
        }

        return sum / marketData.size();
    }


    public void subscribe(VolatilityListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unsubscribeAll() {
        listeners.clear();
    }
}
