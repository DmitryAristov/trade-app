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

import java.util.List;
import java.util.TreeMap;

public class Strategy implements Tickle {
    public enum State {
        WAIT_IMBALANCE,
        ENTRY_POINT_SEARCH,
        POSSIBLE_ENTRY_POINT,
        POSITION_OPENED,
        WAIT_POSITION_CLOSED
    }

    /**
     * Стоп лосс цена - в процентах от размера имбаланса
     * Пример:
     * тип имбаланса = вверх
     * нижняя цена = 10000$
     * верхняя цена = 15000$
     * тип позиции = SHORT
     * TP = 15000 - (15000 - 10000) * 0.3 = 13500$
     * SL = 15000 + (15000 - 10000) * 0.1 = 15500$
     */
    private static final double POSSIBLE_STOP_LOSS_THRESHOLD = 0.02;
    private static final double STOP_LOSS_THRESHOLD = 0.15;
    /**
     * Тейк профит цена - в процентах от размера имбаланса
     */
    private static final double TAKE_PROFIT_THRESHOLD = 0.6;
    private static final long POSITION_LIVE_TIME = 10L * 24L * 60L * 60L * 1000L; // 10 days

    private final ExchangeSimulator simulator;
    private final TreeMap<Long, Double> marketData;
    private final ImbalanceService imbalanceService;
    private final ExtremumService extremumService;
    private final TrendService trendService;
    private final Account account;
    public State state = State.WAIT_IMBALANCE;

    public Strategy(ExchangeSimulator simulator,
                    TreeMap<Long, Double> marketData,
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

    @Override
    public void onTick(long time, double price) {
        //TODO
        // Перед открытием позиции нужно проверить зоны поддержки и сопротивления.
        // Обычно имбаланс заканчивается на этих зонах (например поддержки) и потом доходит обратно
        // к ближайщей или следующей противоположной (сопротивления) зоне
        // var extremums = extremumService.getExtremums();

        switch (state) {
            case WAIT_IMBALANCE -> {
                /*
                 * Is imbalance present?
                 *    yes - change state to ENTRY_POINT_SEARCH
                 *    no - { return without state change }
                 */
                if (imbalanceService.getState() == ImbalanceState.IMBALANCE_IN_PROGRESS) {
                    state = State.ENTRY_POINT_SEARCH;
                }
            }
            case ENTRY_POINT_SEARCH -> {
                /*
                 * Is imbalance completed?
                 *    yes - change state to POSSIBLE_ENTRY_POINT
                 *    no - { return without state change }
                 */
                if (imbalanceService.getState() == ImbalanceState.POTENTIAL_END_POINT_FOUND) {
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
                Imbalance imbalance = imbalanceService.getImbalance();

                Order marketOrder = new Order();
                marketOrder.setExecutionType(ExecutionType.MARKET);

                double tp, sl;
                double imbalanceSize = Math.abs(imbalance.getStartPrice() - imbalance.getEndPrice());
                if (imbalance.getType() == Imbalance.Type.UP) {
                    marketOrder.setType(OrderType.SHORT);
                    tp = price - TAKE_PROFIT_THRESHOLD * imbalanceSize;
                    sl = price + POSSIBLE_STOP_LOSS_THRESHOLD * imbalanceSize;
                } else {
                    marketOrder.setType(OrderType.LONG);
                    tp = price + TAKE_PROFIT_THRESHOLD * imbalanceSize;
                    sl = price - POSSIBLE_STOP_LOSS_THRESHOLD * imbalanceSize;
                }
                marketOrder.setPrice(price);
                marketOrder.setTP_SL(tp, sl);
                marketOrder.setCreateTime(time);

                simulator.submitOrder(marketOrder, time);
                state = State.POSITION_OPENED;
            }
            case POSITION_OPENED -> {
                /*
                 * Wait for the price moving to stop loss or take profit.
                 * Control opened position.
                 * Position is closed?
                 *    yes - change state to IMBALANCE_SEARCH
                 *    no - { return without state change }
                 */
                if (imbalanceService.getState() == ImbalanceState.IMBALANCE_IN_PROGRESS) {
                    List<Position> positions = simulator.getOpenPositions();
                    if (!positions.isEmpty()) {
                        positions.forEach(position -> ExchangeSimulator.closePosition(position, account, time, price));
                    }
                    List<Order> limitOrders = simulator.getLimitOrders();
                    limitOrders.forEach(Order::cancel);
                }

                if (imbalanceService.getState() == ImbalanceState.IMBALANCE_COMPLETED) {
                    List<Position> positions = simulator.getOpenPositions();
                    if (positions.isEmpty()) {
                        state = State.WAIT_IMBALANCE;
                    } else {
                        positions.forEach(position -> correctTP_SL(position, price));
                        state = State.WAIT_POSITION_CLOSED;
                    }
                }
            }
            case WAIT_POSITION_CLOSED -> {
                List<Position> positions = simulator.getOpenPositions();
                if (positions.isEmpty()) {
                    state = State.WAIT_IMBALANCE;
                }
                positions.forEach(position -> closeByTimeout(time, price, position));
            }
        }
    }

    private void correctTP_SL(Position position, double price) {
        Imbalance imbalance = imbalanceService.getImbalance();
        double imbalanceSize = Math.abs(imbalance.getStartPrice() - imbalance.getEndPrice());
        double sl = switch (position.getOrder().getType()) {
            case SHORT -> price + STOP_LOSS_THRESHOLD * imbalanceSize;
            case LONG -> price - STOP_LOSS_THRESHOLD * imbalanceSize;
        };
        position.setStopLoss(sl);
    }

    private void closeByTimeout(long time, double price, Position position) {
        if (time - position.getOrder().getCreateTime() > POSITION_LIVE_TIME) {
            ExchangeSimulator.closePosition(position, account, time, price);
        }
    }

    public State getState() {
        return state;
    }
}