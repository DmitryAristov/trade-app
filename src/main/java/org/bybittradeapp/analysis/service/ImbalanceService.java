package org.bybittradeapp.analysis.service;

import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.domain.ImbalanceState;
import org.bybittradeapp.backtest.service.Tickle;

import java.util.*;

public class ImbalanceService implements Tickle {

    /**
     * Часть от дневной волатильности которая уже считается имбалансом.
     * Например, волатильность = 5%, средняя цена = 50000$, IMBALANCE_SIZE = 1, тогда 0.05 * 50000 * 1 = 2500 - считаем что изменение >= 2500 это имбаланс.
     * Если IMBALANCE_SIZE = 0.5, то 2500 * 0.5 = 1250 - изменение >= 2500 это имбаланс.
     */
    private final double PRICE_CHANGE_THRESHOLD;
    private final double SPEED_THRESHOLD_PER_MILLISECOND;
    private static final int POTENTIAL_ENDPOINT_SPEED_CHANGE_TIME = 5;
    private static final long COMPLETE_TIME =  2 * 60 * 1000L;
    private static final long PREVENT_TRACK_IMBALANCE_TIME = 4 * 60 * 60L * 1000L;


    private final TreeMap<Long, Double> data = new TreeMap<>();
    private final TreeMap<Long, Double> speedTrackData = new TreeMap<>();
    private final LinkedList<Imbalance> imbalances = new LinkedList<>();

    private Imbalance currentImbalance = null;
    private ImbalanceState currentState = ImbalanceState.SEARCHING_FOR_IMBALANCE;

    public ImbalanceService(VolatilityService volatilityService) {
        this.PRICE_CHANGE_THRESHOLD = volatilityService.getVolatility() * volatilityService.getAverage();
        this.SPEED_THRESHOLD_PER_MILLISECOND = volatilityService.getVolatility() * volatilityService.getAverage() / (2 * 60 * 60L * 1000L);
    }

