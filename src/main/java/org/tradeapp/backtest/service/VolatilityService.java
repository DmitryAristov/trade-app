package org.tradeapp.backtest.service;

import org.tradeapp.backtest.binance.APIService;
import org.tradeapp.backtest.domain.MarketEntry;
import org.tradeapp.utils.Log;

import java.util.*;

import static org.tradeapp.backtest.constants.Settings.*;


public class VolatilityService {

    private final Log log = new Log();
    private final APIService apiService;
    private final String symbol;

    private VolatilityListener callback;
    private long lastUpdateTime = -1L;

    public VolatilityService(String symbol, APIService apiService) {
        this.symbol = symbol;
        this.apiService = apiService;
    }

    public void onTick(long currentTime, MarketEntry currentEntry) {
        if (currentTime - lastUpdateTime > UPDATE_TIME_PERIOD_MILLS) {
            double volatility = calculateVolatility(currentTime);
            double average = calculateAverage(currentTime);
            callback.notify(volatility, average, currentTime);
            lastUpdateTime = currentTime;
        }
    }

    private double calculateVolatility(long currentTime) {
        log.debug("Calculating volatility...", currentTime);
        TreeMap<Long, MarketEntry> marketData = fetchMarketData("15m", VOLATILITY_CALCULATE_PAST_TIME_DAYS, currentTime);

        if (marketData.size() < 2) {
            log.warn("Insufficient market data for volatility calculation.", currentTime);
            return 0.0;
        }

        List<Double> changes = new ArrayList<>();
        for (MarketEntry marketDatum : marketData.values()) {
            double priceDiff = marketDatum.high() - marketDatum.low();
            double priceAverage = marketDatum.average();
            changes.add(priceDiff / priceAverage);
        }

        double volatility = changes.stream().reduce(0., Double::sum) / changes.size();
        log.debug(String.format("Calculated volatility: %.2f", volatility));
        return volatility;
    }

    private double calculateAverage(long currentTime) {
        log.debug("Calculating average price...", currentTime);
        TreeMap<Long, MarketEntry> marketData = fetchMarketData("15m", AVERAGE_PRICE_CALCULATE_PAST_TIME_DAYS, currentTime);

        if (marketData.size() < 2) {
            log.warn("Insufficient market data for average price calculation.", currentTime);
            return 0.0;
        }

        double sum = marketData.values().stream().mapToDouble(MarketEntry::average).sum();
        double average = sum / marketData.size();
        log.debug(String.format("Calculated average price: %.2f", average));
        return average;
    }

    private TreeMap<Long, MarketEntry> fetchMarketData(String interval, int days, long currentTime) {
        int requiredEntries = days * 24 * 60 / 15;
        long start = currentTime - days * 24L * 60L * 60L * 1000L;
        log.debug(String.format("Fetching market data for symbol: %s, interval: %s, required entries: %d", symbol, interval, requiredEntries));
        TreeMap<Long, MarketEntry> marketData = apiService.getMarketDataPublicAPI(symbol, interval,
                start, requiredEntries, currentTime).getResponse();
        log.debug(String.format("Fetched %d market entries.", marketData.size()));
        return marketData;
    }

    public void subscribe(VolatilityListener callback) {
        this.callback = callback;
    }
}
