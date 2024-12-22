package org.tradeapp.backtest.service;

import org.tradeapp.analysis.domain.Imbalance;
import org.tradeapp.analysis.domain.ImbalanceState;
import org.tradeapp.analysis.service.ImbalanceService;
import org.tradeapp.backtest.domain.Account;
import org.tradeapp.backtest.domain.ExecutionType;
import org.tradeapp.backtest.domain.Order;
import org.tradeapp.backtest.domain.OrderType;
import org.tradeapp.backtest.domain.Position;
import org.tradeapp.logging.Log;
import org.tradeapp.marketdata.domain.MarketEntry;
import org.tradeapp.ui.domain.MarketKlineEntry;
import org.tradeapp.ui.utils.TradingVueJsonUpdater;

import java.util.*;

/**
 * Класс описывающий стратегию открытия и закрытия сделок на основе технического анализа
 */
public class Strategy {
    /**
     * Текущее состояние программы.
     * Lifecycle:
     *   1. ждет имбаланс
     *   2. имбаланс появился, ищет точку входа
     *   3. точка входа найдена, открывает позицию(и)
     *   4. ожидает закрытия позиции(й)
     */
    public enum State {
        WAIT_IMBALANCE,
        ENTRY_POINT_SEARCH,
        POSITIONS_OPENED,
        WAIT_POSITIONS_CLOSED
    }

    /**
     * Части от размера имбаланса для первого и второго тейка в случае если закрытие одной позиции происходит по частям.
     *  Пример: имбаланс был 55000$ -> 59000$. Тогда его размер = 4000$.
     *          При SHORT сделке первый тейк будет выставлен на 59000 - 4000 * 0.4 = 57400$.
     */
    private static final int TAKES_COUNT = 2;
    private static final double[] TAKE_PROFIT_THRESHOLDS = new double[]{0.5, 0.75};
    private static final double STOP_LOSS_MODIFICATOR = 0.01;
    private static final long POSITION_LIVE_TIME = 240 * 60_000L;

    private final ExchangeSimulator simulator;
    private final TreeMap<Long, MarketEntry> marketData;
    private final TreeMap<Long, MarketKlineEntry> uiMarketData;
    private final ImbalanceService imbalanceService;

    private final Account account;
    public State state = State.WAIT_IMBALANCE;
    private int openTimes = 0;

    public Strategy(ExchangeSimulator simulator,
                    TreeMap<Long, MarketEntry> marketData,
                    TreeMap<Long, MarketKlineEntry> uiMarketData,
                    ImbalanceService imbalanceService,
                    Account account) {
        this.simulator = simulator;
        this.marketData = marketData;
        this.uiMarketData = uiMarketData;
        this.imbalanceService = imbalanceService;
        this.account = account;
        //noinspection ConstantValue
        if (TAKES_COUNT > TAKE_PROFIT_THRESHOLDS.length) {
            throw new RuntimeException("foreach take modifier must be defined");
        }

        Log.info(String.format("""
                        strategy parameters:
                            takes count :: %d
                            takes modifiers :: %s
                            stop modificator :: %.2f
                            position live time :: %d minutes""",
                TAKES_COUNT,
                Arrays.toString(TAKE_PROFIT_THRESHOLDS),
                STOP_LOSS_MODIFICATOR,
                POSITION_LIVE_TIME/60_000L));
    }

    public void onTick(long currentTime, MarketEntry currentEntry) {
        if (imbalanceService.getCurrentState() == ImbalanceState.COMPLETED) {
            lastImbalance = imbalanceService.getCurrentImbalance();
        }

        switch (state) {
            case WAIT_IMBALANCE -> {
                /*
                 * Is imbalance present?
                 *    yes - change state to ENTRY_POINT_SEARCH
                 *    no - { return without state change }
                 */
                lastImbalance = null;
                openTimes = 0;
                if (imbalanceService.getCurrentState() == ImbalanceState.PROGRESS) {
                    state = State.ENTRY_POINT_SEARCH;
                }
            }
            case ENTRY_POINT_SEARCH -> {
                /*
                 * Is imbalance completed?
                 *    yes - change state to POSSIBLE_ENTRY_POINT
                 *    no - { return without state change }
                 */
                if (imbalanceService.getCurrentState() == ImbalanceState.POTENTIAL_END_POINT) {
                    if (openPositions(currentTime, currentEntry)) {
                        openTimes++;
                        state = State.POSITIONS_OPENED;
                    } else {
                        state = State.WAIT_IMBALANCE;
                    }
                }
            }
            case POSITIONS_OPENED -> {
                /*
                 * Wait for the price moving to stop loss or take profit.
                 * Control opened position.
                 * Position is closed?
                 *    yes - change state to IMBALANCE_IN_PROGRESS
                 *    no - { return without state change }
                 */
                List<Position> positions = simulator.getOpenPositions();
                closeByTimeout(currentTime, currentEntry, positions);

                positions = simulator.getOpenPositions();
                if (TAKES_COUNT == 1) {
                    state = State.WAIT_POSITIONS_CLOSED;
                } else {
                    if (positions.isEmpty()) {
                        // все закрылось в убыток -> снова ждем имбаланс
                        if (lastImbalance != null) {
//                        updateUI(currentTime);
                            System.out.print("");
                        }
                        state = State.WAIT_IMBALANCE;
                    } else if (positions.size() == TAKES_COUNT - 1) {
                        // взят первый тейк -> ставим всем остальным без-убыток
                        positions.forEach(position -> {
                            if (position.getProfitLoss() > (position.getOpenFee() + position.getCloseFee()) * 2.) {
                                position.setZeroLoss();
                                state = State.WAIT_POSITIONS_CLOSED;
                            }
                        });
                    }
                }
            }
            case WAIT_POSITIONS_CLOSED -> {
                List<Position> positions = simulator.getOpenPositions();
                closeByTimeout(currentTime, currentEntry, positions);

                positions = simulator.getOpenPositions();
                if (positions.isEmpty()) {
                    if (lastImbalance != null) {
//                        updateUI(currentTime);
                        System.out.print("");
                    }
                    state = State.WAIT_IMBALANCE;
                }
            }
        }
    }

