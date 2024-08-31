package org.bybittradeapp;

import com.bybit.api.client.domain.market.MarketInterval;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bybittradeapp.analysis.Analyser;
import org.bybittradeapp.backtest.BackTester;
import org.bybittradeapp.marketData.service.MarketDataService;
import org.bybittradeapp.ui.service.UiDataService;

public class Main {
    public static final ObjectMapper mapper = new ObjectMapper();

    // base settings
    public static final int HISTORICAL_DATA_SIZE = 60;
    public static final String SYMBOL = "BTCUSDT";
    // imbalance service settings
    public static final int IMBALANCE_SEARCH_TIME_WINDOW = 720; // 720 minutes = 12 hours
    public static final long IMBALANCE_COMPLETE_TIME = 3600000L; // 3600 seconds = 60 minutes = 1 hour
    public static final int IMBALANCE_PRICE_MODIFIER = 700; // min price change modifier (BTC: 700 * ~4.7 ~= 3300$)
    // extremum service settings
    public static final int MIN_MAX_DETECT_ON_MARKET_DATA_POINTS = 10 * 60;
    public static final int MIN_MAX_SEARCH_ON_MA_POINTS = 100;

    // initial historical data
    private static final MarketDataService marketDataService = new MarketDataService();
    private static final UiDataService uiDataService = new UiDataService(MarketInterval.FOUR_HOURLY);

    // backtest and analysis objects
    private static final BackTester tester = new BackTester(marketDataService.getMarketData());
    private static final Analyser analyser = new Analyser(marketDataService.getMarketData(), uiDataService);

    public static void main(String[] args) {
        uiDataService.updateMarketData();
        marketDataService.updateMarketData();

        analyser.runAnalysis();
        tester.runTests();
    }
}
