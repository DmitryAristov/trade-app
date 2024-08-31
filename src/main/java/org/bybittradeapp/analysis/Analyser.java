package org.bybittradeapp.analysis;

import org.bybittradeapp.analysis.domain.Extremum;
import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.service.ExtremumService;
import org.bybittradeapp.analysis.service.ImbalanceService;
import org.bybittradeapp.analysis.service.TrendService;
import org.bybittradeapp.analysis.service.VolatilityService;
import org.bybittradeapp.marketData.domain.MarketKlineEntry;
import org.bybittradeapp.ui.service.UiDataService;
import org.bybittradeapp.ui.utils.JsonUtils;

import java.util.List;

public class Analyser {
    private final List<MarketKlineEntry> marketData;
    private final VolatilityService volatilityService;
    private final ImbalanceService imbalanceService;
    private final TrendService trendService;
    private final ExtremumService extremumService;
    private final UiDataService uiDataService;

    public Analyser(List<MarketKlineEntry> marketData, UiDataService uiDataService) {
        this.marketData = marketData;
        this.volatilityService = new VolatilityService();
        this.imbalanceService = new ImbalanceService(marketData, volatilityService);
        this.trendService = new TrendService(marketData, volatilityService);
        this.extremumService = new ExtremumService(marketData, volatilityService);
        this.uiDataService = uiDataService;
    }

    public void runAnalysis() {
        System.out.println("Starting analysis of technical instruments");

        List<Extremum> extrema = extremumService.getExtremums();
        double progressStep = marketData.size() / 20.;
        double currentProgress = marketData.size() / 20.;
        for (int i = 0; i < marketData.size(); i++) {
            if (i > currentProgress) {
                System.out.println("check imbalance for " + i + " of " + marketData.size());
                currentProgress += progressStep;
            }
            imbalanceService.getImbalance(i);
        }

        //TODO
        // check trend

        List<Imbalance> imbalances = imbalanceService.getImbalances();
        List<MarketKlineEntry> uiMarketData = uiDataService.getMarketData();

        // update UI data
        JsonUtils.updateAnalysedDataJson(extrema, imbalances, uiMarketData);
    }
}
