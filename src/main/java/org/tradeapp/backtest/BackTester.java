package org.tradeapp.backtest;

import org.tradeapp.analysis.service.*;
import org.tradeapp.backtest.domain.Account;
import org.tradeapp.backtest.service.ExchangeSimulator;
import org.tradeapp.backtest.service.Strategy;
import org.tradeapp.logging.Log;
import org.tradeapp.marketdata.domain.MarketEntry;
import org.tradeapp.ui.domain.MarketKlineEntry;
import org.tradeapp.ui.utils.Serializer;
import org.tradeapp.ui.utils.TradingVueJsonUpdater;

import java.time.Instant;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.tradeapp.logging.Log.logProgress;

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

    public BackTester(TreeMap<Long, MarketEntry> marketData, TreeMap<Long, MarketKlineEntry> uiMarketData) {
        this.marketData = marketData;
        this.account = new Account();
        this.simulator = new ExchangeSimulator(account);
        this.volatilityService = new VolatilityService();
        this.imbalanceService = new ImbalanceService();
        imbalanceService.setData(marketData);

        volatilityService.subscribe(this.imbalanceService);

        this.strategy = new Strategy(simulator, marketData, uiMarketData, imbalanceService, account);
    }

    public void runTests() {
        AtomicLong step = new AtomicLong(0L);
        long startTime = Instant.now().toEpochMilli();
        long firstKey = marketData.firstKey();//1666775517000
        long lastKey = marketData.lastKey();//1688132229000
//        long firstKey = 1666675517000L;
//        long lastKey = 1688032229000L;

        Log.info(String.format("starting backtest with balance %.2f$", account.getBalance()));
        try {
            marketData
//                    .subMap(firstKey, lastKey)
                    .forEach((currentTime, currentEntry) -> {
                        volatilityService.onTick(currentTime, currentEntry);
                        imbalanceService.onTick(currentTime, currentEntry);
                        strategy.onTick(currentTime, currentEntry);
                        simulator.onTick(currentTime, currentEntry);

                        double progress = ((double) (currentTime - firstKey)) / ((double) (lastKey - firstKey));
                        logProgress(startTime, step, progress, "backtest");
                    });
            volatilityService.unsubscribeAll();
        } catch (Exception e) {
            Log.debug(e);
        } finally {
            TradingVueJsonUpdater.serializeAll(imbalanceService.getImbalances(), simulator.getPositions());
//            Serializer<TreeMap<Long, MarketEntry>> serializer = new Serializer<>("/src/main/resources/market-data/sub-data/");
//            serializer.serialize(new TreeMap<>(marketData.subMap(firstKey, lastKey)));
        }
        Log.info(String.format("backtest finished with balance %.2f$", account.getBalance()));
    }
}
