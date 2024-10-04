package org.bybittradeapp.analysis.service;

import kotlin.Pair;
import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.domain.ImbalanceState;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;
import org.bybittradeapp.ui.utils.JsonUtils;

import java.time.Instant;
import java.util.*;

public class ImbalanceService implements VolatilityListener {

    /**
     * Время хранения ежесекундных данных (1000мс * 60с * 5м = 5 минут).
     * Отдельная коллекция для поиска окончания размером 120 секунд.
     */
    private static final long DATA_LIVE_TIME = 300_000L, LAST_SECONDS_DATA_LIVE_TIME = 60_000L;

    /**
     * Время за которое если не появилось нового минимума то считаем имбаланс завершенным (1000мс * 60с = 1 минута)
     */
    private static final long COMPLETE_TIME = 7_000L;

    /**
     * Константы для расчета минимальной скорости и цены.
     * Формула: минимальная цена/скорость = [средняя цена] * [волатильность] * [константа]
     */
    private static final double SPEED_MODIFICATOR = 2E-7, PRICE_MODIFICATOR = 0.015;

    private static final int MIN_IMBALANCE_TIME_SIZE = 4;

    /**
     * Минимальное изменение цены и минимальная скорость изменения.
     * Пересчитывается каждый день на основе волатильности и средней цены.
     * Изменение цены в $, скорость изменения в $/миллисекунду
     */
    private double priceChangeThreshold, speedThreshold;



    private ImbalanceState currentState = ImbalanceState.WAIT;
    private Imbalance currentImbalance = null;



    private final TreeMap<Long, MarketEntry> data = new TreeMap<>();
    private final ArrayList<Pair<Long, MarketEntry>> lastSecondsData = new ArrayList<>();
    private final LinkedList<Imbalance> imbalances = new LinkedList<>();



    public ImbalanceService() {
        Log.info(String.format("imbalance parameters:\n    completeTime :: %d\n    speedMod :: %s\n    priceMod :: %s\n    minImbSize :: %d",
                COMPLETE_TIME, SPEED_MODIFICATOR, PRICE_MODIFICATOR, MIN_IMBALANCE_TIME_SIZE));
    }

    public void onTick(long currentTime, MarketEntry currentEntry) {
        updateData(currentTime, currentEntry);
        switch (currentState) {
            case WAIT -> detectImbalance(currentTime, currentEntry);
            case PROGRESS -> trackImbalanceProgress(currentTime, currentEntry);
            case POTENTIAL_END_POINT -> evaluatePossibleEndPoint(currentTime, currentEntry);
            case COMPLETED -> saveCompletedImbalanceAndResetState();
        }
    }

