package org.bybittradeapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bybittradeapp.backtest.BackTester;
import org.bybittradeapp.marketdata.service.MarketDataService;

public class Main {
    public static final ObjectMapper mapper = new ObjectMapper();

    public static final int HISTORICAL_DATA_SIZE = 60;
    public static final String SYMBOL = "BTCUSDT";

    private static final MarketDataService marketDataService = new MarketDataService();
    private static final BackTester tester = new BackTester(marketDataService.getMarketData());

    public static void main(String[] args) {
        tester.runTests();
    }
}
