package org.bybittradeapp.analysis;

import org.bybittradeapp.analysis.service.*;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;
import org.bybittradeapp.ui.domain.MarketKlineEntry;
import org.bybittradeapp.ui.utils.JsonUtils;
import org.bybittradeapp.ui.utils.Serializer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.bybittradeapp.logging.Log.logProgress;

/**
 * Класс для анализа работы технических инструментов
 */
public class Analyser {
    private final TreeMap<Long, MarketEntry> marketData;
    private final TreeMap<Long, MarketKlineEntry> uiMarketData;
    private final VolatilityService volatilityService;
    private final ImbalanceService imbalanceService;
    private final Serializer<List<TreeMap<Long, MarketEntry>>> serializer =
            new Serializer<>("/src/main/resources/results/imbalances/market-data/");

    public Analyser(TreeMap<Long, MarketEntry> marketData, TreeMap<Long, MarketKlineEntry> uiMarketData) {
        this.marketData = marketData;
        this.uiMarketData = uiMarketData;
        this.volatilityService = new VolatilityService();
        this.imbalanceService = new ImbalanceService();

        volatilityService.subscribe(this.imbalanceService);
    }

    public void runAnalysis() {
        AtomicLong step = new AtomicLong(0L);
        long startTime = Instant.now().toEpochMilli();
        long firstKey = marketData.firstKey();
        long lastKey = marketData.lastKey();

        Log.info("starting analysis of technical instruments");
        marketData.forEach((currentTime, currentEntry) -> {
            volatilityService.onTick(currentTime, currentEntry);
            imbalanceService.onTick(currentTime, currentEntry);

            double progress = ((double) (currentTime - firstKey)) / ((double) (lastKey - firstKey));
            logProgress(startTime, step, progress, "analysis");
        });
        volatilityService.unsubscribeAll();
        Log.info("analysis of technical instruments finished");

        JsonUtils.updateUiMarketData(uiMarketData);
        JsonUtils.updateAnalysedUiData(new ArrayList<>(), imbalanceService.getImbalances(), new ArrayList<>(), uiMarketData);
        JsonUtils.serializeAll(new ArrayList<>(), imbalanceService.getImbalances(), new ArrayList<>());
    }
}
