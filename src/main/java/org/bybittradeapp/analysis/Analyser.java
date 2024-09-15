package org.bybittradeapp.analysis;

import com.bybit.api.client.domain.market.MarketInterval;
import org.bybittradeapp.analysis.domain.Extremum;
import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.domain.ImbalanceState;
import org.bybittradeapp.analysis.service.ExtremumService;
import org.bybittradeapp.analysis.service.ImbalanceService;
import org.bybittradeapp.analysis.service.TrendService;
import org.bybittradeapp.analysis.service.VolatilityService;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketKlineEntry;
import org.bybittradeapp.ui.service.UiDataService;
import org.bybittradeapp.ui.utils.JsonUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class Analyser {
    private final TreeMap<Long, Double> marketData;
    private final VolatilityService volatilityService;
    private final ImbalanceService imbalanceService;
    private final TrendService trendService;
    private final ExtremumService extremumService;
    private final UiDataService uiDataService;

    public Analyser(TreeMap<Long, Double> marketData) {
        this.marketData = marketData;
        this.volatilityService = new VolatilityService();
        this.imbalanceService = new ImbalanceService(marketData, volatilityService);
        this.trendService = new TrendService(marketData, volatilityService);
        this.extremumService = new ExtremumService(marketData, volatilityService);
        this.uiDataService = new UiDataService(MarketInterval.FOUR_HOURLY);

    }

    public void runAnalysis() {
        Log.log("starting analysis of technical instruments");
        List<Imbalance> imbalances = new ArrayList<>();
        marketData.forEach((key, value) -> {
            extremumService.onTick(key, value);

            imbalanceService.onTick(key, value);
            if (imbalanceService.getState() == ImbalanceState.COMPLETED && (imbalances.isEmpty() ||
                    !imbalances.get(imbalances.size() - 1).equals(imbalanceService.getImbalance()))) {
                imbalances.add(imbalanceService.getImbalance());
            }
        });
        List<Extremum> extrema = extremumService.getExtremums();

        List<MarketKlineEntry> uiMarketData = uiDataService.getMarketData();
        JsonUtils.updateAnalysedData(extrema, imbalances, new ArrayList<>(), uiMarketData);
    }
}
