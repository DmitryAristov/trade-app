package org.bybittradeapp.analysis;

import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.service.*;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;
import org.bybittradeapp.ui.domain.MarketKlineEntry;
import org.bybittradeapp.ui.utils.JsonUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.bybittradeapp.logging.Log.logProgress;

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
        this.imbalanceService = new ImbalanceService();
        this.trendService = new TrendService();
        this.extremumService = new ExtremumService();

        volatilityService.subscribeAll(List.of(this.imbalanceService, this.extremumService, this.trendService));
    }

    public void runAnalysis() {
        AtomicLong step = new AtomicLong(0L);
        long startTime = Instant.now().toEpochMilli();
        long firstKey = marketData.firstKey();
        long lastKey = marketData.lastKey();

        Log.log("starting analysis of technical instruments");
        marketData.forEach((key, marketEntry) -> {
                    volatilityService.onTick(key, marketEntry);
                    imbalanceService.onTick(key, marketEntry);

                    double progress = ((double) (key - firstKey)) / ((double) (lastKey - firstKey));
                    logProgress(startTime, step, progress, "analysis");
                });
        Log.log("analysis of technical instruments finished");

//        JsonUtils.updateAnalysedData(new ArrayList<>(), imbalanceService.getImbalances(), new ArrayList<>(), uiMarketData);
        JsonUtils.serializeAll(new ArrayList<>(), imbalanceService.getImbalances(), new ArrayList<>());

        imbalanceService.getImbalances().forEach(imbalance -> {
            updateUiData(imbalance);
            System.out.println(imbalance);
        });
    }

    private void updateUiData(Imbalance imbalance) {
        var timeDelay = 1000L * 60L * 60L;
        var secondsMarketKLineEntries = marketData
                .subMap(imbalance.getStartTime() - timeDelay, imbalance.getCompleteTime() + timeDelay)
                .entrySet()
                .stream()
                .map(entry -> {
                    var uiEntry = new MarketKlineEntry();
                    uiEntry.setStartTime(entry.getKey());
                    uiEntry.setLowPrice(entry.getValue().low());
                    uiEntry.setHighPrice(entry.getValue().high());
                    uiEntry.setOpenPrice((entry.getValue().low() + entry.getValue().high()) * 0.5);
                    uiEntry.setClosePrice((entry.getValue().low() + entry.getValue().high()) * 0.5);
                    return uiEntry;
                })
                .collect(Collectors.toMap(
                        MarketKlineEntry::getStartTime,
                        Function.identity(),
                        (first, second) -> first,
                        TreeMap::new
                ));

        JsonUtils.updateMarketData(secondsMarketKLineEntries);
        JsonUtils.updateAnalysedData(new ArrayList<>(), List.of(imbalance), new ArrayList<>(), secondsMarketKLineEntries);
    }
}
