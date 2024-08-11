package org.bybittradeapp.service;

import org.bybittradeapp.domain.MarketKlineEntry;
import org.bybittradeapp.domain.Extremum;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ExtremumService {

    private static final int DETECT_LENGTH = 10 * 60;
    private static final double MIN_PRICE_DIFF = 1000.;

    private final MarketDataService marketDataService;

    public ExtremumService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    /**
     * Calculates list of extremums for all historical market data
     */
    public List<Extremum> getExtremums() {
        ArrayList<MarketKlineEntry> marketData = marketDataService.getMarketData();

        /*
         * First extremums we get from MA(700). It can find all necessary extremums,
         * but they all have wrong timestamp. Tio fix it, we need to map founded extremums on lower MAs
         * with several steps.
         */
        Map<Long, Double> average = movingAverage(marketData, 600);
        List<Extremum> extremums = findExtremums(average, 100);
        System.out.println("Initially found " + extremums.size() + " extremums");


        List<Extremum> extremums500 = mapExtremumsToNewMa(marketData, extremums, 500, 100);
        List<Extremum> extremums400 = mapExtremumsToNewMa(marketData, extremums500, 400, 100);
        List<Extremum> extremums300 = mapExtremumsToNewMa(marketData, extremums400, 300, 100);
        List<Extremum> extremums200 = mapExtremumsToNewMa(marketData, extremums300, 200, 50);
        List<Extremum> extremums100 = mapExtremumsToNewMa(marketData, extremums200, 100, 50);
        System.out.println("After all mappings we have " + extremums100.size() + " extremums");

        List<Extremum> extrema = new ArrayList<>();

        /*
         * When the latest MA with all extremums is ready, we need to map extremums on real market data (not MA)
         * This part of code detects all possible extremums which founded on MA
         */
        extremums100.stream()
                .filter(Objects::nonNull)
                .forEach(extremum -> marketData.stream()
                        .filter(data -> data.getStartTime() == extremum.getTimestamp())
                        .map(marketData::indexOf)
                        .findFirst()
                        .ifPresent(indexOfKey -> {
                            if (extremum.getType() == Extremum.Type.MAX) {
                                MarketKlineEntry max = marketData.get(Math.max(indexOfKey - DETECT_LENGTH, 0));
                                for (int i = Math.max(indexOfKey - DETECT_LENGTH, 0); i < Math.min(indexOfKey + DETECT_LENGTH, marketData.size() - 1); i++) {
                                    if (marketData.get(i).getHighPrice() > max.getHighPrice()) {
                                        max = marketData.get(i);
                                    }
                                }
                                if (!extrema.contains(new Extremum(max.getStartTime(), max.getHighPrice(), Extremum.Type.MAX))) {
                                    extrema.add(new Extremum(max.getStartTime(), max.getHighPrice(), Extremum.Type.MAX));
                                }
                            }
                            if (extremum.getType() == Extremum.Type.MIN) {
                                MarketKlineEntry min = marketData.get(Math.max(indexOfKey - DETECT_LENGTH, 0));
                                for (int i = Math.max(indexOfKey - DETECT_LENGTH, 0); i < Math.min(indexOfKey + DETECT_LENGTH, marketData.size() - 1); i++) {
                                    if (marketData.get(i).getLowPrice() < min.getLowPrice()) {
                                        min = marketData.get(i);
                                    }
                                }
                                if (!extrema.contains(new Extremum(min.getStartTime(), min.getLowPrice(), Extremum.Type.MIN))) {
                                    extrema.add(new Extremum(min.getStartTime(), min.getLowPrice(), Extremum.Type.MIN));
                                }
                            }
                        }));

        /*
         * First we need to make sure that extremums are sorted by time.
         */
        extrema.sort(Comparator.comparing(Extremum::getTimestamp));
        System.out.println("After mapping to market data we have " + extremums100.size() + " extremums");

        System.out.println("Start filtering...");

        removeSeveralOneTypeExtremums(extrema);
        removeMinHigherThanMax(marketData, extrema);
        removeExtremumsByPriceThreshold(extrema);
        removeSeveralOneTypeExtremums(extrema);
        removeMinHigherThanMax(marketData, extrema);
        correctExtremumsIfThereOneIsBetween(marketData, extrema);

        List<Extremum> result = extrema.stream().distinct().toList();
        System.out.println("Found " + result.size() + " extremums (" + extrema.size() + ")");

        return result;
    }

    /**
     * In some cases between MIN and MAX we have MIN (or MAX) which is lower (or higher) than founded one
     * In this case we need to update its price and timestamp to the right ones.
     */
    private static void correctExtremumsIfThereOneIsBetween(ArrayList<MarketKlineEntry> marketData, List<Extremum> extrema) {

        for (int i = 1; i < extrema.size(); i++) {
            if (extrema.get(i - 1).getType() == extrema.get(i).getType()) {
                continue;
            }
            final Extremum prev = extrema.get(i - 1);
            final Extremum curr = extrema.get(i);
            final Extremum max = prev.getType() == Extremum.Type.MAX ? prev : curr;
            final Extremum min = prev.getType() == Extremum.Type.MIN ? prev : curr;

            List<MarketKlineEntry> marketDataBetweenMinMax = marketData.stream()
                    .filter(data -> data.getStartTime() > prev.getTimestamp() && data.getStartTime() < curr.getTimestamp())
                    .toList();

            Optional<MarketKlineEntry> lowerThanMin = marketDataBetweenMinMax.stream()
                    .filter(data -> data.getLowPrice() < min.getPrice())
                    .max(Comparator.comparing(data -> min.getPrice() - data.getLowPrice()));

            Optional<MarketKlineEntry> higherThanMax = marketDataBetweenMinMax.stream()
                    .filter(data -> data.getHighPrice() > max.getPrice())
                    .max(Comparator.comparing(data -> data.getHighPrice() - max.getPrice()));

            if (lowerThanMin.isPresent()) {
                min.setTimestamp(lowerThanMin.get().getStartTime());
                min.setPrice(lowerThanMin.get().getLowPrice());
                System.out.println("MIN corrected");
            }
            if (higherThanMax.isPresent()) {
                max.setTimestamp(higherThanMax.get().getStartTime());
                max.setPrice(higherThanMax.get().getHighPrice());
                System.out.println("MAX corrected");
            }
        }
        System.out.println("Extremums size: " + extrema.size());
    }

    /**
     * This step we remove maximums and minimums that has not enough minimum price difference,
     * Example we convert { MAX (100), MIN (90), MAX (91), MIN (60) } -> { MAX (100), MIN (60) }
     */
    private static void removeExtremumsByPriceThreshold(List<Extremum> extrema) {
        List<Extremum> toRemove;

        do {
            toRemove = new ArrayList<>();
            for (int i = 3; i <= extrema.size(); i++) {
                if (Math.abs(extrema.get(i - 1).getPrice() - extrema.get(i - 2).getPrice()) < MIN_PRICE_DIFF) {
                    toRemove.add(extrema.get(i - 2));
                    if (extrema.get(i - 1).getType() == Extremum.Type.MAX) {
                        if (extrema.get(i - 1).getPrice() > extrema.get(i - 3).getPrice()) {
                            toRemove.add(extrema.get(i - 3));
                        } else {
                            toRemove.add(extrema.get(i - 1));
                        }
                    } else {
                        if (extrema.get(i - 1).getPrice() < extrema.get(i - 3).getPrice()) {
                            toRemove.add(extrema.get(i - 3));
                        } else {
                            toRemove.add(extrema.get(i - 1));
                        }
                    }
                }
            }
            System.out.println("Removed " + toRemove.size() + " nearly extremums (price diff < " + MIN_PRICE_DIFF);
            extrema.removeAll(toRemove);
        } while (!toRemove.isEmpty());
        System.out.println("Extremums size: " + extrema.size());
    }

    /**
     * This step we remove maximum < minimum and minimum > maximum pairs
     * Example we convert { MAX (100), MIN (90), MAX (80), MIN (70) } -> { MAX (100), MIN (70) }
     */
    private static void removeMinHigherThanMax(List<MarketKlineEntry> marketData, List<Extremum> extrema) {
        List<Extremum> toRemove;

        do {
            toRemove = new ArrayList<>();
            for (int i = 1; i < extrema.size() - 1; i++) {
                final Extremum prev = extrema.get(i - 1);
                final Extremum curr = extrema.get(i);
                final Extremum next = extrema.get(i + 1);
                if (prev.getType() == Extremum.Type.MAX) {
                    if (curr.getPrice() > prev.getPrice()) {
                        Optional<MarketKlineEntry> minBetweenPrevNext = marketData.stream()
                                .filter(data -> data.getStartTime() > prev.getTimestamp() && data.getStartTime() < next.getTimestamp())
                                .min(Comparator.comparing(MarketKlineEntry::getLowPrice));

                        if (minBetweenPrevNext.isPresent() &&
                                minBetweenPrevNext.get().getLowPrice() < prev.getPrice()) {
                            curr.setPrice(minBetweenPrevNext.get().getLowPrice());
                            curr.setTimestamp(minBetweenPrevNext.get().getStartTime());
                            System.out.println("MIN > MAX corrected");
                        } else {
                            toRemove.add(prev);
                            toRemove.add(curr);
                        }
                    }
                } else {
                    if (curr.getPrice() < prev.getPrice()) {
                        Optional<MarketKlineEntry> maxBetweenPrevNext = marketData.stream()
                                .filter(data -> data.getStartTime() > prev.getTimestamp() && data.getStartTime() < next.getTimestamp())
                                .max(Comparator.comparing(MarketKlineEntry::getLowPrice));

                        if (maxBetweenPrevNext.isPresent() &&
                                maxBetweenPrevNext.get().getHighPrice() < prev.getPrice()) {
                            curr.setPrice(maxBetweenPrevNext.get().getHighPrice());
                            curr.setTimestamp(maxBetweenPrevNext.get().getStartTime());
                            System.out.println("MAX < MIN corrected");
                        } else {
                            toRemove.add(prev);
                            toRemove.add(curr);
                        }
                    }
                }
            }
            System.out.println("Removed " + toRemove.size() + " MIN > MAX rows");
            extrema.removeAll(toRemove);
        } while (!toRemove.isEmpty());
        System.out.println("Extremums size: " + extrema.size());
    }

    /**
     * This step we remove one type extremums which are goes one by one (only MAX or MIN),
     * Example we convert { MAX (100), MAX (80), MAX (130), MIN (60) } -> { MAX (130), MIN (60) }
     */
    private static void removeSeveralOneTypeExtremums(List<Extremum> extrema) {
        List<Extremum> toRemove;
        do {
            toRemove = new ArrayList<>();
            for (int i = 2; i <= extrema.size(); i++) {
                if (extrema.get(i - 1).getType() == Extremum.Type.MAX &&
                        extrema.get(i - 2).getType() == Extremum.Type.MAX) {
                    if (extrema.get(i - 1).getPrice() > extrema.get(i - 2).getPrice()) {
                        toRemove.add(extrema.get(i - 2));
                    } else {
                        toRemove.add(extrema.get(i - 1));
                    }
                }
                if (extrema.get(i - 1).getType() == Extremum.Type.MIN &&
                        extrema.get(i - 2).getType() == Extremum.Type.MIN) {
                    if (extrema.get(i - 1).getPrice() < extrema.get(i - 2).getPrice()) {
                        toRemove.add(extrema.get(i - 2));
                    } else {
                        toRemove.add(extrema.get(i - 1));
                    }
                }
            }
            System.out.println("Removed " + toRemove.size() + " MAX,MAX... rows");
            extrema.removeAll(toRemove);
        } while (!toRemove.isEmpty());
        System.out.println("Extremums size: " + extrema.size());
    }

    @NotNull
    private List<Extremum> mapExtremumsToNewMa(ArrayList<MarketKlineEntry> marketData, List<Extremum> extremumsIn,
                                               int maPointCount, int avPointCount) {
        Map<Long, Double> average = movingAverage(marketData, maPointCount);
        List<Extremum> extremums = findExtremums(average, avPointCount);

        return extremumsIn.stream().map(extremumIn -> extremums.stream()
                        .min(Comparator.comparing(extremum ->
                                Math.abs(extremum.getTimestamp() - extremumIn.getTimestamp())))
                        .orElse(null))
                .toList();
    }

    /**
     * Method calculates moving average (MA) based on market data and average window size
     */
    private Map<Long, Double> movingAverage(List<MarketKlineEntry> marketData, int pointCount) {
        if (pointCount <= 0 || pointCount > marketData.size()) {
            throw new IllegalArgumentException("Invalid pointCount: " + pointCount);
        }

        Map<Long, Double> result = new HashMap<>();
        double sum = 0;

        /*
         * Initialize the first window sum
         */
        for (int i = 0; i < pointCount; i++) {
            sum += (marketData.get(i).getLowPrice() + marketData.get(i).getHighPrice()) / 2.0;
        }
        result.put(marketData.get(pointCount - 1).getStartTime(), sum / (double) pointCount);

        /*
         * Slide the window across the data (to avoid inner redundant foreach loop)
         */
        for (int i = pointCount; i < marketData.size(); i++) {
            sum += (marketData.get(i).getLowPrice() + marketData.get(i).getHighPrice()) / 2.0;
            sum -= (marketData.get(i - pointCount).getLowPrice() + marketData.get(i - pointCount).getHighPrice()) / 2.0;
            result.put(marketData.get(i).getStartTime(), sum / (double) pointCount);
        }

        return result;
    }

    public static List<Extremum> findExtremums(Map<Long, Double> maData, int pointCount) {
        ArrayList<Extremum> extremums = new ArrayList<>();

        List<Map.Entry<Long, Double>> entryList = new ArrayList<>(maData.entrySet());
        entryList.sort(Map.Entry.comparingByKey());

        int size = entryList.size();

        for (int i = pointCount; i < size - pointCount; i++) {
            double value = entryList.get(i).getValue();
            long key = entryList.get(i).getKey();
            boolean isMaximum = true;
            boolean isMinimum = true;

            for (int j = 1; j <= pointCount; j++) {
                double prevValue = entryList.get(i - j).getValue();
                double nextValue = entryList.get(i + j).getValue();

                if (value <= prevValue || value <= nextValue) {
                    isMaximum = false;
                }

                if (value >= prevValue || value >= nextValue) {
                    isMinimum = false;
                }

                if (!isMaximum && !isMinimum) {
                    break;
                }
            }

            if (isMaximum) {
                extremums.add(new Extremum(key, value, Extremum.Type.MAX));
            } else if (isMinimum) {
                extremums.add(new Extremum(key, value, Extremum.Type.MIN));
            }
        }
        return extremums;
    }
}
