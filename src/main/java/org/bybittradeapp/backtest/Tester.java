package org.bybittradeapp.backtest;

import org.bybittradeapp.analysis.service.ExtremumService;
import org.bybittradeapp.analysis.service.ImbalanceService;
import org.bybittradeapp.analysis.service.VolatilityService;
import org.bybittradeapp.backtest.domain.Account;
import org.bybittradeapp.backtest.service.ExchangeSimulator;
import org.bybittradeapp.backtest.service.Strategy;
import org.bybittradeapp.marketData.domain.MarketKlineEntry;

import java.util.List;

public class Tester {
    private final Strategy strategy;
    private final List<MarketKlineEntry> marketData;
    private final ExchangeSimulator simulator;
    private final Account account;

    public Tester(List<MarketKlineEntry> marketData) {
        this.marketData = marketData;
        this.account = new Account(100000, 10.0);
        this.simulator = new ExchangeSimulator(account);
        VolatilityService volatilityService = new VolatilityService();
        ImbalanceService imbalanceService = new ImbalanceService(marketData, volatilityService);
        ExtremumService extremumService = new ExtremumService(marketData, volatilityService);
        this.strategy = new Strategy(simulator, marketData, imbalanceService, extremumService);
    }

    public void runTests() {
        System.out.println("Starting backtest");
        for (MarketKlineEntry entry : marketData) {
            simulator.onTick(entry);
            strategy.onTick(entry);
        }
        System.out.println("Final account balance: " + account.getBalance());
    }
}

