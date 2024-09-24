package org.bybittradeapp.analysis;

import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.domain.ImbalanceState;
import org.bybittradeapp.analysis.service.ExtremumService;
import org.bybittradeapp.analysis.service.ImbalanceService;
import org.bybittradeapp.analysis.service.TrendService;
import org.bybittradeapp.analysis.service.VolatilityService;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketKlineEntry;
import org.bybittradeapp.ui.utils.JsonUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.bybittradeapp.logging.Log.logProgress;

public class Analyser {
    private final TreeMap<Long, Double> marketData;
    private final VolatilityService volatilityService;
    private final ImbalanceService imbalanceService;
    private final TrendService trendService;
    private final ExtremumService extremumService;
    private final List<MarketKlineEntry> uiMarketData;

    public Analyser(TreeMap<Long, Double> marketData, List<MarketKlineEntry> uiMarketData) {
        this.marketData = marketData;
        this.uiMarketData = uiMarketData;
        this.volatilityService = new VolatilityService();
        this.imbalanceService = new ImbalanceService(volatilityService);
        this.trendService = new TrendService(volatilityService);
        this.extremumService = new ExtremumService(marketData, volatilityService);
    }

    public void runAnalysis() {
        AtomicLong step = new AtomicLong(0L);
        long startTime = Instant.now().toEpochMilli();
//        long firstKey = marketData.firstKey();
//        long lastKey = marketData.lastKey();
        long firstKey = 1726689600000L;
        long lastKey = 1726707600000L;

        Log.log("starting analysis of technical instruments");
        List<Imbalance> imbalances = new ArrayList<>();
        marketData.subMap(firstKey, lastKey).forEach((key, value) -> {

            imbalanceService.onTick(key, value);
            if (imbalanceService.getState() == ImbalanceState.IMBALANCE_COMPLETED && (imbalances.isEmpty() ||
                    !imbalances.get(imbalances.size() - 1).equals(imbalanceService.getImbalance()))) {
                imbalances.add(imbalanceService.getImbalance());
            }

            double progress = ((double) (key - firstKey)) / ((double) (lastKey - firstKey));
            logProgress(startTime, step, progress, "analysis");
        });
        Log.log("analysis of technical instruments finished");

        JsonUtils.updateAnalysedData(new ArrayList<>(), imbalances, new ArrayList<>(), uiMarketData);
        JsonUtils.serializeAll(new ArrayList<>(), imbalances, new ArrayList<>());
    }
}
