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
    private TreeMap<Long, MarketEntry> marketData;
    private TreeMap<Long, MarketKlineEntry> uiMarketData;
    private final VolatilityService volatilityService;
    private final ImbalanceService imbalanceService;
    private final TrendService trendService;
    private final ExtremumService extremumService;
    private final Serializer<List<TreeMap<Long, MarketEntry>>> serializer =
            new Serializer<>("/home/dmitriy/Projects/bybit-trade-app/src/main/resources/results/imbalances/market-data/");


    public Analyser(TreeMap<Long, MarketEntry> marketData, TreeMap<Long, MarketKlineEntry> uiMarketData) {
        this.marketData = marketData;
        this.uiMarketData = uiMarketData;
        this.volatilityService = new VolatilityService();
        this.imbalanceService = new ImbalanceService();
        this.imbalanceService.setMarketData(marketData);

        this.trendService = new TrendService();
        this.extremumService = new ExtremumService();

        volatilityService.subscribeAll(List.of(this.imbalanceService, this.extremumService, this.trendService));
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
        Log.info("analysis of technical instruments finished");

        JsonUtils.updateUiMarketData(uiMarketData);
        JsonUtils.updateAnalysedUiData(new ArrayList<>(), imbalanceService.getImbalances(), new ArrayList<>(), uiMarketData);
        JsonUtils.serializeAll(new ArrayList<>(), imbalanceService.getImbalances(), new ArrayList<>());

//        int nicePriceCounterFirst = 0;
//        int fastFallAfterEndPriceCounterFirst = 0;
//        int movingBackwardFromImbalanceCounterFirst = 0;
//
//        int nicePriceCounterSecond = 0;
//        int movingBackwardFromImbalanceCounterSecond = 0;
//        int fastFallAfterEndPriceCounterSecond = 0;
//
//        int nicePriceCounterThird = 0;
//        int movingBackwardFromImbalanceCounterThird = 0;
//        int fastFallAfterEndPriceCounterThird = 0;
//
//        int nicePriceCounterFourth = 0;
//        int movingBackwardFromImbalanceCounterFourth = 0;
//        int fastFallAfterEndPriceCounterFourth = 0;
//
//        for (ImbalanceService.EndPointCriterias criterias : imbalanceService.endPointCriterias) {
//            if (criterias.endPointCriteriaFirst.nicePrice) {
//                nicePriceCounterFirst++;
//            }
//            if (criterias.endPointCriteriaFirst.fastFallAfterEndPrice) {
//                fastFallAfterEndPriceCounterFirst++;
//            }
//            if (criterias.endPointCriteriaFirst.movingBackwardFromImbalance) {
//                movingBackwardFromImbalanceCounterFirst++;
//            }
//
//            if (criterias.endPointCriteriaSecond.nicePrice) {
//                nicePriceCounterSecond++;
//            }
//            if (criterias.endPointCriteriaSecond.fastFallAfterEndPrice) {
//                fastFallAfterEndPriceCounterSecond++;
//            }
//            if (criterias.endPointCriteriaSecond.movingBackwardFromImbalance) {
//                movingBackwardFromImbalanceCounterSecond++;
//            }
//
//            if (criterias.endPointCriteriaThird.nicePrice) {
//                nicePriceCounterThird++;
//            }
//            if (criterias.endPointCriteriaThird.fastFallAfterEndPrice) {
//                fastFallAfterEndPriceCounterThird++;
//            }
//            if (criterias.endPointCriteriaThird.movingBackwardFromImbalance) {
//                movingBackwardFromImbalanceCounterThird++;
//            }
//
//            if (criterias.endPointCriteriaFourth.nicePrice) {
//                nicePriceCounterFourth++;
//            }
//            if (criterias.endPointCriteriaFourth.fastFallAfterEndPrice) {
//                fastFallAfterEndPriceCounterFourth++;
//            }
//            if (criterias.endPointCriteriaFourth.movingBackwardFromImbalance) {
//                movingBackwardFromImbalanceCounterFourth++;
//            }
//        }
//        Log.log("first second after the end stats");
//        Log.log("nicePrice: " + nicePriceCounterFirst);
//        Log.log("fastFallAfterEndPrice: " + fastFallAfterEndPriceCounterFirst);
//        Log.log("movingBackwardFromImbalance: " + movingBackwardFromImbalanceCounterFirst);
//
//        Log.log("second second after the end stats");
//        Log.log("nicePrice: " + nicePriceCounterSecond);
//        Log.log("fastFallAfterEndPrice: " + fastFallAfterEndPriceCounterSecond);
//        Log.log("movingBackwardFromImbalance: " + movingBackwardFromImbalanceCounterSecond);
//
//        Log.log("third second after the end stats");
//        Log.log("nicePrice: " + nicePriceCounterThird);
//        Log.log("fastFallAfterEndPrice: " + fastFallAfterEndPriceCounterThird);
//        Log.log("movingBackwardFromImbalance: " + movingBackwardFromImbalanceCounterThird);
//
//        Log.log("fourth second after the end stats");
//        Log.log("nicePrice: " + nicePriceCounterFourth);
//        Log.log("fastFallAfterEndPrice: " + fastFallAfterEndPriceCounterFourth);
//        Log.log("movingBackwardFromImbalance: " + movingBackwardFromImbalanceCounterFourth);

//        List<TreeMap<Long, MarketEntry>> listMarketDatas = new ArrayList<>();
//        var timeDelay = 1000L * 60L;
//        imbalanceService.getImbalances().forEach(imbalance -> {
//            listMarketDatas.add(new TreeMap<>(marketData.subMap(imbalance.getStartTime() - timeDelay, imbalance.getEndTime() + timeDelay)));
//        });
//        serializer.serialize(listMarketDatas);

//        listMarketDatas = serializer.deserialize();
//        listMarketDatas.forEach(listMarketData -> {
//            JsonUtils.updateMarketData(listMarketData);
//            imbalanceService.resetData(listMarketData);
//
//            listMarketData.forEach((currentTime, currentEntry) -> {
//                volatilityService.onTick(currentTime, currentEntry);
//                imbalanceService.onTick(currentTime, currentEntry);
//            });
//
//            System.out.println("");
//        });
    }
}
