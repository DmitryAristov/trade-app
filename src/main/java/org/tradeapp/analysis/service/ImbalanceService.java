package org.tradeapp.analysis.service;

import org.tradeapp.analysis.domain.Imbalance;
import org.tradeapp.analysis.domain.ImbalanceState;
import org.tradeapp.logging.Log;
import org.tradeapp.marketdata.domain.MarketEntry;
import org.tradeapp.ui.utils.TradingVueJsonUpdater;
import org.tradeapp.ui.utils.TimeFormatter;

import java.util.*;
import java.util.function.Predicate;

import static org.tradeapp.backtest.constants.Constants.*;

public class ImbalanceService implements VolatilityListener {


    private final Log log = new Log();

    /**
     * Минимальное изменение цены и минимальная скорость изменения.
     * Пересчитывается каждый день на основе волатильности и средней цены.
     * Изменение цены в $, скорость изменения в $/миллисекунду
     */
    private double priceChangeThreshold, speedThreshold;


    private ImbalanceState currentState = ImbalanceState.WAIT;
    private Imbalance currentImbalance = null;


    private final TreeMap<Long, MarketEntry> seconds = new TreeMap<>();
    private final TreeMap<Long, MarketEntry> largeData = new TreeMap<>();
    private double currentMinuteHigh = 0.;
    private double currentMinuteLow = Double.MAX_VALUE;
    private double currentMinuteVolume = 0;
    private long lastMinuteTimestamp = -1L;

    private final LinkedList<Imbalance> imbalances = new LinkedList<>();

    public ImbalanceService() {  }

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
     * Идем по секундным данным со свежих назад.
     * Находим первый имбаланс.
     * Идем еще назад и находим все имбалансы такого же типа.
     * Потом из всех находим с самым большим изменением цены, фильтруем все остальные по размеру > 0.75 * maxSize.
     * Потом из них находим с самой большой скоростью изменения цены.
     */
    private void detectImbalance(long currentTime, MarketEntry currentEntry) {
        NavigableMap<Long, MarketEntry> descendingData = seconds.descendingMap();
        Imbalance imbalance = null;
        for (long previousTime : descendingData.keySet()) {
            if (previousTime == currentTime) {
                continue;
            }
            MarketEntry previousEntry = descendingData.get(previousTime);

            if (currentEntry.high() - previousEntry.low() > priceChangeThreshold) {
                double priceChange = currentEntry.high() - previousEntry.low();
                double priceChangeSpeed = priceChange / (double) (currentTime - previousTime);
                if (priceChangeSpeed > speedThreshold * priceChangeThreshold / priceChange) {
                    imbalance = new Imbalance(previousTime, previousEntry.low(), currentTime, currentEntry.high(), Imbalance.Type.UP);
                    break;
                }
            } else if (previousEntry.high() - currentEntry.low() > priceChangeThreshold) {
                double priceChange = previousEntry.high() - currentEntry.low();
                double priceChangeSpeed = priceChange / (double) (currentTime - previousTime);

                if (priceChangeSpeed > speedThreshold * priceChangeThreshold / priceChange) {
                    imbalance = new Imbalance(previousTime, previousEntry.high(), currentTime, currentEntry.low(), Imbalance.Type.DOWN);
                    break;
                }
            }
        }
        if (imbalance == null) {
            return;
        }
        findImbalanceStart(currentTime, currentEntry, imbalance);
    }

