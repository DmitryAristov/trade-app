package org.bybittradeapp.analysis.service;

import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.domain.ImbalanceState;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;
import org.bybittradeapp.ui.domain.MarketKlineEntry;
import org.bybittradeapp.ui.utils.JsonUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ImbalanceService implements VolatilityListener {

    /**
     * Время хранения ежесекундных данных (1000мс * 60с * 60м * 3ч = 3 часа)
     */
    private static final long SECONDS_DATA_LIVE_TIME = 10800_000L;

    /**
     * Время хранения ежеминутных данных (1000мс * 60с * 60м * 3ч = 3 часа)
     */
    private static final long MINUTES_DATA_LIVE_TIME = 10800_000L;

    /**
     * Время за которое если не появилось нового минимума то считаем имбаланс завершенным (1000мс * 60с = 1 минута)
     */
    private static final long COMPLETE_TIME = 60_000L;

    /**
     * Время в течение которого после завершения предыдущего имбаланса не ищется новый (1000мс * 60с * 5м = 5 минут)
     */
    private static final long PREVENT_TRACK_IMBALANCE_TIME = 300_000L;


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


    public ImbalanceService(TreeMap<Long, MarketEntry> marketData) {
        this.marketData = marketData;
    }

    public void onTick(long currentTime, MarketEntry marketEntry) {
        updateData(currentTime, marketEntry);
        switch (currentState) {
            case WAIT -> detectImbalance(currentTime, marketEntry);
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
                    correctImbalanceStart();
                    correctImbalanceEnd();
                    currentState = ImbalanceState.PROGRESS;
                    Log.log("Imbalance started: " + currentImbalance);
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
                    correctImbalanceStart();
                    correctImbalanceEnd();
                    currentState = ImbalanceState.PROGRESS;
                    Log.log("Imbalance started: " + currentImbalance);
                    return;
                }
            }
        }
    }

    private void correctImbalanceStart() {
        // сейчас времена начала и конца имбаланса - это времена из минутных данных
        // нужно скорректировать эти цены на секундные данные
        // для этого нужно сначала определить конкретную секунду начала имбаланса
        // для этого берем секундные данные за минуту, что является началом имбаланса и ищем
        // в этом списке startPrice
        long startSecond = switch (currentImbalance.getType()) {
            case UP -> secondsData.subMap(currentImbalance.getStartTime() - 60000L, true,
                            currentImbalance.getStartTime(), true)
                    .entrySet()
                    .stream()
                    .filter(marketEntry -> marketEntry.getValue().low() == currentImbalance.getStartPrice())
                    .map(Map.Entry::getKey)
                    .max(Comparator.comparing(Long::longValue))
                    .orElseThrow();
            case DOWN -> secondsData.subMap(currentImbalance.getStartTime() - 60000L, true,
                            currentImbalance.getStartTime(), true)
                    .entrySet()
                    .stream()
                    .filter(marketEntry -> marketEntry.getValue().high() == currentImbalance.getStartPrice())
                    .map(Map.Entry::getKey)
                    .max(Comparator.comparing(Long::longValue))
                    .orElseThrow();
        };
        currentImbalance.setStartTime(startSecond);

        // дальше берем секундные данные в обратном порядке (от большего времени к меньшему)
        // и фильтруем только те, что были до старта имбаланса
        SortedMap<Long, MarketEntry> descendingSecondsDataFromImbalanceStart = secondsData
                .descendingMap()
                .subMap(currentImbalance.getStartTime(), true, secondsData.firstKey(), true);

        // определяем текущую скорость роста или падения имбаланса
        double currentSpeed = Math.abs(currentImbalance.getStartPrice() - currentImbalance.getEndPrice()) /
                (currentImbalance.getEndTime() - currentImbalance.getStartTime());

        // ищем начало имбаланса путем нахождения точки, до которой скорость роста или падения будет всегда меньше чем нам нужно
        switch (currentImbalance.getType()) {
            case UP -> {
                // сначала проходим всю секундную дату, чтобы найти максимально маленькое время,
                // при котором скорость роста еще сохраняется
                long possibleStartTime = currentImbalance.getStartTime();
                for (long previousTime : descendingSecondsDataFromImbalanceStart.keySet()) {
                    double previousPrice = descendingSecondsDataFromImbalanceStart.get(previousTime).low();
                    double nextSpeed = (currentImbalance.getStartPrice() - previousPrice) /
                            (previousTime - currentImbalance.getStartTime());
                    if (nextSpeed > currentSpeed) {
                        possibleStartTime = previousTime;
                    }
                }
                // потом находим в образовавшемся промежутке минимум - он и является началом имбаланса
                var secondsImbalanceSubMap = secondsData.subMap(possibleStartTime, true, currentImbalance.getEndTime(), true);
                for (long time : secondsImbalanceSubMap.keySet()) {
                    double price = secondsImbalanceSubMap.get(time).low();
                    if (price < currentImbalance.getStartPrice()) {
                        currentImbalance.setStartPrice(price);
                        currentImbalance.setStartTime(time);
                    }
                }
            }
            case DOWN -> {
                // сначала проходим всю секундную дату, чтобы найти максимально маленькое время,
                // при котором скорость падения еще сохраняется
                long possibleStartTime = currentImbalance.getStartTime();
                for (long previousTime : descendingSecondsDataFromImbalanceStart.keySet()) {
                    double previousPrice = descendingSecondsDataFromImbalanceStart.get(previousTime).high();
                    double nextSpeed = (previousPrice - currentImbalance.getStartPrice()) /
                            (currentImbalance.getStartTime() - previousTime);
                    if (nextSpeed > currentSpeed) {
                        possibleStartTime = previousTime;
                    }
                }
                // потом находим в образовавшемся промежутке максимум - он и является началом имбаланса
                var secondsImbalanceSubMap = secondsData.subMap(possibleStartTime, true, currentImbalance.getEndTime(), true);
                for (long time : secondsImbalanceSubMap.keySet()) {
                    double price = secondsImbalanceSubMap.get(time).high();
                    if (price > currentImbalance.getStartPrice()) {
                        currentImbalance.setStartPrice(price);
                        currentImbalance.setStartTime(time);
                    }
                }
            }
        }
    }

    private void correctImbalanceEnd() {
        // так как имбаланс был найден на минутных данных,
        // время и цена его окончания возможно неправильные, нужно их скорректировать
        // просто ищем максимум или минимум внутри имбаланса, если такие есть то это правильный конец
        var secondsImbalanceSubMap = secondsData.subMap(currentImbalance.getStartTime(), true, currentImbalance.getEndTime(), true);
        switch (currentImbalance.getType()) {
            case UP -> {
                for (long time : secondsImbalanceSubMap.keySet()) {
                    double price = secondsImbalanceSubMap.get(time).high();
                    if (price > currentImbalance.getEndPrice()) {
                        currentImbalance.setEndPrice(price);
                        currentImbalance.setEndTime(time);
                    }
                }
            }
            case DOWN -> {
                for (long time : secondsImbalanceSubMap.keySet()) {
                    double price = secondsImbalanceSubMap.get(time).low();
                    if (price < currentImbalance.getEndPrice()) {
                        currentImbalance.setEndPrice(price);
                        currentImbalance.setEndTime(time);
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
        updateUiData(currentImbalance);
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
        this.speedThreshold = averageDailyPriceChange * 0.1 / 60000.;

        Log.log("recalculated price change: " + priceChangeThreshold +
                "$ and min speed: " + speedThreshold * 60000 * 60 + "$/hour");
    }

    public ImbalanceState getCurrentState() {
        return currentState;
    }

    public Imbalance getCurrentImbalance() {
        return currentImbalance;
    }




    private final TreeMap<Long, MarketEntry> marketData;

    private void updateUiData(Imbalance imbalance) {
        var timeDelay = 1000L * 60L * 20L;
        var secondsMarketKLineEntries = marketData
                .subMap(imbalance.getStartTime() - timeDelay, imbalance.getEndTime() + timeDelay)
                .entrySet()
                .stream()
                .map(entry -> {
                    var uiEntry = new MarketKlineEntry();
                    uiEntry.setStartTime(entry.getKey());
                    uiEntry.setLowPrice(entry.getValue().low());
                    uiEntry.setHighPrice(entry.getValue().high());
                    uiEntry.setOpenPrice((entry.getValue().low() + entry.getValue().high()) * 0.5);
                    uiEntry.setClosePrice((entry.getValue().low() + entry.getValue().high()) * 0.5);
                    return uiEntry;
                })
                .collect(Collectors.toMap(
                        MarketKlineEntry::getStartTime,
                        Function.identity(),
                        (first, second) -> first,
                        TreeMap::new
                ));

        JsonUtils.updateMarketData(secondsMarketKLineEntries);

        var tempMap = new TreeMap<>(secondsMarketKLineEntries);
        tempMap.forEach((key, value) -> value.setStartTime(value.getStartTime() + JsonUtils.ZONE_DELAY_MILLS));

        JsonUtils.updateAnalysedData(new ArrayList<>(), List.of(imbalance), new ArrayList<>(), tempMap);
    }
}