    private boolean openPositions(long currentTime, MarketEntry currentEntry) {
        if (!simulator.getOpenPositions().isEmpty()) {
            throw new RuntimeException("Trying to open position while already opened " + simulator.getOpenPositions().size());
        }
//        if (openTimes >= 3) {
//            Log.debug("too much attempts for this imbalance");
//            return false;
//        }

        Imbalance imbalance = imbalanceService.getCurrentImbalance();
        double imbalanceSize = imbalance.size();
        Log.debug("imbalance when open position :: " + imbalance);

        List<Order> marketOrders = new ArrayList<>();
        for (int i = 0; i < TAKES_COUNT; i++) {
            Order marketOrder = new Order();
            marketOrder.setImbalance(imbalance);
            marketOrder.setExecutionType(ExecutionType.MARKET);
            double sl = imbalance.getEndPrice();

            double tp = 0;
            switch (imbalance.getType()) {
                case UP -> {
                    marketOrder.setType(OrderType.SHORT);
                    tp = imbalance.getEndPrice() - TAKE_PROFIT_THRESHOLDS[i] * imbalanceSize;
                    sl += imbalanceSize * STOP_LOSS_MODIFICATOR;
                }
                case DOWN -> {
                    marketOrder.setType(OrderType.LONG);
                    tp = imbalance.getEndPrice() + TAKE_PROFIT_THRESHOLDS[i] * imbalanceSize;
                    sl -= imbalanceSize * STOP_LOSS_MODIFICATOR;
                }
            }
            marketOrder.setTP_SL(tp, sl);
            marketOrder.setCreateTime(currentTime);
            marketOrder.setMoneyAmount(account.calculatePositionSize() / (double) TAKES_COUNT);
            marketOrders.add(marketOrder);
        }

        marketOrders.forEach(marketOrder -> simulator.submitOrder(marketOrder, currentTime, currentEntry));
        return true;
    }

    private void closeByTimeout(long currentTime, MarketEntry currentEntry, List<Position> positions) {
        positions.forEach(position -> {
            if (currentTime - position.getOpenTime() > POSITION_LIVE_TIME) {
                Log.debug(String.format("close positions with timeout %d minutes", POSITION_LIVE_TIME / 60_000L));
                position.close(currentTime, currentEntry.average());
                account.updateBalance(position);
            }
        });
    }


    private final TradingVueJsonUpdater tradingVueJsSecondsUtils = new TradingVueJsonUpdater("/home/dmitriy/Projects/trading-vue-js/data/data.json");
    private final TradingVueJsonUpdater tradingVueJsMinutesUtils = new TradingVueJsonUpdater("/home/dmitriy/Projects/trading-vue-js-2/data/data.json");

    private void updateUI(long currentTime) {
        List<Position> positions = simulator.getPositions();
        List<Position> imbPositions = positions
                .stream()
                .filter(position ->
                        position.getOpenTime() > lastImbalance.getStartTime() &&
                                position.getOpenTime() < lastImbalance.getCompleteTime())
                .toList();

        long delay = 10 * 60L * 1000L;
        tradingVueJsSecondsUtils.updateMarketData(new TreeMap<>(marketData.subMap(lastImbalance.getStartTime() - delay, currentTime + delay)));
        tradingVueJsSecondsUtils.updateAnalysedData(List.of(lastImbalance), imbPositions);

        long imbMinuteKey = uiMarketData.keySet().stream()
                .min(Comparator.comparing(key -> Math.abs(key - lastImbalance.getStartTime())))
                .orElseThrow();

        long minutesDelay = 600 * 15L * 60L * 1000L;
        tradingVueJsMinutesUtils.updateUiMarketData(new TreeMap<>(uiMarketData.subMap(imbMinuteKey - minutesDelay, imbMinuteKey + minutesDelay)));
    }

    Imbalance lastImbalance = null;
}