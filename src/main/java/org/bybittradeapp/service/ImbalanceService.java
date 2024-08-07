package org.bybittradeapp.service;

import org.bybittradeapp.domain.Imbalance;
import org.bybittradeapp.domain.MarketKlineEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ImbalanceService {
    // TODO: implement volatility dependency
    private static final long SEARCH_PERIOD = 12L;
    private static final double BTC_PRICE_THRESHOLD = 4000.0;
    private final MarketDataService marketDataService;

    private Imbalance lastImbalance;

    public ImbalanceService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public Imbalance getImbalance(int fromElement) {
        TreeMap<Long, MarketKlineEntry> marketData =
                marketDataService.getMarketData()
                        .entrySet()
                        .stream()
                        .skip(fromElement)
                        .limit(SEARCH_PERIOD * 60L / marketDataService.getMarketInterval())
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (oldValue, newValue) -> oldValue,
                                TreeMap::new
                        ));
        Imbalance imbalance = getImbalance(marketData);
        if (lastImbalance == null ||
                (imbalance != null && !imbalance.same(lastImbalance))) {
            lastImbalance = imbalance;
        }

        setImbalanceStatus(lastImbalance);
        return lastImbalance;
    }

    private void setImbalanceStatus(Imbalance imbalance) {
        if (imbalance == null) {
            return;
        }

        // TODO check last N hours volatility (or last N hours not changed min/max).
        //  if it is lower then ... - FINISHED. Otherwise PROGRESS.
        imbalance.setStatus(Imbalance.Status.PROGRESS);
    }

    /**
     * Check if imbalance is present in provided prices map
     * @return Pair of MIN and MAX market entries which has difference greater then threshold
     */
    private @Nullable Imbalance getImbalance(@NotNull TreeMap<Long, MarketKlineEntry> map) {
        Optional<MarketKlineEntry> marketKlineEntryMaxPriceOpt = map.values().stream()
                .max(Comparator.comparing(MarketKlineEntry::getHighPrice));
        if (marketKlineEntryMaxPriceOpt.isEmpty()) {
            return null;
        }
        MarketKlineEntry marketKlineEntryMaxPrice = marketKlineEntryMaxPriceOpt.get();
        double maxPrice = marketKlineEntryMaxPrice.getHighPrice();

        Optional<MarketKlineEntry> marketKlineEntryMinPriceOpt = map.values().stream()
                .min(Comparator.comparing(MarketKlineEntry::getLowPrice));
        if (marketKlineEntryMinPriceOpt.isEmpty()) {
            return null;
        }
        MarketKlineEntry marketKlineEntryMinPrice = marketKlineEntryMinPriceOpt.get();
        double minPrice = marketKlineEntryMinPrice.getLowPrice();

        // Check if the price difference exceeds the threshold
        if (maxPrice - minPrice > BTC_PRICE_THRESHOLD) {
            Imbalance imbalance = new Imbalance(marketKlineEntryMinPrice, marketKlineEntryMaxPrice);
            if (marketKlineEntryMinPrice.getStartTime() > marketKlineEntryMaxPrice.getStartTime()) {
                imbalance.setType(Imbalance.Type.DOWN);
            } else {
                imbalance.setType(Imbalance.Type.UP);
            }
            System.out.println(imbalance);
            return imbalance;
        }
        return null;
    }
}
