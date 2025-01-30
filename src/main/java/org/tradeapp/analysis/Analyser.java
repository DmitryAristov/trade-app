package org.tradeapp.analysis;

import org.tradeapp.analysis.service.*;
import org.tradeapp.logging.Log;
import org.tradeapp.marketdata.domain.MarketEntry;
import org.tradeapp.ui.utils.TradingVueJsonUpdater;

import java.time.Instant;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Класс для анализа работы технических инструментов
 */
public class Analyser {
    private final Log log = new Log();

    private final TreeMap<Long, MarketEntry> marketData;
    private final VolatilityService volatilityService;
    private final ImbalanceService imbalanceService;

    public Analyser(TreeMap<Long, MarketEntry> marketData) {
        this.marketData = marketData;
        this.volatilityService = new VolatilityService();
        this.imbalanceService = new ImbalanceService();
        imbalanceService.setData(marketData);

        volatilityService.subscribe(this.imbalanceService);
    }

    public void runAnalysis() {
        AtomicLong step = new AtomicLong(0L);
        long startTime = Instant.now().toEpochMilli();
        long firstKey = marketData.firstKey();
        long lastKey = marketData.lastKey();

        log.info("starting analysis of technical instruments", firstKey);
        try {
            marketData.forEach((currentTime, currentEntry) -> {
                volatilityService.onTick(currentTime, currentEntry);
                imbalanceService.onTick(currentTime, currentEntry);

                double progress = ((double) (currentTime - firstKey)) / ((double) (lastKey - firstKey));
                log.logProgress(startTime, step, progress, "analysis", currentTime);
            });
            volatilityService.unsubscribeAll();
        } catch (Exception e) {
            log.error("", e, lastKey);
        } finally {
            TradingVueJsonUpdater.serializeAll(imbalanceService.getImbalances(), new ArrayList<>());
        }
        log.info("analysis of technical instruments finished", lastKey);
    }
}
