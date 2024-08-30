package org.bybittradeapp;

import com.bybit.api.client.domain.market.MarketInterval;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.backtest.Tester;
import org.bybittradeapp.marketData.domain.MarketKlineEntry;
import org.bybittradeapp.analysis.domain.Extremum;
import org.bybittradeapp.analysis.service.ImbalanceService;
import org.bybittradeapp.marketData.service.MarketDataService;
import org.bybittradeapp.analysis.service.ExtremumService;
import org.bybittradeapp.ui.service.UIService;
import org.bybittradeapp.analysis.service.VolatilityService;
import org.bybittradeapp.ui.utils.JsonUtils;

import java.util.List;

public class Main {
    public static final ObjectMapper mapper = new ObjectMapper();

    // temporary service for UI purposes
    private static final UIService uiService = new UIService(MarketInterval.FOUR_HOURLY);

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
    private static final VolatilityService volatilityService = new VolatilityService();
    private static final MarketDataService marketDataService = new MarketDataService();

    // analysis classes
    private static final ImbalanceService imbalanceService = new ImbalanceService(marketDataService.getMarketData(), volatilityService);
    private static final ExtremumService extremumService = new ExtremumService(marketDataService.getMarketData(), volatilityService);

    private static final Tester tester = new Tester(marketDataService.getMarketData());

    public static void main(String[] args) {
        // update initial/historical data
        uiService.updateMarketData();
        marketDataService.updateMarketData();

        // analyse
        List<Extremum> extrema = extremumService.getExtremums();

        // calculate imbalances foreach historical data
        for (int i = 0; i < marketDataService.getMarketData().size(); i++) {
            if (i % 20000 == 0)
                System.out.println("check imb for i " + i);
            imbalanceService.getImbalance(i);
        }

        // calculate imbalances and zones
        List<Imbalance> imbalances = imbalanceService.getImbalances();
        List<MarketKlineEntry> uiMarketData = uiService.getMarketData();

        // update UI
        JsonUtils.updateAnalysedDataJson(extrema, imbalances, uiMarketData);


        // backtest
        tester.runTests();
    }
}