    @Override
    public void onTick(long time, double price) {
        updateData(time, price);
        switch (currentState) {
            case SEARCHING_FOR_IMBALANCE -> detectImbalanceStart(time, price);
            case COMBINE_IMBALANCES -> combineImbalances();
            case IMBALANCE_IN_PROGRESS -> trackImbalanceProgress(time, price);
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
                continue;
            }

            double priceChange = Math.abs(currentPrice - previousPrice);
            if (priceChange > PRICE_CHANGE_THRESHOLD) {
                double speed = priceChange / (currentTime - previousTime);

                if (speed > SPEED_THRESHOLD_PER_MILLISECOND) {
                    if (currentPrice > previousPrice) {
                        if (!imbalances.isEmpty() && imbalances.getLast().getType() == Imbalance.Type.DOWN &&
                                currentTime - imbalances.getLast().getEndTime() < PREVENT_TRACK_IMBALANCE_TIME) {
                            return;
                        }
                        if (!imbalances.isEmpty() && imbalances.getLast().getType() == Imbalance.Type.UP &&
                                imbalances.getLast().getEndPrice() > currentPrice) {
                            return;
                        }
                    } else {
                        if (!imbalances.isEmpty() && imbalances.getLast().getType() == Imbalance.Type.UP &&
                                currentTime - imbalances.getLast().getEndTime() < PREVENT_TRACK_IMBALANCE_TIME) {
                            return;
                        }
                        if (!imbalances.isEmpty() && imbalances.getLast().getType() == Imbalance.Type.DOWN &&
                                imbalances.getLast().getEndPrice() < currentPrice) {
                            return;
                        }
                    }
                    if (initialTime == null) {
                        initialTime = previousTime;
                        initialPrice = previousPrice;
                    }

                    double nextSpeed = Math.abs(initialPrice - previousPrice) / (initialTime - previousTime);
                    if (nextSpeed < speed) {
                        currentImbalance = new Imbalance(previousTime, previousPrice, currentTime, currentPrice);
                        currentState = ImbalanceState.COMBINE_IMBALANCES;
                    }
                }
            }
        }
    }

    private void combineImbalances() {
        if (imbalances.isEmpty()) {
            currentState = ImbalanceState.IMBALANCE_IN_PROGRESS;
            return;
        }

        Imbalance prevImbalance = imbalances.getLast();
        if (prevImbalance.getType() != currentImbalance.getType()) {
            currentState = ImbalanceState.IMBALANCE_IN_PROGRESS;
            return;
        }

        double priceChange = Math.abs(prevImbalance.getStartPrice() - currentImbalance.getEndPrice());
        double speed = priceChange / (currentImbalance.getEndTime() - prevImbalance.getStartTime());
        if (speed > SPEED_THRESHOLD_PER_MILLISECOND * Math.sqrt(PRICE_CHANGE_THRESHOLD / priceChange)) {
            currentImbalance.setStartTime(prevImbalance.getStartTime());
            currentImbalance.setStartPrice(prevImbalance.getStartPrice());
            currentImbalance.setCombinesCount(prevImbalance.getCombinesCount() + 1);
            imbalances.removeLast();
        }
        currentState = ImbalanceState.IMBALANCE_IN_PROGRESS;
    }

    private void trackImbalanceProgress(long currentTime, double currentPrice) {
        updateImbalanceProgress(currentTime, currentPrice);

        double[] speedArray = new double[POTENTIAL_ENDPOINT_SPEED_CHANGE_TIME - 1];
        List<Map.Entry<Long, Double>> dataArray = new ArrayList<>(speedTrackData.entrySet());
        for (int i = 1; i < dataArray.size(); i++) {
            speedArray[i - 1] = dataArray.get(i - 1).getValue() - dataArray.get(i).getValue();
        }

        if (currentImbalance.getType() == Imbalance.Type.UP) {
            if (Arrays.stream(speedArray).allMatch(value -> value > SPEED_THRESHOLD_PER_MILLISECOND * 1000)) {
                currentState = ImbalanceState.POTENTIAL_END_POINT_FOUND;
            }
        } else {
            if (Arrays.stream(speedArray).allMatch(value -> value < - SPEED_THRESHOLD_PER_MILLISECOND * 1000)) {
                currentState = ImbalanceState.POTENTIAL_END_POINT_FOUND;
            }
        }
    }

    private void evaluatePossibleEndPointFound(long currentTime, double currentPrice) {
        updateImbalanceProgress(currentTime, currentPrice);

        if (currentTime - currentImbalance.getEndTime() > COMPLETE_TIME) {
            currentImbalance.setCompleteTime(currentTime);
            currentImbalance.setCompletePrice(currentPrice);
            currentImbalance.incrementCompletesCount();
            currentState = ImbalanceState.IMBALANCE_COMPLETED;
        }
    }

    private void updateImbalanceProgress(long currentTime, double currentPrice) {
        switch (currentImbalance.getType()) {
            case UP -> {
                if (currentPrice > currentImbalance.getEndPrice()) {
                    currentImbalance.setEndPrice(currentPrice);
                    currentImbalance.setEndTime(currentTime);
                    currentState = ImbalanceState.IMBALANCE_IN_PROGRESS;
                }
            }
            case DOWN -> {
                if (currentPrice < currentImbalance.getEndPrice()) {
                    currentImbalance.setEndPrice(currentPrice);
                    currentImbalance.setEndTime(currentTime);
                    currentState = ImbalanceState.IMBALANCE_IN_PROGRESS;
                }
            }
        }
    }

    private void resetImbalanceState() {
        if (imbalances.isEmpty() || !imbalances.getLast().equals(currentImbalance)) {
            imbalances.add(currentImbalance);
        }
        currentImbalance = null;
        currentState = ImbalanceState.SEARCHING_FOR_IMBALANCE;
    }

    private void updateData(long time, double price) {
        data.put(time, price);
        if (data.size() > 10800) {
            data.pollFirstEntry();
        }
        speedTrackData.put(time, price);
        if (speedTrackData.size() > POTENTIAL_ENDPOINT_SPEED_CHANGE_TIME) {
            speedTrackData.pollFirstEntry();
        }
    }

    public LinkedList<Imbalance> getImbalances() {
        return imbalances;
    }

    public ImbalanceState getState() {
        return currentState;
    }

    public Imbalance getCurrentImbalance() {
        return currentImbalance;
    }
}
