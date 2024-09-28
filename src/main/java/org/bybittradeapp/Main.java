package org.bybittradeapp;

import com.bybit.api.client.domain.market.MarketInterval;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bybittradeapp.analysis.Analyser;
import org.bybittradeapp.backtest.BackTester;
import org.bybittradeapp.marketdata.service.MarketDataService;
import org.bybittradeapp.ui.service.UiDataService;

public class Main {
    public static final ObjectMapper mapper = new ObjectMapper();

    public static final int HISTORICAL_DATA_SIZE = 120;
    public static final String SYMBOL = "BTCUSDT";
    public static final boolean SKIP_MARKET_DATA_UPDATE = true;

    private static final MarketDataService marketDataService = new MarketDataService();
    private static final UiDataService uiDataService = new UiDataService(MarketInterval.THREE_MINUTES);

    private static final BackTester tester = new BackTester(marketDataService.getMarketData(), uiDataService.getMarketData());
//    private static final Analyser analyser = new Analyser(marketDataService.getMarketData(), uiDataService.getMarketData());

    public static void main(String[] args) {
//        analyser.runAnalysis();
        tester.runTests();
    }
}
