package org.bybittradeapp.backtest;

import org.bybittradeapp.analysis.service.ExtremumService;
import org.bybittradeapp.analysis.service.ImbalanceService;
import org.bybittradeapp.analysis.service.TrendService;
import org.bybittradeapp.analysis.service.VolatilityService;
import org.bybittradeapp.backtest.domain.Account;
import org.bybittradeapp.backtest.service.ExchangeSimulator;
import org.bybittradeapp.backtest.service.Strategy;
import org.bybittradeapp.marketData.domain.MarketKlineEntry;

import java.util.List;

public class BackTester {
    private final Strategy strategy;
    private final List<MarketKlineEntry> marketData;
    private final ExchangeSimulator simulator;
    private final Account account;

    public BackTester(List<MarketKlineEntry> marketData) {
        this.marketData = marketData;
        this.account = new Account(100000, 50.0, 3);
        this.simulator = new ExchangeSimulator(account);
        VolatilityService volatilityService = new VolatilityService();
        ImbalanceService imbalanceService = new ImbalanceService(marketData, volatilityService);
        TrendService trendService = new TrendService(marketData, volatilityService);
        ExtremumService extremumService = new ExtremumService(marketData, volatilityService);
        this.strategy = new Strategy(simulator, marketData, imbalanceService, extremumService, trendService);
    }

    public void runTests() {
        System.out.println("Starting backtest");
        double progressStep = marketData.size() / 20.;
        double currentProgress = marketData.size() / 20.;
        for (int i = 0; i < marketData.size(); i++) {
            if (i > currentProgress) {
                System.out.println("check backtest for " + i + " of " + marketData.size());
                currentProgress += progressStep;
            }
            simulator.onTick(marketData.get(i));
            strategy.onTick(marketData.get(i));
        }
        System.out.println("Final account balance: " + account.getBalance());
    }
}

