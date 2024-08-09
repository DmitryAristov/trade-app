package org.bybittradeapp.service;

import org.bybittradeapp.domain.Imbalance;
import org.bybittradeapp.domain.Trend;
import org.bybittradeapp.domain.Zone;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.bybittradeapp.Main.TEST_OPTION;

public class StrategyService {

    private final ImbalanceService imbalanceService;
    private final SupportResistanceService srService;
    private final TrendService trendService;
    private final MarketDataService marketDataService;

    public static List<Zone> zones;
    public static Imbalance imbalance;
    public static Set<Imbalance> imbalances = new HashSet<>();
    public static Trend trend;

    public StrategyService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
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
        for (int i = 0; i < marketDataService.getMarketData().size(); i++) {
            if (i % 1000 == 0)
                System.out.println("check market conditions number " + i);
            Imbalance imbalance = imbalanceService.getImbalance(i);
            if (imbalance != null) {
                System.out.println(imbalance);
                imbalances.add(imbalance);
            }
            trend = trendService.getTrend(i);
        }
    }
}
