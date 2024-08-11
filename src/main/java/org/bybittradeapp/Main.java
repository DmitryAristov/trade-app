package org.bybittradeapp;

import com.bybit.api.client.domain.market.MarketInterval;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bybittradeapp.domain.Imbalance;
import org.bybittradeapp.domain.MarketKlineEntry;
import org.bybittradeapp.domain.Extremum;
import org.bybittradeapp.service.ImbalanceService;
import org.bybittradeapp.service.MarketDataService;
import org.bybittradeapp.service.ExtremumService;
import org.bybittradeapp.service.UIService;
import org.bybittradeapp.utils.JsonUtils;

import java.util.List;

public class Main {

    public static final int DAYS_TO_CHECK = 200;
    public static final ObjectMapper mapper = new ObjectMapper();

    private static final MarketDataService marketDataService = new MarketDataService();
    private static final UIService uiService = new UIService(MarketInterval.FOUR_HOURLY);
    private static final ImbalanceService imbalanceService = new ImbalanceService(marketDataService);
    private static final ExtremumService mmService = new ExtremumService(marketDataService);

    public static void main(String[] args) {
        uiService.updateMarketData();
        marketDataService.updateMarketData();

        List<Extremum> extrema = mmService.getExtremums();
        for (int i = 0; i < marketDataService.getMarketData().size(); i++) {
            if (i % 20000 == 0)
                System.out.println("check imb for i " + i);
            imbalanceService.getImbalance(i);
        }
        List<Imbalance> imbalances = imbalanceService.getImbalances();
        List<MarketKlineEntry> uiMarketData = uiService.getUiMarketData();

        JsonUtils.updateAnalysedDataJson(extrema, imbalances, uiMarketData);
    }
}
