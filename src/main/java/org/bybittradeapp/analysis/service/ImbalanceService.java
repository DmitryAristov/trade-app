package org.bybittradeapp.analysis.service;

import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.domain.ImbalanceState;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;

import java.time.Instant;
import java.util.*;

public class ImbalanceService implements VolatilityListener {

    /**
     * Время хранения ежесекундных данных (1000мс * 60с * 60м * 2ч = 2 часа)
     */
    private static final long SECONDS_DATA_LIVE_TIME = 7200_000L;

    /**
     * Время хранения ежеминутных данных (1000мс * 60с * 60м * 2ч = 2 часа)
     */
    private static final long MINUTES_DATA_LIVE_TIME = 7200_000L;

    /**
     * Время за которое если не появилось нового минимума то считаем имбаланс завершенным (1000мс * 60с * 15м = 15 минут)
     */
    private static final long COMPLETE_TIME = 900_000L;

    /**
     * Время в течение которого после завершения предыдущего имбаланса не ищется новый (1000мс * 60с * 60м = 1 час)
     */
    private static final long PREVENT_TRACK_IMBALANCE_TIME = 3600_000L;


    /**
     * Минимальное изменение цены, которое считается имбалансом если оно произошло со скоростью >= минимальной скорости
     */
    private double priceChangeThreshold;

    /**
     * Минимальная скорость изменения цены
     */
    private double speedThreshold;

    private ImbalanceState currentState = ImbalanceState.WAIT;

    private final TreeMap<Long, MarketEntry> secondsData = new TreeMap<>();
    private final TreeMap<Long, MarketEntry> minutesData = new TreeMap<>();

    private double currentMinuteHigh = 0.;
    private double currentMinuteLow = Double.MAX_VALUE;
    private long lastMinuteTimestamp = -1L;
    private long lastDetectImbalanceTime = -1L;

    private Imbalance currentImbalance = null;
    private final LinkedList<Imbalance> imbalances = new LinkedList<>();


    public ImbalanceService() {  }

    public void onTick(long currentTime, MarketEntry marketEntry) {
        updateData(currentTime, marketEntry);
        switch (currentState) {
            case WAIT -> detectImbalance(currentTime, marketEntry);
            case STARTED -> determineImbalanceStart();
            case PROGRESS -> trackImbalanceProgress(currentTime, marketEntry);
            case POTENTIAL_END_POINT -> evaluatePossibleEndPoint(currentTime, marketEntry);
            case COMPLETED -> resetImbalanceState();
        }
    }

