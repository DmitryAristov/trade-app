package org.bybittradeapp.backtest;

import org.bybittradeapp.analysis.service.*;
import org.bybittradeapp.backtest.domain.Account;
import org.bybittradeapp.backtest.service.ExchangeSimulator;
import org.bybittradeapp.backtest.service.Strategy;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;
import org.bybittradeapp.ui.utils.JsonUtils;

import java.time.Instant;
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

    private final VolatilityService volatilityService;
    private final ImbalanceService imbalanceService;

    public BackTester(TreeMap<Long, MarketEntry> marketData) {
        this.marketData = marketData;
        this.account = new Account();
        this.simulator = new ExchangeSimulator(account);
        this.volatilityService = new VolatilityService();
        this.imbalanceService = new ImbalanceService();

        volatilityService.subscribe(this.imbalanceService);

        this.strategy = new Strategy(simulator, marketData, imbalanceService, account);
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
        volatilityService.unsubscribeAll();
        Log.info(String.format("backtest finished with balance %.2f$", account.getBalance()));

//        JsonUtils.updateMarketData(marketData);
//        JsonUtils.updateAnalysedData(imbalanceService.getImbalances(), simulator.getPositions());
        JsonUtils.serializeAll(imbalanceService.getImbalances(), simulator.getPositions());
    }
}
