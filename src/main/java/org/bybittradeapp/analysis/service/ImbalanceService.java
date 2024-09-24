package org.bybittradeapp.analysis.service;

import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.domain.ImbalanceState;
import org.bybittradeapp.backtest.service.Tickle;

import java.util.NavigableMap;
import java.util.TreeMap;

public class ImbalanceService implements Tickle {

    /**
     * Часть от дневной волатильности которая уже считается имбалансом.
     * Например, волатильность = 5%, средняя цена = 50000$, IMBALANCE_SIZE = 1, тогда 0.05 * 50000 * 1 = 2500 - считаем что изменение >= 2500 это имбаланс.
     * Если IMBALANCE_SIZE = 0.5, то 2500 * 0.5 = 1250 - изменение >= 2500 это имбаланс.
     */
    private final double PRICE_CHANGE_THRESHOLD;
    private final double SPEED_THRESHOLD_PER_MILLISECOND;
    private static final long POSSIBLE_END_TIME = 60 * 1000L;
    private static final long EXACT_END_TIME = 180 * 1000L;
    private static final long COMPLETE_TIME = 600 * 1000L;
    private static final long COMBINE_TIME_FACTOR = 2;

    private final TreeMap<Long, Double> data = new TreeMap<>();
    private Imbalance currentImbalance = null;
    private Imbalance lastImbalance = null;
    private ImbalanceState currentState = ImbalanceState.SEARCHING_FOR_IMBALANCE;

    public ImbalanceService(VolatilityService volatilityService) {
        this.PRICE_CHANGE_THRESHOLD = volatilityService.getVolatility() * volatilityService.getAverage() * 0.3;
        this.SPEED_THRESHOLD_PER_MILLISECOND = volatilityService.getVolatility() * volatilityService.getAverage() / (1.5 * 60 * 60L * 1000L);
    }

    @Override
    public void onTick(long time, double price) {
        updateData(time, price);
        switch (currentState) {
            case SEARCHING_FOR_IMBALANCE -> detectImbalanceStart(time, price);
            case IMBALANCE_IN_PROGRESS -> trackImbalanceProgress(time, price);
            case SEARCHING_POTENTIAL_END_POINT -> searchingPossibleEndPoint(time, price);
            case POTENTIAL_END_POINT_FOUND -> evaluatePossibleEndPointFound(time, price);
            case IMBALANCE_COMPLETED -> resetImbalanceState();
        }
    }

    private void detectImbalanceStart(long currentTime, double currentPrice) {

        NavigableMap<Long, Double> descendingData = data.descendingMap();
        Long initialTime = null;
        Double initialPrice = null;

        for (long previousTime : descendingData.keySet()) {
            double previousPrice = descendingData.get(previousTime);

            if (previousTime == currentTime) {
                // Пропускаем текущий элемент, так как он является точкой отсчета
                continue;
            }

            double priceChange = Math.abs(currentPrice - previousPrice);
            if (priceChange > PRICE_CHANGE_THRESHOLD) {
                double speed = priceChange / (currentTime - previousTime);

                if (speed > SPEED_THRESHOLD_PER_MILLISECOND) {
                    if (initialTime == null) {
                        initialTime = previousTime;
                        initialPrice = previousPrice;
                    }

                    //TODO combine if previous imbalance meets condition
                    double nextSpeed = Math.abs(initialPrice - previousPrice) / (initialTime - previousTime);
                    if (nextSpeed < speed) {
                        currentImbalance = new Imbalance(previousTime, previousPrice, currentTime, currentPrice);
                        currentState = ImbalanceState.IMBALANCE_IN_PROGRESS;
                        return;
                    }
                }
            }
        }
    }

    private void trackImbalanceProgress(long currentTime, double currentPrice) {
        double priceChange = Math.abs(currentPrice - currentImbalance.getStartPrice());
        long timeElapsed = currentTime - currentImbalance.getStartTime();
        double speed = priceChange / timeElapsed;

        if (speed > SPEED_THRESHOLD_PER_MILLISECOND) {
            updateImbalanceEndPrice(currentTime, currentPrice, ImbalanceState.IMBALANCE_IN_PROGRESS);
            if (currentTime - currentImbalance.getEndTime() > POSSIBLE_END_TIME) {
                currentState = ImbalanceState.SEARCHING_POTENTIAL_END_POINT;
            }

        } else {
            currentState = ImbalanceState.SEARCHING_POTENTIAL_END_POINT;
        }
    }

    private void searchingPossibleEndPoint(long currentTime, double currentPrice) {
        updateImbalanceEndPrice(currentTime, currentPrice, ImbalanceState.IMBALANCE_IN_PROGRESS);

        if (currentTime - currentImbalance.getEndTime() > EXACT_END_TIME) {
            currentState = ImbalanceState.POTENTIAL_END_POINT_FOUND;
        }
    }

    private void evaluatePossibleEndPointFound(long currentTime, double currentPrice) {
        updateImbalanceEndPrice(currentTime, currentPrice, ImbalanceState.IMBALANCE_IN_PROGRESS);

        if (currentTime - currentImbalance.getEndTime() > COMPLETE_TIME) {
            currentImbalance.setCompleteTime(currentTime);
            currentState = ImbalanceState.IMBALANCE_COMPLETED;
        }
    }

    private void updateImbalanceEndPrice(long currentTime, double currentPrice, ImbalanceState state) {
        switch (currentImbalance.getType()) {
            case UP -> {
                if (currentPrice > currentImbalance.getEndPrice()) {
                    currentImbalance.setEndPrice(currentPrice);
                    currentImbalance.setEndTime(currentTime);
                    currentState = state;
                }
            }
            case DOWN -> {
                if (currentPrice < currentImbalance.getEndPrice()) {
                    currentImbalance.setEndPrice(currentPrice);
                    currentImbalance.setEndTime(currentTime);
                    currentState = state;
                }
            }
        }
    }

    private void resetImbalanceState() {
        lastImbalance = Imbalance.of(currentImbalance);
        currentImbalance = null;
        currentState = ImbalanceState.SEARCHING_FOR_IMBALANCE;
    }

    private void updateData(long time, double price) {
        data.put(time, price);
        if (data.size() > 10800) {
            data.pollFirstEntry();
        }
    }

    public Imbalance getImbalance() {
        return currentImbalance;
    }

    public ImbalanceState getState() {
        return currentState;
    }
}
