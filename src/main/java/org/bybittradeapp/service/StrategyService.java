package org.bybittradeapp.service;

import org.bybittradeapp.domain.Imbalance;
import org.bybittradeapp.domain.Trend;
import org.bybittradeapp.domain.Zone;

import java.util.List;

import static org.bybittradeapp.Main.TEST_OPTION;

public class StrategyService {

    private final ImbalanceService imbalanceService;
    private final SupportResistanceService srService;
    private final TrendService trendService;

    private static List<Zone> zones;
    private static Imbalance imbalance;
    private static Trend trend;

    public StrategyService(MarketDataService marketDataService) {
        imbalanceService = new ImbalanceService(marketDataService);
        srService = new SupportResistanceService();
        trendService = new TrendService(marketDataService);
    }

    public void performCheck() {
        if (TEST_OPTION) {
            checkMarketConditionsTest();
        } else {
            checkMarketConditions();
        }
    }

    /**
     * Check if imbalance continues, update the same imbalance and setup progress status
     * if imbalance is finished (2-3 hours if it is not update min/max) - change imbalance status to finished
     */
    private void checkMarketConditions() {
        imbalance = imbalanceService.getImbalance(0);
        zones = srService.getZones();
        trend = trendService.getTrend(0);




        // after all gets, check strategy
    }

    /**
     * regression test for all period of market data
     * TODO implement trade simulation
     */
    private void checkMarketConditionsTest() {
        zones = srService.getZones();
//        for (int i = 0; i < marketDataService.getMarketData().size(); i++) {
//            System.out.println("check market conditions number " + i);
//            imbalance = imbalanceService.getImbalance(i);
//            trend = trendService.getTrend(i);
//
//        }
    }
}
