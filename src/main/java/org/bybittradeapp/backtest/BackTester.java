package org.bybittradeapp.backtest;

import org.bybittradeapp.analysis.service.ExtremumService;
import org.bybittradeapp.analysis.service.ImbalanceService;
import org.bybittradeapp.analysis.service.TrendService;
import org.bybittradeapp.analysis.service.VolatilityService;
import org.bybittradeapp.backtest.domain.Account;
import org.bybittradeapp.backtest.service.ExchangeSimulator;
import org.bybittradeapp.backtest.service.Strategy;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;
import org.bybittradeapp.ui.domain.MarketKlineEntry;
import org.bybittradeapp.ui.utils.JsonUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.bybittradeapp.logging.Log.logProgress;
import static org.bybittradeapp.ui.utils.JsonUtils.updateMarketData;

/**
 * Для тестирования стратегии на предыдущих исторических данных.
 */
public class BackTester {
    private final Strategy strategy;
    private final TreeMap<Long, MarketEntry> marketData;
    private final ExchangeSimulator simulator;
    private final ImbalanceService imbalanceService;
    private final Account account;
    private final TreeMap<Long, MarketKlineEntry> uiMarketData;


    public BackTester(TreeMap<Long, MarketEntry> marketData, TreeMap<Long, MarketKlineEntry> uiMarketData) {
        this.marketData = marketData;
        this.uiMarketData = uiMarketData;
        this.account = new Account();
        this.simulator = new ExchangeSimulator(account);
        VolatilityService volatilityService = new VolatilityService();
        this.imbalanceService = new ImbalanceService(volatilityService);

        TrendService trendService = new TrendService(volatilityService);
        ExtremumService extremumService = new ExtremumService(marketData, volatilityService);
        this.strategy = new Strategy(simulator, marketData, imbalanceService, extremumService, trendService, account);
        updateMarketData(uiMarketData);
    }

    public void runTests() {
        AtomicLong step = new AtomicLong(0L);
        long startTime = Instant.now().toEpochMilli();
        long firstKey = marketData.firstKey();
        long lastKey = marketData.lastKey();
//        long firstKey = 1722816000000L;
//        long lastKey = 1722938400000L;

        Log.log(String.format("starting backtest with balance %.2f$", account.getBalance()));
        marketData
//                .subMap(firstKey, lastKey)
                .forEach((key, value) -> {
                    imbalanceService.onTick(key, value);
                    strategy.onTick(key, value);
                    simulator.onTick(key, value);

                    double progress = ((double) (key - firstKey)) / ((double) (lastKey - firstKey));
                    logProgress(startTime, step, progress, "backtest");
                });
        Log.log(String.format("backtest finished with balance %.2f$", account.getBalance()));

//        var uiMarketDataFiltered = uiMarketData.stream()
//                .filter(entry -> entry.getStartTime() >= firstKey - 5 * 60 * 1000L && entry.getStartTime() <= lastKey + 5 * 60 * 1000L)
//                .collect(Collectors.toList());
//        JsonUtils.updateMarketData(uiMarketDataFiltered);

        JsonUtils.updateAnalysedData(new ArrayList<>(), imbalanceService.getImbalances(), simulator.getPositions(), uiMarketData);
        JsonUtils.serializeAll(new ArrayList<>(), imbalanceService.getImbalances(), simulator.getPositions());
    }
}
