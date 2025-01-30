package org.tradeapp;

import org.tradeapp.backtest.BackTester;
import org.tradeapp.marketdata.domain.MarketEntry;
import org.tradeapp.marketdata.service.FileMarketDataLoader;

import java.util.TreeMap;

import static org.tradeapp.backtest.constants.Constants.SYMBOL;

public class Main {

    public static final boolean SKIP_MARKET_DATA_UPDATE = false;
    private static final FileMarketDataLoader handler = new FileMarketDataLoader(System.getProperty("user.dir") + "/input/market-data/" + SYMBOL);

    public static void main(String[] args) {

        if (!SKIP_MARKET_DATA_UPDATE)
            handler.updateOrDownloadData();

        for (int year = 2017; year <= 2025; year++) {
            TreeMap<Long, MarketEntry> marketData = handler.readAllEntries(year);
            BackTester tester = new BackTester(marketData, null);
            tester.runTests();
        }
    }
}
