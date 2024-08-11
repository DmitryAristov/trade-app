package org.bybittradeapp.service;

import org.bybittradeapp.domain.MarketKlineEntry;
import org.bybittradeapp.domain.Zone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExtremumService {

    private static final long EXTREMUM_THRESHOLD = 18000000L;
    private final MarketDataService marketDataService;

    public ExtremumService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public List<Zone> getZones() {
        ArrayList<MarketKlineEntry> marketData = marketDataService.getMarketData();

        Map<Long, Double> average1000 = movingAverage(marketData, 1000);
        Map<Long, String> extremums1000 = findExtremums(average1000);

        Map<Long, Double> average600 = movingAverage(marketData, 600);
        Map<Long, String> extremums600 = findExtremums(average600);

        Map<Long, String> extremums600filtered = extremums600.entrySet().stream()
                .filter(extremum600 -> extremums1000.entrySet().stream().anyMatch(extremum1000 ->
                        Math.abs(extremum600.getKey() - extremum1000.getKey()) < EXTREMUM_THRESHOLD))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));

        Map<Long, Double> average250 = movingAverage(marketData, 250);
        Map<Long, String> extremums250 = findExtremums(average250);

        Map<Long, String> extremums250filtered = extremums250.entrySet().stream()
                .filter(extremum250 -> extremums600filtered.entrySet().stream().anyMatch(extremum600 ->
                        Math.abs(extremum250.getKey() - extremum600.getKey()) < EXTREMUM_THRESHOLD))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));

        Map<Long, Double> average100 = movingAverage(marketData, 100);
        Map<Long, String> extremums100 = findExtremums(average100);

        Map<Long, String> extremums100filtered = extremums100.entrySet().stream()
                .filter(extremum100 -> extremums250filtered.entrySet().stream().anyMatch(extremum250 ->
                        Math.abs(extremum100.getKey() - extremum250.getKey()) < EXTREMUM_THRESHOLD))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));

        List<Zone> zones = new ArrayList<>();

        extremums100filtered.forEach((key, value) -> {
            marketData.stream()
                    .filter(data -> data.getStartTime() == key)
                    .map(marketData::indexOf)
                    .findFirst()
                    .ifPresent(indexOfKey -> {
                        if ("Maximum".equals(value)) {
                            MarketKlineEntry max = marketData.get(indexOfKey - 300);
                            for (int i = Math.max(indexOfKey - 300, 0); i < Math.min(indexOfKey + 300, marketData.size() - 1); i++) {
                                if (marketData.get(i).getHighPrice() > max.getHighPrice()) {
                                    max = marketData.get(i);
                                }
                            }
                            zones.add(new Zone(max.getStartTime(), max.getHighPrice(), Zone.Type.SUPPORT));
                        }
                        if ("Minimum".equals(value)) {
                            MarketKlineEntry min = marketData.get(indexOfKey - 300);
                            for (int i = Math.max(indexOfKey - 300, 0); i < Math.min(indexOfKey + 300, marketData.size() - 1); i++) {
                                if (marketData.get(i).getLowPrice() > min.getLowPrice()) {
                                    min = marketData.get(i);
                                }
                            }
                            zones.add(new Zone(min.getStartTime(), min.getLowPrice(), Zone.Type.RESISTANCE));
                        }
                    });
        });
        return zones.stream().distinct().toList();
    }

    private Map<Long, Double> movingAverage(List<MarketKlineEntry> marketData, int pointCount) {
        if (pointCount <= 0 || pointCount > marketData.size()) {
            throw new IllegalArgumentException("Invalid pointCount: " + pointCount);
        }

        Map<Long, Double> result = new HashMap<>();
        double sum = 0;

        // Initialize the first window sum
        for (int i = 0; i < pointCount; i++) {
            sum += (marketData.get(i).getLowPrice() + marketData.get(i).getHighPrice()) / 2.0;
        }
        result.put(marketData.get(pointCount - 1).getStartTime(), sum / (double) pointCount);

        // Slide the window across the data
        for (int i = pointCount; i < marketData.size(); i++) {
            sum += (marketData.get(i).getLowPrice() + marketData.get(i).getHighPrice()) / 2.0;
            sum -= (marketData.get(i - pointCount).getLowPrice() + marketData.get(i - pointCount).getHighPrice()) / 2.0;
            result.put(marketData.get(i).getStartTime(), sum / (double) pointCount);
        }

        return result;
    }

    public static Map<Long, String> findExtremums(Map<Long, Double> maData) {
        Map<Long, String> extremums = new HashMap<>();

        // Convert the map to a sorted list of entries based on time (key)
        List<Map.Entry<Long, Double>> entryList = new ArrayList<>(maData.entrySet());
        entryList.sort(Map.Entry.comparingByKey());

        // TODO rewrite it to find only extremums with several left right values
        // Traverse the list to find local maxima and minima
        for (int i = 1; i < entryList.size() - 1; i++) {
            double prevValue = entryList.get(i - 1).getValue();
            double currentValue = entryList.get(i).getValue();
            double nextValue = entryList.get(i + 1).getValue();

            // Check for local maximum
            if (currentValue > prevValue && currentValue > nextValue) {
                extremums.put(entryList.get(i).getKey(), "Maximum");
            }
            // Check for local minimum
            else if (currentValue < prevValue && currentValue < nextValue) {
                extremums.put(entryList.get(i).getKey(), "Minimum");
            }
        }

        return extremums;
    }
}