    private void findImbalanceStart(long currentTime, MarketEntry currentEntry, final Imbalance imbalance) {
        List<Imbalance> imbalances = new ArrayList<>();
        switch (imbalance.getType()) {
            case UP -> {
                long minEntryTime = seconds.subMap(seconds.firstKey(), true, imbalance.getStartTime(), true)
                        .entrySet()
                        .stream()
                        .min(Comparator.comparing(entry -> entry.getValue().low())).orElseThrow().getKey();
                NavigableMap<Long, MarketEntry> descendingSubData = seconds.descendingMap()
                        .subMap(imbalance.getStartTime(), false, minEntryTime, true);

                for (long previousTime : descendingSubData.keySet()) {
                    MarketEntry previousEntry = descendingSubData.get(previousTime);
                    if (currentEntry.high() - previousEntry.low() > priceChangeThreshold) {
                        double priceChange = currentEntry.high() - previousEntry.low();
                        double priceChangeSpeed = priceChange / (double) (currentTime - previousTime);
                        if (priceChangeSpeed > speedThreshold * priceChangeThreshold / priceChange) {
                            imbalances.add(new Imbalance(previousTime, previousEntry.low(), currentTime, currentEntry.high(), Imbalance.Type.UP));
                        }
                    }
                }
            }
            case DOWN -> {
                long maxEntryTime = seconds.subMap(seconds.firstKey(), true, imbalance.getStartTime(), true)
                        .entrySet()
                        .stream()
                        .max(Comparator.comparing(entry -> entry.getValue().high())).orElseThrow().getKey();
                NavigableMap<Long, MarketEntry> descendingSubData = seconds.descendingMap()
                        .subMap(imbalance.getStartTime(), false, maxEntryTime, true);

                for (long previousTime : descendingSubData.keySet()) {
                    MarketEntry previousEntry = descendingSubData.get(previousTime);
                    if (previousEntry.high() - currentEntry.low() > priceChangeThreshold) {
                        double priceChange = previousEntry.high() - currentEntry.low();
                        double priceChangeSpeed = priceChange / (double) (currentTime - previousTime);
                        if (priceChangeSpeed > speedThreshold * priceChangeThreshold / priceChange) {
                            imbalances.add(new Imbalance(previousTime, previousEntry.high(), currentTime, currentEntry.low(), Imbalance.Type.DOWN));
                        }
                    }
                }
            }
        }

        imbalances.add(imbalance);
        double maxImbalanceSize = imbalances.stream().max(Comparator.comparing(Imbalance::size)).get().size();
        currentImbalance = imbalances.stream()
                .filter(imbalance_ -> imbalance_.size() >= maxImbalanceSize * 0.75)
                .filter(this::isValid)
                .max(Comparator.comparing(Imbalance::speed))
                .orElse(null);

        if (currentImbalance != null) {
            currentState = ImbalanceState.PROGRESS;
            log.debug(currentImbalance.getType() + " started: " + currentImbalance, currentTime);
        }
    }

    /**
     * Должно выполняться несколько условий:
     *  <li> первая и последняя свечи имбаланса не должны превышать половину его размера</li>
     *  <li> внутри имбаланса не должно быть экстремумов больше или меньше чем крайние точки</li>
     *  <li> до имбаланса в течение определенного времени не должно быть цены 80% от последней имбаланса. То есть до имбаланса не должно быть контр-имбаланса.</li>
     * @return true если валидный имбаланс
     */
    private boolean isValid(Imbalance imbalance) {
        return imbalance.duration() > MIN_IMBALANCE_TIME_DURATION &&
                seconds.get(imbalance.getStartTime()).size() * 2 < imbalance.size() &&
                seconds.get(imbalance.getEndTime()).size() * 2 < imbalance.size() &&
                seconds.subMap(imbalance.getStartTime(), imbalance.getEndTime()).entrySet().stream()
                        .noneMatch(entry -> switch (imbalance.getType()) {
                            case UP -> entry.getValue().high() > imbalance.getEndPrice();
                            case DOWN -> entry.getValue().low() < imbalance.getEndPrice();
                        }) &&
                largeData.entrySet().stream()
                        .filter(entry -> entry.getKey() <= imbalance.getStartTime() &&
                                entry.getKey() >= imbalance.getStartTime() - TIME_CHECK_CONTR_IMBALANCE)
                        .noneMatch(entry -> switch (imbalance.getType()) {
                            case UP -> entry.getValue().high() > imbalance.getEndPrice() - imbalance.size() * 0.25;
                            case DOWN -> entry.getValue().low() < imbalance.getEndPrice() + imbalance.size() * 0.25;
                        });
    }

