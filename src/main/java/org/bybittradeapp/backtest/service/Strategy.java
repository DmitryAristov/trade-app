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
        POSITIONS_OPENED,
        WAIT_POSITIONS_CLOSED
    }

    private static final double STOP_LOSS_THRESHOLD = 0.02;
    private static final double FIRST_TAKE_PROFIT_THRESHOLD = 0.4;
    private static final double SECOND_TAKE_PROFIT_THRESHOLD = 0.8;
    private static final double THIRD_TAKE_PROFIT_THRESHOLD = 1.2;

    private final ExchangeSimulator simulator;
    private final TreeMap<Long, Double> marketData;
    private final ImbalanceService imbalanceService;
    private final ExtremumService extremumService;

    //TODO в будущем проанализировать зависимость от тренда.
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


        //TODO: добавить комиссии биржи и добавить изменение стоп лосса с учетом комиссий.
        // Переписать имбаланс сервис так чтобы при поиске имбаланса использовались только минутные данные.
        // Основное время программа ищет имбаланс и нет нужды в секундных данных.


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
                marketOrder1.setPrice(price);
                marketOrder1.setTP_SL(tp, sl);
                marketOrder1.setCreateTime(time);
                marketOrder1.setMoneyAmount(0.5 * account.calculatePositionSize());


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
                marketOrder2.setPrice(price);
                marketOrder2.setTP_SL(tp, sl);
                marketOrder2.setCreateTime(time);
                marketOrder2.setMoneyAmount(0.25 * account.calculatePositionSize());


                Order marketOrder3 = new Order();
                marketOrder3.setExecutionType(ExecutionType.MARKET);
                if (imbalance.getType() == Imbalance.Type.UP) {
                    marketOrder3.setType(OrderType.SHORT);
                    tp = imbalance.getEndPrice() - THIRD_TAKE_PROFIT_THRESHOLD * imbalanceSize;
                    sl = price + STOP_LOSS_THRESHOLD * imbalanceSize;
                } else {
                    marketOrder3.setType(OrderType.LONG);
                    tp = imbalance.getEndPrice() + THIRD_TAKE_PROFIT_THRESHOLD * imbalanceSize;
                    sl = price - STOP_LOSS_THRESHOLD * imbalanceSize;
                }
                marketOrder3.setPrice(price);
                marketOrder3.setTP_SL(tp, sl);
                marketOrder3.setCreateTime(time);
                marketOrder3.setMoneyAmount(0.25 * account.calculatePositionSize());

                simulator.submitOrder(marketOrder1, time);
                simulator.submitOrder(marketOrder2, time);
                simulator.submitOrder(marketOrder3, time);

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

                if (positions.size() == 3) {
                    positions.forEach(position -> {
                        if (position.getProfitLoss() * account.getCredit() > position.getOpenCommission() * 3) {
                            setZeroLoss(position);
                        }
                    });
                }

                if (positions.stream().allMatch(Position::isZeroLoss)) {
                    state = State.WAIT_POSITIONS_CLOSED;
                }
            }
            case WAIT_POSITIONS_CLOSED -> {
                List<Position> positions = simulator.getOpenPositions();
                if (positions.size() == 2 || positions.size() == 1) {
                    simulator.getLastClosedPosition().ifPresent(lastClosedPosition -> {
                        positions.forEach(position -> {
                            switch (position.getOrder().getType()) {
                                case LONG -> {
                                    position.setStopLoss(lastClosedPosition.getClosePrice() -
                                            0.1 * (position.getOpenPrice() - lastClosedPosition.getClosePrice()));
                                }
                                case SHORT -> {
                                    position.setStopLoss(lastClosedPosition.getClosePrice() +
                                            0.1 * (position.getOpenPrice() - lastClosedPosition.getClosePrice()));
                                }
                            }
                        });
                    });
                }

                if (positions.isEmpty()) {
                    state = State.WAIT_IMBALANCE;
                }
            }
        }
    }

    private void setZeroLoss(Position position) {

        // LONG: profitLoss = (closePrice - openPrice) * amount * credit and must be > twoEmission ->
        //       closePrice - openPrice > twoEmission / (amount * credit)
        //       closePrice > openPrice + twoEmission / (amount * credit)
        // SHORT: same, but   openPrice - twoEmission / (amount * credit)
        double twoEmissionProfitPrice = (position.getOpenCommission() * 2) / (position.getAmount() * account.getCredit());
        switch (position.getOrder().getType()) {
            case LONG -> position.setStopLoss(position.getOpenPrice() + twoEmissionProfitPrice);
            case SHORT -> position.setStopLoss(position.getOpenPrice() - twoEmissionProfitPrice);
        }
    }
}