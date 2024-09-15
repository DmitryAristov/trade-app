package org.bybittradeapp.analysis.service;

import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.domain.ImbalanceState;
import org.bybittradeapp.backtest.service.Tickle;

import java.util.AbstractMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;


public class ImbalanceService implements Tickle {
    /**
     * Часть от дневной волатильности которая уже считается имбалансом.
     * Например, волатильность = 5%, средняя цена = 50000$, IMBALANCE_SIZE = 1, тогда 0.05 * 50000 * 1 = 2500 - считаем что изменение >= 2500 это имбаланс.
     * Если IMBALANCE_SIZE = 0.5, то 2500 * 0.5 = 1250 - изменение >= 2500 это имбаланс.
     */
    public static final double IMBALANCE_SIZE = 0.5;
    /**
     * Окно данных в которых происходит поиск
     */
    public static final long SEARCH_TIME_WINDOW = 6L * 60L * 60L * 1000L; // 6 hours
    /**
     * Если в течение этого времени минимуму/максимум не обновился - считаем имбаланс завершенным
     */
    public static final long POSSIBLE_COMPLETE_TIME_WINDOW = 1L * 60L * 1000L;    // 1 minute
    public static final long COMPLETE_TIME_WINDOW = 10L * 60L * 1000L;    // 10 minutes
    /**
     * Минимальная скорость изменения необходимая для того чтобы считать данное изменение имбалансом.
     * Долларов в миллисекунду
     */
    public final double speed;
    /**
     * Минимальная разница цен изменения которую можно считать имбалансом.
     */
    public final double priceThreshold;


    private final TreeMap<Long, Double> marketData;
    private TreeMap<Long, Double> data = null;
    private Imbalance imbalance = null;
    private Map.Entry<Long, Double> max, min;
    private ImbalanceState state = ImbalanceState.SEARCH_IMBALANCE;

    AtomicLong step = new AtomicLong(0L);

    public ImbalanceService(TreeMap<Long, Double> marketData, VolatilityService volatilityService) {
        this.marketData = marketData;
        this.priceThreshold = volatilityService.getVolatility() * volatilityService.getAverage() * IMBALANCE_SIZE;
        this.speed = this.priceThreshold / (120L * 60L * 1000L * IMBALANCE_SIZE);
    }

    @Override
    public void onTick(long time, double price) {
        updateData(time, price);

        if (data.isEmpty()) {
            return;
        }

        if (time == 1723473690000L) {
            System.out.print("");
        }
        switch (state) {
            case SEARCH_IMBALANCE -> {
                /* Look for imbalance. Found?
                 *    yes - change state to PROGRESS, save imbalance to local variable, return
                 *    no - { return without state change }
                 */
                double priceDiff = max.getValue() - min.getValue();
                double timeDiff = Math.abs(max.getKey() - min.getKey());

                if (priceDiff > priceThreshold && priceDiff / timeDiff > speed) {
                    imbalance = new Imbalance(min, max);
                    state = ImbalanceState.PROGRESS;
                }
            }
            case PROGRESS -> {
                /* Imbalance completed?
                 *    yes - change state to POSSIBLE_COMPLETED, return
                 *    no - { return without state change }
                 */
                if (imbalance.getType() == Imbalance.Type.UP) {
                    // If min/max is changed - update it
                    if (price > imbalance.getMax()) {
                        imbalance.setMax(price);
                        imbalance.setEnd(time);
                    }
                } else {
                    if (price < imbalance.getMin()) {
                        imbalance.setMin(price);
                        imbalance.setEnd(time);
                    }
                }
                /*
                 * Чем больше имбаланс, тем дольше нужно ждать для его подтверждения.
                 * Поэтому дополнительный множитель { разница цен } / { минимальная разница цен }
                 */
                if (time - imbalance.getEnd() >
                        POSSIBLE_COMPLETE_TIME_WINDOW * (imbalance.getMax() - imbalance.getMin()) / priceThreshold) {
                    state = ImbalanceState.POSSIBLE_COMPLETED;
                }
            }
            case POSSIBLE_COMPLETED -> {
                /* Check some time if it is stopped. If after some time it is finally stopped?
                 *    yes - change state to COMPLETED, return
                 *    no - change state to PROGRESS
                 *
                 * Before got result - { return without state change }
                 */

                if (imbalance.getType() == Imbalance.Type.UP) {
                    // If the min/max is changed -> change state to PROGRESS, update min/max
                    if (price > imbalance.getMax()) {
                        imbalance.setMax(price);
                        imbalance.setEnd(time);
                        state = ImbalanceState.PROGRESS;
                        break;
                    }
                } else {
                    if (price < imbalance.getMin()) {
                        imbalance.setMin(price);
                        imbalance.setEnd(time);
                        state = ImbalanceState.PROGRESS;
                        break;
                    }
                }
                if (time - imbalance.getEnd() >
                        COMPLETE_TIME_WINDOW * (imbalance.getMax() - imbalance.getMin()) / priceThreshold) {
                    state = ImbalanceState.COMPLETED;
                    imbalance.setCompleteTime(time);
                }

            }
            case COMPLETED -> {
                //TODO wait for SEARCH_TIME_WINDOW before reset and look for another imbalance
                if (time - imbalance.getEnd() > SEARCH_TIME_WINDOW) {
                    imbalance = null;
                    state = ImbalanceState.SEARCH_IMBALANCE;
                }

            }
        }
    }

    private void updateData(long time, double price) {
        if (data == null) {
            data = new TreeMap<>(marketData.subMap(time - SEARCH_TIME_WINDOW, true, time, true));
            max = data.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElseThrow();
            min = data.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .orElseThrow();
            step.addAndGet(time);
            return;
        }

        data.put(time, price);
        Long removed = null;
        if (data.size() > SEARCH_TIME_WINDOW / 1000L) {
            removed = data.firstKey();
            data.remove(data.firstKey());
        }

        // skip every 60 seconds when imbalance is completed or empty
        if (state == ImbalanceState.COMPLETED || state == ImbalanceState.SEARCH_IMBALANCE) {
            if (time < step.get()) {
                return;
            } else {
                step.addAndGet(20000L);
            }
        } else {
            step.set(time);
        }

        if (price > max.getValue()) {
            max = new AbstractMap.SimpleEntry<>(time, price);
        } else if (removed != null && !data.containsKey(removed)) {
            max = data.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElseThrow();
        }

        if (price < min.getValue()) {
            min = new AbstractMap.SimpleEntry<>(time, price);
        } else if (removed != null && !data.containsKey(removed)) {
            min = data.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .orElseThrow();
        }
    }

    public Imbalance getImbalance() {
        return imbalance;
    }

    public ImbalanceState getState() {
        return state;
    }
}
