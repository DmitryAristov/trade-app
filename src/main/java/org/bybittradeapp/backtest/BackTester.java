package org.bybittradeapp.backtest;

import com.bybit.api.client.domain.market.MarketInterval;
import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.domain.ImbalanceState;
import org.bybittradeapp.analysis.service.ExtremumService;
import org.bybittradeapp.analysis.service.ImbalanceService;
import org.bybittradeapp.analysis.service.TrendService;
import org.bybittradeapp.analysis.service.VolatilityService;
import org.bybittradeapp.backtest.domain.Account;
import org.bybittradeapp.backtest.service.ExchangeSimulator;
import org.bybittradeapp.backtest.service.Strategy;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketKlineEntry;
import org.bybittradeapp.ui.service.UiDataService;
import org.bybittradeapp.ui.utils.JsonUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.bybittradeapp.logging.Log.logProgress;

public class BackTester {
    private final Strategy strategy;
    private final TreeMap<Long, Double> marketData;
    private final ExchangeSimulator simulator;
    private final ImbalanceService imbalanceService;
    private final Account account;
    private final UiDataService uiDataService;

    public BackTester(TreeMap<Long, Double> marketData) {
        this.marketData = marketData;
        this.account = new Account();
        this.simulator = new ExchangeSimulator(account);
        VolatilityService volatilityService = new VolatilityService();
        this.imbalanceService = new ImbalanceService(marketData, volatilityService);

        TrendService trendService = new TrendService(marketData, volatilityService);
        ExtremumService extremumService = new ExtremumService(marketData, volatilityService);
        this.strategy = new Strategy(simulator, marketData, imbalanceService, extremumService, trendService, account);
        this.uiDataService = new UiDataService(MarketInterval.FIVE_MINUTES);
    }

    public void runTests() {
        AtomicLong step = new AtomicLong(0L);
        long startTime = Instant.now().toEpochMilli();
        long firstKey = marketData.firstKey();
        long lastKey = marketData.lastKey();

        List<Imbalance> imbalances = new ArrayList<>();

        Log.log(String.format("starting backtest with balance %.2f$", account.getBalance()));
        marketData.forEach((key, value) -> {
            imbalanceService.onTick(key, value);
            if (imbalanceService.getState() == ImbalanceState.COMPLETED && (imbalances.isEmpty() ||
                    !imbalances.get(imbalances.size() - 1).equals(imbalanceService.getImbalance()))) {
                imbalances.add(imbalanceService.getImbalance());
            }
            strategy.onTick(key, value);
            simulator.onTick(key, value);

            double progress = ((double) (key - firstKey)) / ((double) (lastKey - firstKey));
            logProgress(startTime, step, progress, "backtest");
        });
        Log.log(String.format("backtest finished with balance %.2f$", account.getBalance()));

        List<MarketKlineEntry> uiMarketData = uiDataService.getMarketData();
        JsonUtils.updateAnalysedData(new ArrayList<>(), imbalances, simulator.getPositions(), uiMarketData);
        JsonUtils.serializeAll(new ArrayList<>(), imbalances, simulator.getPositions());
    }
}

