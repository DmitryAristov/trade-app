package org.tradeapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tradeapp.backtest.BackTester;
import org.tradeapp.logging.Log;
import org.tradeapp.marketdata.domain.MarketEntry;
import org.tradeapp.marketdata.service.MarketDataLoader;
import org.tradeapp.marketdata.service.ExchangeRequestService;
import org.tradeapp.ui.domain.MarketKlineEntry;
import org.tradeapp.ui.utils.Serializer;

import java.util.TreeMap;

public class Main {
    public static final String PEZDA = """
            
            
                       -----------------------------------
                  ---------------------------------------------
              -----------------------------------------------------
            ----------                                     ----------
            ----------                                     ----------
              -----------------------------------------------------
                  ---------------------------------------------
                       -----------------------------------
            
            
            """;
    public static final ObjectMapper MAPPER = new ObjectMapper();
    public static final Log.Level LOG_LEVEL = Log.Level.INFO;

    public static final int HISTORICAL_DATA_SIZE = 60;
    public static final String SYMBOL = "BTCUSDT";

    /**
     * Офлайн мод. Если не нужно обновлять рыночные данные и UI данные или если нет сети.
     */
    public static final boolean SKIP_MARKET_DATA_UPDATE = false;

    public static final long UI_DATA_INTERVAL = 15 * 60_000L;
    public static final String UI_DATA_INTERVAL_STRING = "15m";
//TODO move parameters to main. Initialize services with dynamic params
    static {
        Log.info(PEZDA);
    }

    private static final MarketDataLoader<TreeMap<Long, MarketKlineEntry>> uiMarketDataLoader = new MarketDataLoader<>(
            new Serializer<>("/src/main/resources/ui-data/sub-data/"),
            UI_DATA_INTERVAL,
            ExchangeRequestService::performBinanceUiMarketDataRequest);
    private static final MarketDataLoader<TreeMap<Long, MarketEntry>> analyseMarketDataLoader = new MarketDataLoader<>(
            new Serializer<>("/src/main/resources/market-data/sub-data/"),
            1000L,
            ExchangeRequestService::performBinanceMarketDataRequest);


    private static final BackTester tester = new BackTester(analyseMarketDataLoader.getData(), uiMarketDataLoader.getData());
//    private static final Analyser analyser = new Analyser(analyseMarketDataLoader.getData());


    public static void main(String[] args) {
//        analyser.runAnalysis();
        tester.runTests();
    }
}
