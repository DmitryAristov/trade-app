package org.bybittradeapp.service;

import org.bybittradeapp.domain.Imbalance;
import org.bybittradeapp.domain.MarketKlineEntry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ImbalanceService {
    // TODO: implement volatility dependency
    private static final int SEARCH_PERIOD = 720;
    private static final double BTC_PRICE_THRESHOLD = 4000.0;
    private static final long IMBALANCE_COMPLETE_THRESHOLD = 3600000L;
    private final MarketDataService marketDataService;
    private final ArrayList<Imbalance> imbalances = new ArrayList<>();

    public ImbalanceService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public void getImbalance(int fromElement) {
        if (marketDataService.getMarketData().size() < fromElement + SEARCH_PERIOD) {
            return;
        }
        List<MarketKlineEntry> marketData = marketDataService.getMarketData()
                .subList(fromElement, fromElement + SEARCH_PERIOD);
        checkImbalance(marketData);
    }

    private void checkImbalance(@NotNull List<MarketKlineEntry> marketData) {
        MarketKlineEntry maxPrice = marketData.stream()
                .max(Comparator.comparing(MarketKlineEntry::getHighPrice))
                .orElse(null);

        MarketKlineEntry minPrice = marketData.stream()
                .min(Comparator.comparing(MarketKlineEntry::getLowPrice))
                .orElse(null);
        if (maxPrice == null || minPrice == null) {
            return;
        }

        if (!imbalances.isEmpty() && imbalances.get(imbalances.size() - 1).getStatus() == Imbalance.Status.PROGRESS) {

            if (imbalances.get(imbalances.size() - 1).getType() == Imbalance.Type.UP) {
                if (maxPrice.getHighPrice() > imbalances.get(imbalances.size() - 1).getMax().getHighPrice()) {
                    imbalances.get(imbalances.size() - 1).setMax(maxPrice);
                }
                if (marketData.get(imbalances.size() - 1).getStartTime() - imbalances.get(imbalances.size() - 1).getMax().getStartTime() > IMBALANCE_COMPLETE_THRESHOLD) {
                    imbalances.get(imbalances.size() - 1).setStatus(Imbalance.Status.COMPLETE);
                }
            } else {
                if (minPrice.getLowPrice() < imbalances.get(imbalances.size() - 1).getMin().getLowPrice()) {
                    imbalances.get(imbalances.size() - 1).setMin(minPrice);
                }
                if (marketData.get(imbalances.size() - 1).getStartTime() - imbalances.get(imbalances.size() - 1).getMin().getStartTime() > IMBALANCE_COMPLETE_THRESHOLD) {
                    imbalances.get(imbalances.size() - 1).setStatus(Imbalance.Status.COMPLETE);
                }
            }

        } else {
            if (!imbalances.isEmpty() && imbalances.get(imbalances.size() - 1).getStatus() == Imbalance.Status.COMPLETE &&
                    ((imbalances.get(imbalances.size() - 1).getType() == Imbalance.Type.UP &&
                            marketData.get(0).getStartTime() < imbalances.get(imbalances.size() - 1).getMax().getStartTime())
                            ||
                            (imbalances.get(imbalances.size() - 1).getType() == Imbalance.Type.DOWN &&
                                    marketData.get(0).getStartTime() < imbalances.get(imbalances.size() - 1).getMin().getStartTime()))
            ) {
                return;
            }

            if (maxPrice.getHighPrice() - minPrice.getLowPrice() > BTC_PRICE_THRESHOLD) {
                Imbalance imbalance = new Imbalance(minPrice, maxPrice);
                imbalance.setStatus(Imbalance.Status.PROGRESS);
                if (minPrice.getStartTime() > maxPrice.getStartTime()) {
                    imbalance.setType(Imbalance.Type.DOWN);
                } else {
                    imbalance.setType(Imbalance.Type.UP);
                }
                imbalances.add(imbalance);
            }
        }
    }

    public List<Imbalance> getImbalances() {
        return imbalances;
    }
}