    /**
     * Идем от текущей цены назад. Определяем разницу цен и скорость изменения.
     * Если разница и скорость больше минимальных, идем дальше по циклу и смотрим когда скорость падает. Это и есть точка начала.
     */
    private void detectImbalance(long currentTime, MarketEntry currentEntry) {
        NavigableMap<Long, MarketEntry> descendingData = data.descendingMap();
//        if (!imbalances.isEmpty() && imbalances.getLast() != null) {
//            // фильтр данных идущих только после последнего имбаланса
//            descendingData = descendingData.subMap(descendingData.firstKey(), true,
//                    Math.max(imbalances.getLast().getEndTime(), descendingData.lastKey()), true);
//        }

        for (long previousTime : descendingData.keySet()) {
            if (previousTime == currentTime) {
                continue;
            }
            MarketEntry previousEntry = descendingData.get(previousTime);

            // если максимум за текущую секунду больше чем минимум за секунду из цикла на минимальное изменение цены
            //  -> потенциальный имбаланс вверх
            if (currentEntry.high() - previousEntry.low() > priceChangeThreshold) {
                double priceChange = currentEntry.high() - previousEntry.low();
                double priceChangeSpeed = priceChange / (double) (currentTime - previousTime);

                // если скорость изменения > минимальной необходимой скорости -> нашли имбаланс вверх
                // теперь нужно отследить его начало
                if (priceChangeSpeed > speedThreshold) {
                    currentImbalance = new Imbalance(previousTime, previousEntry.low(), currentTime, currentEntry.high(), Imbalance.Type.UP);
                    Log.debug("UP detected: " + currentImbalance, Instant.ofEpochMilli(data.lastKey()));
                    findImbalanceStart();
                    if (validateImbalance(currentTime)) {
                        resetImbalanceState();
                        return;
                    }
                    currentState = ImbalanceState.PROGRESS;
                    Log.debug("UP started: " + currentImbalance, Instant.ofEpochMilli(data.lastKey()));
                    return;
                }

            // если максимум за минуту из цикла меньше чем минимум за текущую секунду на минимальное изменение цены
            //  -> потенциальный имбаланс вниз
            } else if (previousEntry.high() - currentEntry.low() > priceChangeThreshold) {
                double priceChange = previousEntry.high() - currentEntry.low();
                double priceChangeSpeed = priceChange / (double) (currentTime - previousTime);

                // если скорость изменения > минимальной необходимой скорости -> нашли имбаланс вниз
                // теперь нужно отследить его начало
                if (priceChangeSpeed > speedThreshold) {
                    currentImbalance = new Imbalance(previousTime, previousEntry.high(), currentTime, currentEntry.low(), Imbalance.Type.DOWN);
                    Log.debug("DOWN detected: " + currentImbalance, Instant.ofEpochMilli(data.lastKey()));
                    findImbalanceStart();
                    if (validateImbalance(currentTime)) {
                        resetImbalanceState();
                        return;
                    }
                    currentState = ImbalanceState.PROGRESS;
                    Log.debug("DOWN started: " + currentImbalance, Instant.ofEpochMilli(data.lastKey()));
                    return;
                }
            }
        }
    }

    private boolean validateImbalance(long currentTime) {
        boolean invalid =  (currentImbalance.getEndTime() - currentImbalance.getStartTime() <= MIN_IMBALANCE_TIME_SIZE) ||
                data.subMap(currentImbalance.getStartTime(), currentTime).entrySet().stream()
                        .anyMatch(entry -> switch (currentImbalance.getType()) {
                            case UP -> entry.getValue().high() > currentImbalance.getEndPrice();
                            case DOWN -> entry.getValue().low() < currentImbalance.getEndPrice();
                        });
        Log.debug("is valid? = " + !invalid);
        return invalid;
    }