    private void trackImbalanceProgress(long currentTime, MarketEntry currentEntry) {
        if (checkProgressCondition(currentTime, currentEntry)) {
            return;
        }
        if (checkCompleteCondition(currentTime)) {
            return;
        }

        if (checkPotentialEndPointCondition(currentTime, currentEntry)) {
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

    private boolean checkCompleteCondition(long currentTime) {
        double completeTime = currentImbalance.duration() * COMPLETE_TIME_MODIFICATOR;
        if (currentTime - currentImbalance.getEndTime() > Math.max(completeTime, MIN_COMPLETE_TIME)) {
            log.debug(currentImbalance.getType() + " completed: " + currentImbalance, currentTime);
            currentImbalance.setCompleteTime(currentTime);
            currentState = ImbalanceState.COMPLETED;
//            updateUI(currentTime);
            return true;
        }
        return false;
    }

    public boolean checkPotentialEndPointCondition(long currentTime, MarketEntry currentEntry) {
        double relevantSize = currentImbalance.size() / priceChangeThreshold; //TODO брать не от минимального изменения а от средней цены
        double possibleDuration = currentImbalance.duration() / relevantSize * POTENTIAL_COMPLETE_TIME_MODIFICATOR;
        if (currentTime - currentImbalance.getEndTime() < Math.max(possibleDuration, MIN_POTENTIAL_COMPLETE_TIME)) {
            return false;
        }

        if (Math.abs(currentImbalance.getEndPrice() - currentEntry.average()) / currentImbalance.size() > MAX_VALID_IMBALANCE_PART) {
            return false;
        }

        Predicate<MarketEntry> alreadyReturnedPriceCondition = switch (currentImbalance.getType()) {
            case UP -> {
                double averagePrice = currentImbalance.getEndPrice() - (currentImbalance.size() * RETURNED_PRICE_IMBALANCE_PARTITION);
                yield (marketEntry -> marketEntry.low() < averagePrice);
            }
            case DOWN -> {
                double averagePrice = currentImbalance.getEndPrice() + currentImbalance.size() * RETURNED_PRICE_IMBALANCE_PARTITION;
                yield (marketEntry -> marketEntry.high() > averagePrice);
            }
        };
        if (seconds.subMap(currentImbalance.getEndTime(), currentTime).values().stream().anyMatch(alreadyReturnedPriceCondition)) {
            return false;
        }

        currentImbalance.setComputedDuration(possibleDuration);
//        calc(currentTime);
        return true;
    }

    private void evaluatePossibleEndPoint(long currentTime, MarketEntry currentEntry) {
        if (checkProgressCondition(currentTime, currentEntry)) {
            return;
        }
        checkCompleteCondition(currentTime);
    }

    private void updateData(long currentTime, MarketEntry currentEntry) {
        seconds.put(currentTime, currentEntry);
        if (currentTime - seconds.firstKey() > DATA_LIVE_TIME) {
            seconds.pollFirstEntry();
        }

        double priceHigh = currentEntry.high();
        double priceLow = currentEntry.low();
        double volume = currentEntry.volume();

        currentMinuteVolume += volume;
        if (priceHigh > currentMinuteHigh) {
            currentMinuteHigh = priceHigh;
        }
        if (priceLow < currentMinuteLow) {
            currentMinuteLow = priceLow;
        }
        if (lastMinuteTimestamp == -1) {
            lastMinuteTimestamp = currentTime;
        }
        if (currentTime - lastMinuteTimestamp > LARGE_DATA_ENTRY_SIZE) {
            largeData.put(currentTime, new MarketEntry(currentMinuteHigh, currentMinuteLow, currentMinuteVolume));
            currentMinuteHigh = 0;
            currentMinuteLow = Double.MAX_VALUE;
            currentMinuteVolume = 0;
            lastMinuteTimestamp = currentTime;
        }
        if (!largeData.isEmpty() && currentTime - largeData.firstKey() > LARGE_DATA_LIVE_TIME) {
            largeData.pollFirstEntry();
        }
    }

    @Override
    public void notify(double volatility, double average, long currentTime) {
        this.priceChangeThreshold = average * PRICE_MODIFICATOR;
        this.speedThreshold = average * SPEED_MODIFICATOR;
        log.debug(String.format("new price change %.2f$ || speed %.2f$/minute", priceChangeThreshold, speedThreshold * 60_000L), currentTime);
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

    public LinkedList<Imbalance> getImbalances() {
        return imbalances;
    }


    private final TradingVueJsonUpdater tradingVueJsonUpdater = new TradingVueJsonUpdater("/home/dmitriy/Projects/trading-vue-js/data/data.json");
    private void updateUI(long currentTime) {
        long delay = 20 * 60L * 1000L;
        TreeMap<Long, MarketEntry> marketData__ = new TreeMap<>(data.subMap(currentImbalance.getStartTime() - delay, currentTime + delay));
        tradingVueJsonUpdater.updateMarketData(marketData__);
        tradingVueJsonUpdater.updateAnalysedData(List.of(currentImbalance), new ArrayList<>());
    }

    private TreeMap<Long, MarketEntry> data;

    public void setData(TreeMap<Long, MarketEntry> data) {
        this.data = data;
    }

    private void calc(long currentTime_) {
        var marketData_ = data.subMap(currentImbalance.getStartTime(), currentTime_).entrySet().stream().toList();

        log.debug(currentImbalance.toString(), currentTime_);
        log.debug(String.format("price %.2f$ || time %ds || speed = %.2f$/s",
                currentImbalance.size(), currentImbalance.duration() / 1000, currentImbalance.speed() * 1000), currentTime_);

        for (int duration = 1; duration < 120; duration++) {
            List<PossibleCompleteAnalysisDTO> possibleCompletePoints = getPossibleCompleteAnalysisDTOS(marketData_, duration);
            if (possibleCompletePoints.isEmpty() || possibleCompletePoints.size() > 4 ||
                    possibleCompletePoints.stream().noneMatch(possibleCompletePoint -> possibleCompletePoint.imbalance.getEndPrice() == currentImbalance.getEndPrice())) {
                continue;
            }

            log.debug(String.format("duration = %ds || points found %d", duration, possibleCompletePoints.size()), currentTime_);
            for (PossibleCompleteAnalysisDTO possibleCompletePoint : possibleCompletePoints) {
                double relevantSize = possibleCompletePoint.imbalance.size() / priceChangeThreshold;
                long timeSize = possibleCompletePoint.imbalance.duration();
                double possibleDuration = timeSize / relevantSize * POTENTIAL_COMPLETE_TIME_MODIFICATOR;

                log.debug(String.format("      open position %.2f$ on %s || imbalance price %.2f$ time %ds speed = %.2f$/s || partition = %.2f || duration = %.2fms",
                        possibleCompletePoint.currentEntry.average(),
                        TimeFormatter.format(possibleCompletePoint.currentTime),
                        possibleCompletePoint.imbalance.size(),
                        possibleCompletePoint.imbalance.duration(),
                        possibleCompletePoint.imbalance.speed(),
                        possibleCompletePoint.partition,
                        possibleDuration
                ), currentTime_);
            }
        }
    }

    private List<PossibleCompleteAnalysisDTO> getPossibleCompleteAnalysisDTOS(List<Map.Entry<Long, MarketEntry>> marketData_, int duration) {
        Imbalance currentTempImbalance = Imbalance.of(currentImbalance.getStartTime(),
                currentImbalance.getStartPrice(),
                currentImbalance.getStartTime(),
                currentImbalance.getStartPrice(),
                currentImbalance.getType()
        );
        List<PossibleCompleteAnalysisDTO> possibleCompletePoints = new ArrayList<>();

        for (Map.Entry<Long, MarketEntry> currentEntry : marketData_) {
            switch (currentTempImbalance.getType()) {
                case UP -> {
                    if (currentEntry.getValue().high() > currentTempImbalance.getEndPrice()) {
                        currentTempImbalance.setEndPrice(currentEntry.getValue().high());
                        currentTempImbalance.setEndTime(currentEntry.getKey());
                    }
                    if (currentEntry.getKey() - currentTempImbalance.getEndTime() == duration * 1000L &&
                            currentTempImbalance.getEndPrice() - currentTempImbalance.getStartPrice() > priceChangeThreshold) {
                        double partition = Math.abs(currentTempImbalance.getEndPrice() - currentEntry.getValue().average()) / currentTempImbalance.size();
                        if (partition <= 0.16) {
                            possibleCompletePoints.add(new PossibleCompleteAnalysisDTO(Imbalance.of(currentTempImbalance), currentEntry.getKey(), currentEntry.getValue(), partition));
                        }
                    }
                }
                case DOWN -> {
                    if (currentEntry.getValue().low() < currentTempImbalance.getEndPrice()) {
                        currentTempImbalance.setEndPrice(currentEntry.getValue().low());
                        currentTempImbalance.setEndTime(currentEntry.getKey());
                    }
                    if (currentEntry.getKey() - currentTempImbalance.getEndTime() == duration * 1000L &&
                            currentTempImbalance.getStartPrice() - currentTempImbalance.getEndPrice() > priceChangeThreshold) {
                        double partition = Math.abs(currentTempImbalance.getEndPrice() - currentEntry.getValue().average()) / currentTempImbalance.size();
                        if (partition <= 0.16) {
                            possibleCompletePoints.add(new PossibleCompleteAnalysisDTO(Imbalance.of(currentTempImbalance), currentEntry.getKey(), currentEntry.getValue(), partition));
                        }
                    }
                }
            }
        }
        return possibleCompletePoints;
    }

    private record PossibleCompleteAnalysisDTO(Imbalance imbalance, long currentTime, MarketEntry currentEntry, double partition) {  }
}
