package org.tradeapp.analysis.service;

import org.tradeapp.logging.Log;
import org.tradeapp.marketdata.domain.MarketEntry;

import java.util.*;

import static org.tradeapp.marketdata.service.ExchangeRequestService.performBinanceVMarketDataRequest;

public class VolatilityService {

    /**
     * Период обновления волатильности и средней цены (1000мс * 60с * 60м * 24ч = 1 день)
     */
    private static final long UPDATE_TIME_PERIOD_MILLS = 24L * 60L * 60L * 1000L;
    private static final int VOLATILITY_CALCULATE_DAYS_COUNT = 5;
    private static final int AVERAGE_PRICE_CALCULATE_DAYS_COUNT = 5;

    private long lastUpdateTime = -1L;

    private final List<VolatilityListener> listeners = new ArrayList<>();

    public VolatilityService() {
        Log.info(String.format("""
                        imbalance parameters:
                            update time period :: %d hours
                            volatility calculation past time :: %d days
                            average price calculation past time :: %d days""",
                UPDATE_TIME_PERIOD_MILLS / 3_600_000L,
                VOLATILITY_CALCULATE_DAYS_COUNT,
                AVERAGE_PRICE_CALCULATE_DAYS_COUNT));
    }

    public void onTick(long currentTime, MarketEntry currentEntry) {
        if (currentTime - lastUpdateTime > UPDATE_TIME_PERIOD_MILLS) {
            double volatility = calculateVolatility(currentTime);
            double average = calculateAverage(currentTime);
            Log.debug(String.format("volatility=%.2f%% || average=%.2f$", volatility * 100, average), currentTime);
            listeners.forEach(listener -> listener.notify(volatility, average));
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

    public void subscribeAll(List<VolatilityListener> listeners_) {
        listeners_.forEach(this::subscribe);
    }

    public void unsubscribe(VolatilityListener listener) {
        listeners.remove(listener);
    }

    public void unsubscribeAll() {
        listeners.clear();
    }
}