    /**
     * Идем от текущей цены назад. Определяем разницу цен и скорость изменения.
     * Если разница и скорость больше минимальных, то смотрим на предыдущие имбалансы:
     *   1. Если предыдущий имбаланс был в другую сторону и был меньше 4 часов назад, не учитываем текущий.
     *   2. Если есть предыдущий имбаланс с конечной ценой "лучше" чем найденный текущий, не учитываем текущий
     * Если пройдены эти проверки, то дальше ищем точку начала имбаланса:
     *   идем дальше по циклу и смотрим когда скорость падает. Это и есть точка начала.
     *
     */
    private void detectImbalance(long currentTime, MarketEntry currentEntry) {
        if (currentTime - lastDetectImbalanceTime < 60_000L) {
            return;
        }
        lastDetectImbalanceTime = currentTime;


        if (!imbalances.isEmpty() &&
                imbalances.getLast() != null &&
                currentTime - imbalances.getLast().getEndTime() < PREVENT_TRACK_IMBALANCE_TIME) {
            return;
        }

        NavigableMap<Long, MarketEntry> descendingData = minutesData.descendingMap();
        for (long previousTime : descendingData.keySet()) {

            if (previousTime == currentTime) {
                continue;
            }
            MarketEntry previousEntry = descendingData.get(previousTime);

            // если максимум за текущую секунду больше чем минимум за минуту из цикла на минимальное изменение цены
            //  -> потенциальный имбаланс вверх
            if (currentEntry.high() - previousEntry.low() > priceChangeThreshold) {
                double priceChange = currentEntry.high() - previousEntry.low();
                double priceChangeSpeed = priceChange / (currentTime - previousTime);

                // если скорость изменения > минимальной необходимой скорости -> нашли имбаланс вверх
                // теперь нужно отследить его начало
                if (priceChangeSpeed > speedThreshold) {
                    currentImbalance = new Imbalance(previousTime, previousEntry.low(), currentTime, currentEntry.high(), Imbalance.Type.UP);
                    currentState = ImbalanceState.STARTED;
                } else {
                    // условие по минимальной скорости не выполнено
                    return;
                }

            // если максимум за минуту из цикла меньше чем минимум за текущую секунду на минимальное изменение цены
            //  -> потенциальный имбаланс вниз
            } else if (previousEntry.high() - currentEntry.low() > priceChangeThreshold) {
                double priceChange = previousEntry.high() - currentEntry.low();
                double priceChangeSpeed = priceChange / (currentTime - previousTime);

                // если скорость изменения > минимальной необходимой скорости -> нашли имбаланс вниз
                // теперь нужно отследить его начало
                if (priceChangeSpeed > speedThreshold) {
                    currentImbalance = new Imbalance(previousTime, previousEntry.high(), currentTime, currentEntry.low(), Imbalance.Type.DOWN);
                    currentState = ImbalanceState.STARTED;
                } else {
                    // условие по минимальной скорости не выполнено
                    return;
                }
            }
        }
    }

    private void determineImbalanceStart() {
        long startSecond = switch (currentImbalance.getType()) {
            case UP -> secondsData.subMap(currentImbalance.getStartTime() - 60000L, currentImbalance.getStartTime())
                    .entrySet()
                    .stream()
                    .filter(marketEntry -> marketEntry.getValue().low() == currentImbalance.getStartPrice())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(currentImbalance.getStartTime());
            case DOWN -> secondsData.subMap(currentImbalance.getStartTime() - 60000L, currentImbalance.getStartTime())
                    .entrySet()
                    .stream()
                    .filter(marketEntry -> marketEntry.getValue().high() == currentImbalance.getStartPrice())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(currentImbalance.getStartTime());
        };

        SortedMap<Long, MarketEntry> descendingData = secondsData
                .descendingMap()
                .subMap(startSecond, secondsData.firstKey());

        double endPrice = currentImbalance.getEndPrice();
        double endTime = currentImbalance.getEndTime();

        switch (currentImbalance.getType()) {
            case UP -> {
                for (long previousTime : descendingData.keySet()) {
                    double previousPrice = descendingData.get(previousTime).low();
                    double speed = (endPrice - previousPrice) / (endTime - previousTime);

                    if (speed < speedThreshold * 0.75) {
                        currentImbalance.setStartTime(previousTime);
                        currentImbalance.setStartPrice(previousPrice);
                        currentState = ImbalanceState.PROGRESS;
                        Log.log("UP started: " + currentImbalance);
                        return;
                    }
                }
            }
            case DOWN -> {
                for (long previousTime : descendingData.keySet()) {
                    double previousPrice = descendingData.get(previousTime).high();

                    double speed = (previousPrice - endPrice) / (endTime - previousTime);
                    if (speed < speedThreshold * 0.75) {
                        currentImbalance.setStartTime(previousTime);
                        currentImbalance.setStartPrice(previousPrice);
                        currentState = ImbalanceState.PROGRESS;
                        Log.log("DOWN started: " + currentImbalance);
                        return;
                    }
                }
            }
        }
    }

