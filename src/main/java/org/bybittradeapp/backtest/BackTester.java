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
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.bybittradeapp.logging.Log.logProgress;

/**
 * Для тестирования стратегии на предыдущих исторических данных.
 */
public class BackTester {
    private final ExchangeSimulator simulator;
    private final Strategy strategy;
    private final Account account;

    private final TreeMap<Long, MarketEntry> marketData;
    private final TreeMap<Long, MarketKlineEntry> uiMarketData;

    private final VolatilityService volatilityService;
    private final ImbalanceService imbalanceService;
    private final TrendService trendService;
    private final ExtremumService extremumService;

    public BackTester(TreeMap<Long, MarketEntry> marketData, TreeMap<Long, MarketKlineEntry> uiMarketData) {
        this.marketData = marketData;
        this.uiMarketData = uiMarketData;
        this.account = new Account();
        this.simulator = new ExchangeSimulator(account);
        this.volatilityService = new VolatilityService();
        this.imbalanceService = new ImbalanceService();
        this.imbalanceService.setMarketData(marketData);

        this.trendService = new TrendService();
        this.extremumService = new ExtremumService();

        volatilityService.subscribeAll(List.of(this.imbalanceService, this.extremumService, this.trendService));

        this.strategy = new Strategy(simulator, marketData, imbalanceService, extremumService, trendService, account);
    }

    public void runTests() {
        AtomicLong step = new AtomicLong(0L);
        long startTime = Instant.now().toEpochMilli();
        long firstKey = marketData.firstKey();
        long lastKey = marketData.lastKey();

        Log.info(String.format("starting backtest with balance %.2f$", account.getBalance()));
        marketData.forEach((currentTime, currentEntry) -> {
            volatilityService.onTick(currentTime, currentEntry);
            imbalanceService.onTick(currentTime, currentEntry);
            strategy.onTick(currentTime, currentEntry);
            simulator.onTick(currentTime, currentEntry);

            double progress = ((double) (currentTime - firstKey)) / ((double) (lastKey - firstKey));
            logProgress(startTime, step, progress, "backtest");
        });
        Log.info(String.format("backtest finished with balance %.2f$", account.getBalance()));

        JsonUtils.updateUiMarketData(uiMarketData);
        JsonUtils.updateAnalysedUiData(new ArrayList<>(), imbalanceService.getImbalances(), simulator.getPositions(), uiMarketData);
        JsonUtils.serializeAll(new ArrayList<>(), imbalanceService.getImbalances(), simulator.getPositions());
    }
}
