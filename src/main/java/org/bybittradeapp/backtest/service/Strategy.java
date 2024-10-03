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
        POSSIBLE_ENTRY_POINT,
        POSITIONS_OPENED,
        WAIT_POSITIONS_CLOSED
    }

    /**
     * Начальная стоп цена, перед которой еще не выставлена без-убыточная стоп цена
     */
    private static final double STOP_LOSS_THRESHOLD = 0.02;
    /**
     * Части от размера имбаланса для первого и второго тейка в случае если закрытие одной позиции происходит по частям.
     *  Пример: имбаланс был 55000$ -> 59000$. Тогда его размер = 4000$.
     *          При SHORT сделке первый тейк будет выставлен на 59000 - 4000 * 0.4 = 57400$.
     */
    private static final double FIRST_TAKE_PROFIT_THRESHOLD = 0.4;
    private static final double SECOND_TAKE_PROFIT_THRESHOLD = 0.8;

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
    }

    public void onTick(long time, MarketEntry marketEntry) {

        //TODO(1) Отслеживать был ли взят имбаланс. Если нет -> брать принудительно если цена еще не сильно вернулась обратно.
        // Переписать имбаланс сервис так чтобы при поиске имбаланса использовались только минутные данные.
        // Основное время программа ищет имбаланс и нет нужды в секундных данных.

        double price = (marketEntry.high() + marketEntry.low()) / 2.;

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
                if (imbalanceService.getCurrentState() == ImbalanceState.POTENTIAL_END_POINT) {
                    state = State.POSSIBLE_ENTRY_POINT;
                }
            }
            case POSSIBLE_ENTRY_POINT -> {
                /*
                 * Open position with stop loss near to open price.
                 * After some time position is closed automatically (or manually)?
                 *    yes - { return without state change }
                 *    no - change state to POSITION_OPENED
                 */
                Imbalance imbalance = imbalanceService.getCurrentImbalance();


                Order marketOrder1 = new Order();
                marketOrder1.setExecutionType(ExecutionType.MARKET);
                double tp, sl;
                double imbalanceSize = Math.abs(imbalance.getStartPrice() - imbalance.getEndPrice());
                if (imbalance.getType() == Imbalance.Type.UP) {
                    marketOrder1.setType(OrderType.SHORT);
                    tp = imbalance.getEndPrice() - FIRST_TAKE_PROFIT_THRESHOLD * imbalanceSize;
                    sl = price + STOP_LOSS_THRESHOLD * imbalanceSize;
                } else {
                    marketOrder1.setType(OrderType.LONG);
                    tp = imbalance.getEndPrice() + FIRST_TAKE_PROFIT_THRESHOLD * imbalanceSize;
                    sl = price - STOP_LOSS_THRESHOLD * imbalanceSize;
                }
                marketOrder1.setExecutionPrice(price);
                marketOrder1.setTP_SL(tp, sl);
                marketOrder1.setCreateTime(time);
                marketOrder1.setMoneyAmount(0.6 * account.calculatePositionSize());


                Order marketOrder2 = new Order();
                marketOrder2.setExecutionType(ExecutionType.MARKET);
                if (imbalance.getType() == Imbalance.Type.UP) {
                    marketOrder2.setType(OrderType.SHORT);
                    tp = imbalance.getEndPrice() - SECOND_TAKE_PROFIT_THRESHOLD * imbalanceSize;
                    sl = price + STOP_LOSS_THRESHOLD * imbalanceSize;
                } else {
                    marketOrder2.setType(OrderType.LONG);
                    tp = imbalance.getEndPrice() + SECOND_TAKE_PROFIT_THRESHOLD * imbalanceSize;
                    sl = price - STOP_LOSS_THRESHOLD * imbalanceSize;
                }
                marketOrder2.setTP_SL(tp, sl);
                marketOrder2.setCreateTime(time);
                marketOrder2.setMoneyAmount(0.4 * account.calculatePositionSize());

                simulator.submitOrder(marketOrder1, time, price);
                simulator.submitOrder(marketOrder2, time, price);

                state = State.POSITIONS_OPENED;
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
                    state = State.ENTRY_POINT_SEARCH;
                }

                if (positions.size() == 2) {
                    positions.forEach(position -> {
                        if (position.getProfitLoss() > (position.getOpenFee() + position.getCloseFee()) * 1.2) {
                            position.setZeroLoss();
                        }
                    });
                }

                if (positions.stream().allMatch(Position::isZeroLoss)) {
                    state = State.WAIT_POSITIONS_CLOSED;
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
}