    private void trackImbalanceProgress(long currentTime, MarketEntry currentEntry) {
        switch (currentImbalance.getType()) {
            case UP -> {
                if (currentEntry.high() > currentImbalance.getEndPrice()) {
                    currentImbalance.setEndPrice(currentEntry.high());
                    currentImbalance.setEndTime(currentTime);
                }
            }
            case DOWN -> {
                if (currentEntry.low() < currentImbalance.getEndPrice()) {
                    currentImbalance.setEndPrice(currentEntry.low());
                    currentImbalance.setEndTime(currentTime);
                }
            }
        }

        // здесь нужно проверять паттерн разворота
        // нужно измерять ебанутую скорость вниз, а потом некоторое время (больше чем время вниз) ебанутую скорость вверх (~100$/секунду)


        if (true) {

            currentState = ImbalanceState.POTENTIAL_END_POINT;
        }

    }

    private void evaluatePossibleEndPoint(long currentTime, MarketEntry currentEntry) {
        switch (currentImbalance.getType()) {
            case UP -> {
                if (currentEntry.high() > currentImbalance.getEndPrice()) {
                    currentImbalance.setEndPrice(currentEntry.high());
                    currentImbalance.setEndTime(currentTime);
                    currentState = ImbalanceState.PROGRESS;
                }
            }
            case DOWN -> {
                if (currentEntry.low() < currentImbalance.getEndPrice()) {
                    currentImbalance.setEndPrice(currentEntry.low());
                    currentImbalance.setEndTime(currentTime);
                    currentState = ImbalanceState.PROGRESS;
                }
            }
        }

        if (currentTime - currentImbalance.getEndTime() > COMPLETE_TIME) {
            Log.log(currentImbalance.getType() + " completed at " + Instant.ofEpochMilli(currentTime));

            currentImbalance.setCompleteTime(currentTime);
            currentImbalance.setCompletePrice((currentEntry.high() + currentEntry.low()) / 2.);
            currentState = ImbalanceState.COMPLETED;
        }
    }

    private void updateData(long currentTime, MarketEntry currentEntry) {
        // Добавляем данные в secondsData
        secondsData.put(currentTime, currentEntry);
        if (currentTime - secondsData.firstKey() > SECONDS_DATA_LIVE_TIME) {
            secondsData.pollFirstEntry();
        }

        double priceHigh = currentEntry.high();
        double priceLow = currentEntry.low();

        if (priceHigh > currentMinuteHigh) {
            currentMinuteHigh = priceHigh;
        }
        if (priceLow < currentMinuteLow) {
            currentMinuteLow = priceLow;
        }

        if (lastMinuteTimestamp == -1) {
            lastMinuteTimestamp = currentTime;
        }

        if (currentTime - lastMinuteTimestamp > 60000L) {
            minutesData.put(currentTime, new MarketEntry(currentMinuteHigh, currentMinuteLow));
            currentMinuteHigh = 0;
            currentMinuteLow = Double.MAX_VALUE;
            lastMinuteTimestamp = currentTime;
        }
        if (!minutesData.isEmpty() && currentTime - minutesData.firstKey() > MINUTES_DATA_LIVE_TIME) {
            minutesData.pollFirstEntry();
        }
    }

    private void resetImbalanceState() {
        imbalances.add(currentImbalance);

        currentImbalance = null;
        currentState = ImbalanceState.WAIT;
    }

    public LinkedList<Imbalance> getImbalances() {
        return imbalances;
    }

    @Override
    public void notify(double volatility, double average) {
        double averageDailyPriceChange = volatility * average;
        this.priceChangeThreshold = averageDailyPriceChange * 0.4; // ~500$
        this.speedThreshold = averageDailyPriceChange * 0.02 / 60000.;

        Log.log("recalculated price change: " + priceChangeThreshold +
                "$ and min speed: " + speedThreshold * 60000 * 60 + "$/hour");
    }

    public ImbalanceState getCurrentState() {
        return currentState;
    }

    public Imbalance getCurrentImbalance() {
        return currentImbalance;
    }
}