    private void findImbalanceStart() {
        // дальше берем секундные данные в обратном порядке (от большего времени к меньшему)
        // и фильтруем только те, что были до старта имбаланса
        SortedMap<Long, MarketEntry> descendingSecondsDataFromImbalanceStart = data
                .descendingMap()
                .subMap(currentImbalance.getStartTime(), true, data.firstKey(), true);

        // определяем текущую скорость роста или падения имбаланса
        double currentSpeed = Math.abs(currentImbalance.getStartPrice() - currentImbalance.getEndPrice()) /
                (double) (currentImbalance.getEndTime() - currentImbalance.getStartTime());

        // ищем начало имбаланса путем нахождения точки, до которой скорость роста или падения будет всегда меньше чем нам нужно
        switch (currentImbalance.getType()) {
            case UP -> {
                // сначала проходим всю секундную дату, чтобы найти максимально маленькое время,
                // при котором скорость роста еще сохраняется
                long possibleStartTime = currentImbalance.getStartTime();
                for (long previousTime : descendingSecondsDataFromImbalanceStart.keySet()) {
                    double previousPrice = descendingSecondsDataFromImbalanceStart.get(previousTime).low();
                    double nextSpeed = (currentImbalance.getStartPrice() - previousPrice) /
                            (double) (previousTime - currentImbalance.getStartTime());
                    if (nextSpeed > currentSpeed) {
                        possibleStartTime = previousTime;
                    }
                }
                // потом находим в образовавшемся промежутке минимум - он и является началом имбаланса
                var secondsImbalanceSubMap = data.subMap(possibleStartTime, true, currentImbalance.getEndTime(), true);
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
                            (double) (currentImbalance.getStartTime() - previousTime);
                    if (nextSpeed > currentSpeed) {
                        possibleStartTime = previousTime;
                    }
                }
                // потом находим в образовавшемся промежутке максимум - он и является началом имбаланса
                var secondsImbalanceSubMap = data.subMap(possibleStartTime, true, currentImbalance.getEndTime(), true);
                for (long time : secondsImbalanceSubMap.keySet()) {
                    double price = secondsImbalanceSubMap.get(time).high();
                    if (price > currentImbalance.getStartPrice()) {
                        currentImbalance.setStartPrice(price);
                        currentImbalance.setStartTime(time);
                    }
                }
            }
        }
        Log.debug("start corrected: " + currentImbalance, Instant.ofEpochMilli(data.lastKey()));
    }

    /**
     * ебанутая скорость (~100$/секунду)
     * Сначала сильное в одну сторону в течение скольки-то.
     * Потом если 00 то мало ждать, если 01-10 то средне ждать, если 11-89, то ждать долго и скорость обоих большая.
     * Плюс еще посмотреть может там самая большая палка, если да то снизить ожидание (больше шанс).
     * Потом когда уже, то ставить на мин или макс + ещё маленько. А если взятие 1, то переставлять.
     */
    private void trackImbalanceProgress(long currentTime, MarketEntry currentEntry) {
        if (checkProgressCondition(currentTime, currentEntry)) {
            return;
        }
        if (checkCompleteCondition(currentTime)) {
            return;
        }

        if (checkPotentialEndPointCondition(currentTime, currentEntry)) {
//            updateUI(currentTime);
            currentState = ImbalanceState.POTENTIAL_END_POINT;
        }
    }

    private boolean checkProgressCondition(long currentTime, MarketEntry currentEntry) {
        switch (currentImbalance.getType()) {
            case UP -> {
                if (currentEntry.high() >= currentImbalance.getEndPrice()) {
                    currentImbalance.setEndPrice(currentEntry.high());
                    currentImbalance.setEndTime(currentTime);
                    currentState = ImbalanceState.PROGRESS;
                    return true;
                }
            }
            case DOWN -> {
                if (currentEntry.low() <= currentImbalance.getEndPrice()) {
                    currentImbalance.setEndPrice(currentEntry.low());
                    currentImbalance.setEndTime(currentTime);
                    currentState = ImbalanceState.PROGRESS;
                    return true;
                }
            }
        }
        return false;
    }

    public boolean checkPotentialEndPointCondition(long currentTime, MarketEntry currentEntry) {
        long currentImbalanceEndTime = currentImbalance.getEndTime();

        // если максимум/минимум обновился только что, то это точно не входная точка
        if (currentImbalanceEndTime == currentTime) {
            return false;
        }
        long timeAfterImbalanceEndTime = currentTime - currentImbalanceEndTime;
        if (timeAfterImbalanceEndTime > COMPLETE_TIME * currentImbalance.size() / priceChangeThreshold) {
            return false;
        }
        boolean lowerThanTenPercentsOfImbalanceSize = Math.abs(currentImbalance.getEndPrice() - currentEntry.average()) / currentImbalance.size() < 0.15;
        if (!lowerThanTenPercentsOfImbalanceSize) {
            return false;
        }

        boolean nicePrice = nicePrice();
        boolean fastFallAfterEndPrice = fastMoveAfterEndPrice(data.get(currentImbalanceEndTime + 1000L));
        boolean movingBackwardFromImbalance = movingBackwardFromImbalance();

        if (timeAfterImbalanceEndTime <= 1000L * currentImbalance.size() / priceChangeThreshold) {
            return false;
        }
        if (timeAfterImbalanceEndTime <= 2000L * currentImbalance.size() / priceChangeThreshold) {
            return false;
        }
        if (timeAfterImbalanceEndTime <= 3000L * currentImbalance.size() / priceChangeThreshold) {
            return nicePrice && fastFallAfterEndPrice && movingBackwardFromImbalance;
        }
        if (timeAfterImbalanceEndTime <= 4000L * currentImbalance.size() / priceChangeThreshold) {
            return fastFallAfterEndPrice && movingBackwardFromImbalance;
        }

        return false;
    }

    private boolean nicePrice() {
        return switch (currentImbalance.getType()) {
            case UP -> {
                long remainder = Math.round(currentImbalance.getEndPrice()) % 100;
                yield (remainder <= 5 && remainder >= 0) || remainder == 50;
            }
            case DOWN -> {
                long remainder = Math.round(currentImbalance.getEndPrice()) % 100;
                yield remainder >= 95 || remainder == 0 || remainder == 50;
            }
        };
    }

    private boolean fastMoveAfterEndPrice(MarketEntry currentEntry) {

        return switch (currentImbalance.getType()) {
            case UP -> {
                double speed = (currentImbalance.getEndPrice() - currentEntry.low());
                yield speed > currentImbalance.speed() * 2;
            }
            case DOWN -> {
                double speed = (currentEntry.high() - currentImbalance.getEndPrice());
                yield speed > currentImbalance.speed() * 2;
            }
        };
    }

    private boolean movingBackwardFromImbalance() {
        return switch (currentImbalance.getType()) {
            case UP -> lastSecondsData.stream()
                    .filter(entry -> entry.getFirst() > currentImbalance.getEndTime())
                    .allMatch(entry ->
                            (currentImbalance.getEndPrice() - entry.getSecond().low()) / (entry.getFirst() - currentImbalance.getEndTime()) > speedThreshold
                    );
            case DOWN -> lastSecondsData.stream()
                    .filter(entry -> entry.getFirst() > currentImbalance.getEndTime())
                    .allMatch(entry ->
                            (entry.getSecond().high() - currentImbalance.getEndPrice()) / (entry.getFirst() - currentImbalance.getEndTime()) > speedThreshold
                    );
        };
    }

    private void evaluatePossibleEndPoint(long currentTime, MarketEntry currentEntry) {
        if (checkProgressCondition(currentTime, currentEntry)) {
            return;
        }
        checkCompleteCondition(currentTime);
    }

    private boolean checkCompleteCondition(long currentTime) {
        if (currentTime - currentImbalance.getEndTime() > COMPLETE_TIME * currentImbalance.size() / priceChangeThreshold) {
            Log.debug(currentImbalance.getType() + " completed at " + Instant.ofEpochMilli(currentTime));
            currentState = ImbalanceState.COMPLETED;
            return true;
        }
        return false;
    }

    private void updateData(long currentTime, MarketEntry currentEntry) {
        updateSecondsData(currentTime, currentEntry);
        updateLastSecondsData(currentTime, currentEntry);
    }

    private void updateSecondsData(long currentTime, MarketEntry currentEntry) {
        data.put(currentTime, currentEntry);
        if (currentTime - data.firstKey() > DATA_LIVE_TIME) {
            data.pollFirstEntry();
        }
    }

    private void updateLastSecondsData(long currentTime, MarketEntry currentEntry) {
        lastSecondsData.add(new Pair<>(currentTime, currentEntry));
        if (currentTime - lastSecondsData.get(0).getFirst() > LAST_SECONDS_DATA_LIVE_TIME) {
            lastSecondsData.remove(0);
        }
    }

    public LinkedList<Imbalance> getImbalances() {
        return imbalances;
    }

    @Override
    public void notify(double volatility, double average) {
        this.priceChangeThreshold = average * PRICE_MODIFICATOR;
        this.speedThreshold = average * SPEED_MODIFICATOR;

        Log.debug(String.format("new min price change: %.2f$ and speed: %.2f$/minute", priceChangeThreshold, speedThreshold * 60000));
    }

    public ImbalanceState getCurrentState() {
        return currentState;
    }

    public Imbalance getCurrentImbalance() {
        return currentImbalance;
    }

    private void saveCompletedImbalanceAndResetState() {
        imbalances.add(currentImbalance);
        resetImbalanceState();
    }


    private void resetImbalanceState() {
        currentImbalance = null;
        currentState = ImbalanceState.WAIT;
    }

    private TreeMap<Long, MarketEntry> marketData = new TreeMap<>();
    public void setMarketData(TreeMap<Long, MarketEntry> marketData) {
        this.marketData = marketData;
    }

    private void updateUI(long currentTime) {
        long delay = 10 * 60L * 1000L;
        var marketData__ = new TreeMap<>(marketData.subMap(currentImbalance.getStartTime() - delay, currentTime + delay));
        JsonUtils.updateMarketData(marketData__);
        JsonUtils.updateAnalysedData(new ArrayList<>(), List.of(currentImbalance), new ArrayList<>(), marketData__);
    }
}
