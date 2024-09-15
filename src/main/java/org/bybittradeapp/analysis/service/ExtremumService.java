package org.bybittradeapp.analysis.service;

import org.bybittradeapp.analysis.domain.Extremum;
import org.bybittradeapp.backtest.service.Tickle;
import org.bybittradeapp.logging.Log;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ExtremumService implements Tickle {
    public static final long MIN_MAX_DETECT_ON_MARKET_DATA_TIME_WINDOW = 10L * 60L * 60L * 1000L;
    public static final long MIN_MAX_SEARCH_ON_MA_TIME_WINDOW = 100L * 60L * 1000L ;
    public static final long SEARCH_TIME_WINDOW = 60L * 24L * 60L * 60L * 1000L; // 60 seconds * 60 minutes * 24 hours * 60 days
    public static final double MIN_DIFF_BETWEEN_EXTREMUMS = 0.1;

    private final long initialMaTimeWindow;
    private final double minPriceDiff;
    private TreeMap<Long, Double> data;
    private final TreeMap<Long, Double> marketData;

    public ExtremumService(TreeMap<Long, Double> marketData, VolatilityService volatilityService) {
        this.marketData = marketData;
        this.initialMaTimeWindow = Math.round(volatilityService.getVolatility() * 120L * 60L * 1000L);
        this.minPriceDiff = volatilityService.getVolatility() * volatilityService.getAverage() * MIN_DIFF_BETWEEN_EXTREMUMS;
    }

    @Override
    public void onTick(long time, double price) {
//        updateData(time, price);
    }

    private void updateData(long time, double price) {
        // All market data. Need to filter SEARCH_TIME_WINDOW values only
        if (data == null) {
            data = new TreeMap<>(marketData.subMap(time - SEARCH_TIME_WINDOW, true, time, true));
        } else {
            data.put(time, price);
            data.entrySet().removeIf(entry -> time - entry.getKey() > SEARCH_TIME_WINDOW);
        }
    }

    /**
     * Calculates list of extremums for all historical market data
     */
    public List<Extremum> getExtremums() {

        /*
         * First extremums we get from MA(700). It can find all necessary extremums,
         * but they all have wrong timestamp. Tio fix it, we need to map founded extremums on lower MAs
         * with several steps.
         */
        TreeMap<Long, Double> average = movingAverage(marketData, initialMaTimeWindow);
        List<Extremum> extremums = findExtremums(average);
        Log.log("initially found " + extremums.size() + " extremums");

        final long secondCount = Math.round(initialMaTimeWindow * 0.8);
        List<Extremum> extremums500 = mapExtremumsToNewMa(marketData, extremums, secondCount);
        final long thirdCount = Math.round(initialMaTimeWindow * 0.6);
        List<Extremum> extremums400 = mapExtremumsToNewMa(marketData, extremums500, thirdCount);
        final long foursCount = Math.round(initialMaTimeWindow * 0.4);
        List<Extremum> extremums300 = mapExtremumsToNewMa(marketData, extremums400, foursCount);
        final long fifthCount = Math.round(initialMaTimeWindow * 0.25);
        List<Extremum> extremums200 = mapExtremumsToNewMa(marketData, extremums300, fifthCount);
        final long sixthCount = Math.round(initialMaTimeWindow * 0.15);
        List<Extremum> extremums100 = mapExtremumsToNewMa(marketData, extremums200, sixthCount);
        Log.log("after all mappings we have " + extremums100.size() + " extremums");

        Set<Extremum> extrema = new HashSet<>();

        /*
         * When the latest MA with all extremums is ready, we need to map extremums on real market data (not MA)
         * This part of code detects all possible extremums which founded on MA
         */
        extremums100.stream()
                .filter(Objects::nonNull)
                .forEach(extremum -> marketData.entrySet().stream()
                        .min(Comparator.comparing(entry -> entry.getKey() == extremum.getTimestamp()))
                        .ifPresent(entry -> {
                            if (extremum.getType() == Extremum.Type.MAX) {
                                Map.Entry<Long, Double> max = marketData.entrySet().stream()
                                        .filter(entry_ -> entry.getKey() - entry_.getKey() > -MIN_MAX_DETECT_ON_MARKET_DATA_TIME_WINDOW &&
                                                entry.getKey() - entry_.getKey() < MIN_MAX_DETECT_ON_MARKET_DATA_TIME_WINDOW)
                                        .max(Map.Entry.comparingByValue())
                                        .get();

                                extrema.add(new Extremum(max.getKey(), max.getValue(), Extremum.Type.MAX));
                            }
                            if (extremum.getType() == Extremum.Type.MIN) {
                                Map.Entry<Long, Double> min = marketData.entrySet().stream()
                                        .filter(entry_ -> entry.getKey() - entry_.getKey() > -MIN_MAX_DETECT_ON_MARKET_DATA_TIME_WINDOW &&
                                                entry.getKey() - entry_.getKey() < MIN_MAX_DETECT_ON_MARKET_DATA_TIME_WINDOW)
                                        .min(Map.Entry.comparingByValue())
                                        .get();

                                extrema.add(new Extremum(min.getKey(), min.getValue(), Extremum.Type.MIN));
                            }
                        }));

        /*
         * First we need to make sure that extremums are sorted by time.
         */
        List<Extremum> result = extrema.stream().sorted(Comparator.comparing(Extremum::getTimestamp)).toList();

        Log.log("start filtering...");
        do {
            removeSeveralOneTypeExtremums(result);
            removeMinHigherThanMax(marketData, result);
            removeExtremumsByPriceThreshold(result);
        } while (extremaAreNotCorrect(result));

        correctExtremumsIfThereOneIsBetween(marketData, result);
        Log.log("found " + extrema.size() + " extremums");

        return result;
    }

    /**
     * In some cases between MIN and MAX we have MIN (or MAX) which is lower (or higher) than founded one
     * In this case we need to update its price and timestamp to the right ones.
     */
    private void correctExtremumsIfThereOneIsBetween(TreeMap<Long, Double> marketData, List<Extremum> extrema) {

        for (int i = 1; i < extrema.size(); i++) {
            if (extrema.get(i - 1).getType() == extrema.get(i).getType()) {
                continue;
            }
            final Extremum prev = extrema.get(i - 1);
            final Extremum curr = extrema.get(i);
            final Extremum max = prev.getType() == Extremum.Type.MAX ? prev : curr;
            final Extremum min = prev.getType() == Extremum.Type.MIN ? prev : curr;

            TreeMap<Long, Double> marketDataBetweenMinMax = marketData.entrySet().stream()
                    .filter(data -> data.getKey() > prev.getTimestamp() && data.getKey() < curr.getTimestamp())
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            Map.Entry::getValue,
                            (first, second) -> first,
                            TreeMap::new
                    ));

            Optional<Map.Entry<Long, Double>> lowerThanMin = marketDataBetweenMinMax.entrySet().stream()
                    .filter(data -> data.getValue() < min.getPrice())
                    .max(Comparator.comparing(data -> min.getPrice() - data.getValue()));

            Optional<Map.Entry<Long, Double>> higherThanMax = marketDataBetweenMinMax.entrySet().stream()
                    .filter(data -> data.getValue() > max.getPrice())
                    .max(Comparator.comparing(data -> data.getValue() - max.getPrice()));

            if (lowerThanMin.isPresent()) {
                min.setTimestamp(lowerThanMin.get().getKey());
                min.setPrice(lowerThanMin.get().getValue());
                Log.log("MIN corrected");
            }
            if (higherThanMax.isPresent()) {
                max.setTimestamp(higherThanMax.get().getKey());
                max.setPrice(higherThanMax.get().getValue());
                Log.log("MAX corrected");
            }
        }
        Log.log("extremums size: " + extrema.size());
    }

    /**
     * This step we remove maximums and minimums that has not enough minimum price difference,
     * Example we convert { MAX (100), MIN (90), MAX (91), MIN (60) } -> { MAX (100), MIN (60) }
     */
    private void removeExtremumsByPriceThreshold(List<Extremum> extrema) {
        List<Extremum> toRemove;

        do {
            toRemove = new ArrayList<>();
            for (int i = 3; i <= extrema.size(); i++) {
                if (Math.abs(extrema.get(i - 1).getPrice() - extrema.get(i - 2).getPrice()) < minPriceDiff) {
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
            Log.log("removed " + toRemove.size() + " nearly extremums (price diff < " + minPriceDiff);
            extrema.removeAll(toRemove);
        } while (!toRemove.isEmpty());
        Log.log("extremums size: " + extrema.size());
    }

    /**
     * This step we remove maximum < minimum and minimum > maximum pairs
     * Example we convert { MAX (100), MIN (90), MAX (80), MIN (70) } -> { MAX (100), MIN (70) }
     */
    private void removeMinHigherThanMax(TreeMap<Long, Double> marketData, List<Extremum> extrema) {
        List<Extremum> toRemove;

        do {
            toRemove = new ArrayList<>();
            for (int i = 1; i < extrema.size() - 1; i++) {
                final Extremum prev = extrema.get(i - 1);
                final Extremum curr = extrema.get(i);
                final Extremum next = extrema.get(i + 1);
                if (prev.getType() == Extremum.Type.MAX) {
                    if (curr.getPrice() > prev.getPrice()) {
                        Optional<Map.Entry<Long, Double>> minBetweenPrevNext = marketData.entrySet().stream()
                                .filter(data -> data.getKey() > prev.getTimestamp() && data.getKey() < next.getTimestamp())
                                .min(Map.Entry.comparingByValue());

                        if (minBetweenPrevNext.isPresent() &&
                                minBetweenPrevNext.get().getValue() < prev.getPrice()) {
                            curr.setPrice(minBetweenPrevNext.get().getValue());
                            curr.setTimestamp(minBetweenPrevNext.get().getKey());
                            Log.log("MIN > MAX corrected");
                        } else {
                            toRemove.add(prev);
                            toRemove.add(curr);
                        }
                    }
                } else {
                    if (curr.getPrice() < prev.getPrice()) {
                        Optional<Map.Entry<Long, Double>> maxBetweenPrevNext = marketData.entrySet().stream()
                                .filter(data -> data.getKey() > prev.getTimestamp() && data.getKey() < next.getTimestamp())
                                .max(Map.Entry.comparingByValue());

                        if (maxBetweenPrevNext.isPresent() &&
                                maxBetweenPrevNext.get().getValue() < prev.getPrice()) {
                            curr.setPrice(maxBetweenPrevNext.get().getValue());
                            curr.setTimestamp(maxBetweenPrevNext.get().getKey());
                            Log.log("MAX < MIN corrected");
                        } else {
                            toRemove.add(prev);
                            toRemove.add(curr);
                        }
                    }
                }
            }
            Log.log("removed " + toRemove.size() + " MIN > MAX rows");
            extrema.removeAll(toRemove);
        } while (!toRemove.isEmpty());
        Log.log("extremums size: " + extrema.size());
    }

    /**
     * This step we remove one type extremums which are goes one by one (only MAX or MIN),
     * Example we convert { MAX (100), MAX (80), MAX (130), MIN (60) } -> { MAX (130), MIN (60) }
     */
    private void removeSeveralOneTypeExtremums(List<Extremum> extrema) {
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
            Log.log("removed " + toRemove.size() + " MAX,MAX... rows");
            extrema.removeAll(toRemove);
        } while (!toRemove.isEmpty());
        Log.log("extremums size: " + extrema.size());
    }

    @NotNull
    private List<Extremum> mapExtremumsToNewMa(TreeMap<Long, Double> marketData, List<Extremum> extremumsIn,
                                               long maTimeWindow) {
        TreeMap<Long, Double> average = movingAverage(marketData, maTimeWindow);
        List<Extremum> extremums = findExtremums(average);

        return extremumsIn.stream().map(extremumIn -> extremums.stream()
                        .min(Comparator.comparing(extremum ->
                                Math.abs(extremum.getTimestamp() - extremumIn.getTimestamp())))
                        .orElse(null))
                .collect(Collectors.toList());
    }

    /**
     * Method calculates moving average (MA) based on market data and average window size
     */
    private TreeMap<Long, Double> movingAverage(TreeMap<Long, Double> marketData, long timeWindow) {
        if (timeWindow <= 0) {
            throw new IllegalArgumentException("Invalid timeWindow: " + timeWindow);
        }

        TreeMap<Long, Double> result = new TreeMap<>();
        double sum = 0;

        // Get an iterator over the TreeMap's entry set
        Iterator<Map.Entry<Long, Double>> iterator = marketData.entrySet().iterator();
        LinkedList<Map.Entry<Long, Double>> window = new LinkedList<>();

        /*
         * Slide the window across the data
         */
        while (iterator.hasNext()) {
            Map.Entry<Long, Double> currentEntry = iterator.next();

            // Add the new price to the sum
            window.addLast(currentEntry);
            sum += currentEntry.getValue();

            // Check the time difference between the first and last elements of the window
            while (!window.isEmpty() && (window.getLast().getKey() - window.getFirst().getKey() > timeWindow)) {
                // Remove the oldest price from the sum and the window
                sum -= window.removeFirst().getValue();
            }

            // Calculate the moving average for the current window and add it to the result map
            result.put(currentEntry.getKey(), sum / window.size());
        }

        return result;
    }

    public static List<Extremum> findExtremums(TreeMap<Long, Double> maData) {
        ArrayList<Extremum> extremums = new ArrayList<>();

        List<Map.Entry<Long, Double>> entryList = new ArrayList<>(maData.entrySet());
        int size = entryList.size();

        for (int i = 0; i < size; i++) {
            double value = entryList.get(i).getValue();
            long key = entryList.get(i).getKey();
            boolean isMaximum = true;
            boolean isMinimum = true;

            // Check previous entries within the time window
            for (int j = i - 1; j >= 0; j--) {
                long prevKey = entryList.get(j).getKey();
                double prevValue = entryList.get(j).getValue();
                if (key - prevKey > MIN_MAX_SEARCH_ON_MA_TIME_WINDOW) {
                    break;  // Stop checking if the previous entry is outside the time window
                }
                if (value <= prevValue) {
                    isMaximum = false;
                }
                if (value >= prevValue) {
                    isMinimum = false;
                }
                if (!isMaximum && !isMinimum) {
                    break;
                }
            }

            // Check next entries within the time window
            for (int j = i + 1; j < size; j++) {
                long nextKey = entryList.get(j).getKey();
                double nextValue = entryList.get(j).getValue();
                if (nextKey - key > MIN_MAX_SEARCH_ON_MA_TIME_WINDOW) {
                    break;  // Stop checking if the next entry is outside the time window
                }
                if (value <= nextValue) {
                    isMaximum = false;
                }
                if (value >= nextValue) {
                    isMinimum = false;
                }
                if (!isMaximum && !isMinimum) {
                    break;
                }
            }

            // If the current value is either a maximum or a minimum, add it to the list
            if (isMaximum) {
                extremums.add(new Extremum(key, value, Extremum.Type.MAX));
            } else if (isMinimum) {
                extremums.add(new Extremum(key, value, Extremum.Type.MIN));
            }
        }

        return extremums;
    }

    private boolean extremaAreNotCorrect(List<Extremum> extrema) {
        for (int i = 2; i <= extrema.size(); i++) {
            final Extremum prev = extrema.get(i - 2);
            final Extremum curr = extrema.get(i - 1);
            if (curr.getType() == Extremum.Type.MAX &&
                    prev.getType() == Extremum.Type.MAX) {
                return true;
            }
            if (curr.getType() == Extremum.Type.MIN &&
                    prev.getType() == Extremum.Type.MIN) {
                return true;
            }
            if (prev.getType() == Extremum.Type.MAX) {
                if (curr.getPrice() > prev.getPrice()) {
                    return true;
                }
            } else {
                if (curr.getPrice() < prev.getPrice()) {
                    return true;
                }
            }
        }
        return false;
    }
}
