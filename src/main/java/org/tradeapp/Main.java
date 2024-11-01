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

//TODO может быть что на бОльшем времени это уже возврат, тогда на текущем возврата может вообще не быть, или нужно реагировать гораздо медленнее (х2).
// Открываться только какую то часть времени от длины имбаланса.
// .
// .
// Проверка если цена уже вернулась до открытия то не открываться. (!!!!!!!!!!!!!) - DONE
// 3 тейка (!!!!!!!!!!!!1)
// Понизить границу взятия с 0.2 на поменьше. Протестировать значения
// .
// .
// Проверка скорости и валидности в процессе имбаланса, а не только при старте
// Собрать данные за все имбалансы за три года, собрать результаты calc и построить функцию(и) зависимости potentialCompleteTime от размера имбаланса по цене и времени
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

    public static final int HISTORICAL_DATA_SIZE = 1100;
    public static final String SYMBOL = "BTCUSDT";

    /**
     * Офлайн мод. Если не нужно обновлять рыночные данные и UI данные или если нет сети.
     */
    public static final boolean SKIP_MARKET_DATA_UPDATE = true;

    public static final long UI_DATA_INTERVAL = 15 * 60_000L;
    public static final String UI_DATA_INTERVAL_STRING = "15m";
//TODO move parameters to main. Initialize services with dynamic params
    static {
        Log.info(PEZDA);
    }

    private static final MarketDataLoader<TreeMap<Long, MarketKlineEntry>> uiMarketDataLoader = new MarketDataLoader<>(
            new Serializer<>("/src/main/resources/ui-data/"),
            UI_DATA_INTERVAL,
            ExchangeRequestService::performBinanceUiMarketDataRequest);
    private static final MarketDataLoader<TreeMap<Long, MarketEntry>> analyseMarketDataLoader = new MarketDataLoader<>(
            new Serializer<>("/src/main/resources/market-data/"),
            1000L,
            ExchangeRequestService::performBinanceMarketDataRequest);


    private static final BackTester tester = new BackTester(analyseMarketDataLoader.getData(), uiMarketDataLoader.getData());
//    private static final Analyser analyser = new Analyser(analyseMarketDataLoader.getData());


    public static void main(String[] args) {
//        analyser.runAnalysis();
        tester.runTests();
    }
}
