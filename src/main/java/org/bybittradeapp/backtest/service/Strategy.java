package org.bybittradeapp.backtest.service;

import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.domain.ImbalanceState;
import org.bybittradeapp.analysis.service.ExtremumService;
import org.bybittradeapp.analysis.service.ImbalanceService;
import org.bybittradeapp.analysis.service.TrendService;
import org.bybittradeapp.backtest.domain.Account;
import org.bybittradeapp.backtest.domain.ExecutionType;
import org.bybittradeapp.backtest.domain.Order;
import org.bybittradeapp.backtest.domain.OrderType;
import org.bybittradeapp.backtest.domain.Position;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;

import java.util.List;
import java.util.TreeMap;

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
    private static final double FIRST_TAKE_PROFIT_THRESHOLD = 0.5;
    private static final double SECOND_TAKE_PROFIT_THRESHOLD = 1.;
    private static final double STOP_LOSS_MODIFICATOR = 0.01;
    private static final double MAX_VALID_IMBALANCE_PART_FOR_POSITION = 0.2;
    private static final boolean TWO_TAKES = true;

    private final ExchangeSimulator simulator;
    private final TreeMap<Long, MarketEntry> marketData;
    private final ImbalanceService imbalanceService;
    private final ExtremumService extremumService;

    private final TrendService trendService;
    private final Account account;
    public State state = State.WAIT_IMBALANCE;

    public Strategy(ExchangeSimulator simulator,
                    TreeMap<Long, MarketEntry> marketData,
                    ImbalanceService imbalanceService,
                    ExtremumService extremumService,
                    TrendService trendService,
                    Account account) {
        this.simulator = simulator;
        this.marketData = marketData;
        this.imbalanceService = imbalanceService;
        this.extremumService = extremumService;
        this.trendService = trendService;
        this.account = account;
        Log.info(String.format("strategy parameters:\n    firstTake :: %.2f\n    secondTake :: %.2f\n    stopModificator :: %.2f\n    maxValidImbSize :: %.2f\n    2_takes :: %b",
                FIRST_TAKE_PROFIT_THRESHOLD, SECOND_TAKE_PROFIT_THRESHOLD, STOP_LOSS_MODIFICATOR, MAX_VALID_IMBALANCE_PART_FOR_POSITION, TWO_TAKES));
    }

    public void onTick(long currentTime, MarketEntry currentEntry) {

        //TODO(1) Отслеживать был ли взят имбаланс. Если нет -> брать принудительно если цена еще не сильно вернулась обратно.
        // Переписать имбаланс сервис так чтобы при поиске имбаланса использовались только минутные данные.
        // Основное время программа ищет имбаланс и нет нужды в секундных данных.

        switch (state) {
            case WAIT_IMBALANCE -> {
                /*
                 * Is imbalance present?
                 *    yes - change state to ENTRY_POINT_SEARCH
                 *    no - { return without state change }
                 */
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
                if (imbalanceService.getCurrentState() == ImbalanceState.COMPLETED) {
                    openPositions(currentTime, currentEntry);
                    state = State.POSITIONS_OPENED;
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
                if (positions.isEmpty()) {
                    state = State.WAIT_IMBALANCE;
                } else if (positions.size() == 1) {
                    Position position = positions.get(0);
                    if (position.getProfitLoss() > (position.getOpenFee() + position.getCloseFee()) * 2.) {
                        position.setZeroLoss();
                        state = State.WAIT_POSITIONS_CLOSED;
                    }
                }
            }
            case WAIT_POSITIONS_CLOSED -> {
                List<Position> positions = simulator.getOpenPositions();
                if (positions.isEmpty()) {
                    state = State.WAIT_IMBALANCE;
                }
            }
        }
    }

    private void openPositions(long currentTime, MarketEntry currentEntry) {
        if (!simulator.getOpenPositions().isEmpty()) {
            throw new RuntimeException("Trying to open position while already opened " + simulator.getOpenPositions().size());
        }

        Imbalance imbalance = imbalanceService.getCurrentImbalance();
        double imbalanceSize = imbalance.size();
        boolean lowerThanTenPercentsOfImbalanceSize = Math.abs(imbalance.getEndPrice() - currentEntry.average()) / imbalanceSize < MAX_VALID_IMBALANCE_PART_FOR_POSITION;
        if (!lowerThanTenPercentsOfImbalanceSize) {
            return;
        }
        Log.debug("imbalance when open position :: " + imbalance);

        Order marketOrder1 = new Order();
        marketOrder1.setExecutionType(ExecutionType.MARKET);
        double sl = imbalance.getEndPrice();

        double tp = 0;
        switch (imbalance.getType()) {
            case UP -> {
                marketOrder1.setType(OrderType.SHORT);
                tp = imbalance.getEndPrice() - FIRST_TAKE_PROFIT_THRESHOLD * imbalanceSize;
                sl += imbalanceSize * STOP_LOSS_MODIFICATOR;
            }
            case DOWN -> {
                marketOrder1.setType(OrderType.LONG);
                tp = imbalance.getEndPrice() + FIRST_TAKE_PROFIT_THRESHOLD * imbalanceSize;
                sl -= imbalanceSize * STOP_LOSS_MODIFICATOR;
            }
        }
        marketOrder1.setTP_SL(tp, sl);
        marketOrder1.setCreateTime(currentTime);
        marketOrder1.setMoneyAmount(account.calculatePositionSize());

        if (TWO_TAKES) {
            Order marketOrder2 = new Order();
            marketOrder2.setExecutionType(ExecutionType.MARKET);
            sl = imbalance.getEndPrice();

            tp = 0;
            switch (imbalance.getType()) {
                case UP -> {
                    marketOrder2.setType(OrderType.SHORT);
                    tp = imbalance.getEndPrice() - SECOND_TAKE_PROFIT_THRESHOLD * imbalanceSize;
                    sl += imbalanceSize * STOP_LOSS_MODIFICATOR;
                }
                case DOWN -> {
                    marketOrder2.setType(OrderType.LONG);
                    tp = imbalance.getEndPrice() + SECOND_TAKE_PROFIT_THRESHOLD * imbalanceSize;
                    sl -= imbalanceSize * STOP_LOSS_MODIFICATOR;
                }
            }
            marketOrder2.setTP_SL(tp, sl);
            marketOrder2.setCreateTime(currentTime);
            marketOrder1.setMoneyAmount(0.5 * account.calculatePositionSize());
            marketOrder2.setMoneyAmount(0.5 * account.calculatePositionSize());

            simulator.submitOrder(marketOrder2, currentTime, currentEntry);
        }
        simulator.submitOrder(marketOrder1, currentTime, currentEntry);
    }
}