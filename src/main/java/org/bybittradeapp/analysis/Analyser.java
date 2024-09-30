package org.bybittradeapp.analysis;

import org.bybittradeapp.analysis.service.ExtremumService;
import org.bybittradeapp.analysis.service.ImbalanceService;
import org.bybittradeapp.analysis.service.TrendService;
import org.bybittradeapp.analysis.service.VolatilityService;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;
import org.bybittradeapp.ui.domain.MarketKlineEntry;
import org.bybittradeapp.ui.utils.JsonUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.bybittradeapp.logging.Log.logProgress;
import static org.bybittradeapp.ui.utils.JsonUtils.updateMarketData;

/**
 * Класс для анализа работы технических инструментов
 */
public class Analyser {
    private final TreeMap<Long, MarketEntry> marketData;
    private final VolatilityService volatilityService;
    private final ImbalanceService imbalanceService;
    private final TrendService trendService;
    private final ExtremumService extremumService;
    private final TreeMap<Long, MarketKlineEntry> uiMarketData;

    public Analyser(TreeMap<Long, MarketEntry> marketData, TreeMap<Long, MarketKlineEntry> uiMarketData) {
        this.marketData = marketData;
        this.uiMarketData = uiMarketData;
        this.volatilityService = new VolatilityService();
        this.imbalanceService = new ImbalanceService(volatilityService);
        this.trendService = new TrendService(volatilityService);
        this.extremumService = new ExtremumService(marketData, volatilityService);
        updateMarketData(uiMarketData);
    }

    public void runAnalysis() {
        AtomicLong step = new AtomicLong(0L);
        long startTime = Instant.now().toEpochMilli();
        long firstKey = marketData.firstKey();
        long lastKey = marketData.lastKey();
//        long firstKey = 1722816000000L;
//        long lastKey = 1722938400000L;

        Log.log("starting analysis of technical instruments");
        marketData
//                .subMap(firstKey, lastKey)
                .forEach((key, value) -> {
                    imbalanceService.onTick(key, value);

                    double progress = ((double) (key - firstKey)) / ((double) (lastKey - firstKey));
                    logProgress(startTime, step, progress, "analysis");
                });
        Log.log("analysis of technical instruments finished");

//        var uiMarketDataFiltered = uiMarketData.stream()
//                .filter(entry -> entry.getStartTime() >= firstKey - 5 * 60 * 1000L && entry.getStartTime() <= lastKey + 5 * 60 * 1000L)
//                .collect(Collectors.toList());
//        JsonUtils.updateMarketData(uiMarketDataFiltered);

        JsonUtils.updateAnalysedData(new ArrayList<>(), imbalanceService.getImbalances(), new ArrayList<>(), uiMarketData);
        JsonUtils.serializeAll(new ArrayList<>(), imbalanceService.getImbalances(), new ArrayList<>());
    }
}
