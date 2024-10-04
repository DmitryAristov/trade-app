package org.bybittradeapp;

import com.bybit.api.client.domain.market.MarketInterval;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bybittradeapp.backtest.BackTester;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;
import org.bybittradeapp.marketdata.service.MarketDataLoader;
import org.bybittradeapp.marketdata.service.ExchangeRequestService;
import org.bybittradeapp.ui.domain.MarketKlineEntry;
import org.bybittradeapp.ui.utils.Serializer;

import java.util.TreeMap;

public class Main {
    public static String PEZDA = "\n" + "\n" + "\n" + "           -----------------------------------\n" + "      ---------------------------------------------\n" + "  -----------------------------------------------------\n" + "----------                                     ----------\n" + "----------                                     ----------\n" + "  -----------------------------------------------------\n" + "      ---------------------------------------------\n" + "           -----------------------------------\n" + "\n" + "\n";
    public static final ObjectMapper MAPPER = new ObjectMapper();
    public static final Log.Level LOG_LEVEL = Log.Level.INFO;

    public static final int HISTORICAL_DATA_SIZE = 360;
    public static final String SYMBOL = "BTCUSDT";

    /**
     * Офлайн мод. Если не нужно обновлять рыночные данные и UI данные или если нет сети.
     */
    public static final boolean SKIP_MARKET_DATA_UPDATE = true;
    public static final MarketInterval UI_DATA_INTERVAL = MarketInterval.HALF_HOURLY;
    static {
        Log.info(PEZDA);
    }

    private static final MarketDataLoader<TreeMap<Long, MarketKlineEntry>> uiMarketDataLoader = new MarketDataLoader<>(
            new Serializer<>("/home/dmitriy/Projects/bybit-trade-app/src/main/resources/ui-data/"),
            ExchangeRequestService.toMills(UI_DATA_INTERVAL),
            ExchangeRequestService::performBybitMarketDataRequest);
    private static final MarketDataLoader<TreeMap<Long, MarketEntry>> analyseMarketDataLoader = new MarketDataLoader<>(
            new Serializer<>("/home/dmitriy/Projects/bybit-trade-app/src/main/resources/market-data/"),
            1000L,
            ExchangeRequestService::performBinanceMarketDataRequest);


    private static final BackTester tester = new BackTester(analyseMarketDataLoader.getData(), uiMarketDataLoader.getData());
//    private static final Analyser analyser = new Analyser(analyseMarketDataLoader.getData(), uiMarketDataLoader.getData());


    public static void main(String[] args) {
//        analyser.runAnalysis();
        tester.runTests();
    }
}
