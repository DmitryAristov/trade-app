package org.bybittradeapp.backtest.service;

import org.bybittradeapp.backtest.domain.Account;
import org.bybittradeapp.backtest.domain.ExecutionType;
import org.bybittradeapp.backtest.domain.Order;
import org.bybittradeapp.backtest.domain.Position;
import org.bybittradeapp.logging.Log;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

public class ExchangeSimulator implements Tickle {
    private final List<Order> limitOrders;
    private final List<Position> positions;
    private final Account account;

    public ExchangeSimulator(Account account) {
        this.limitOrders = new ArrayList<>();
        this.positions = new ArrayList<>();
        this.account = account;
    }

    @Override
    public void onTick(long time, double price) {
        executeLimitOrders(time, price);
        checkAndClosePositions(time, price);
    }

    /**
     * Лимитные ордера добавляем в список для отслеживания
     * Рыночные ордера исполняем сразу
     */
    public void submitOrder(Order order, long time) {
        if (order.getExecutionType() == ExecutionType.MARKET) {
            executeMarketOrder(order, time);
        } else {
            limitOrders.add(order);
        }
    }

    private void executeMarketOrder(Order order, long time) {
        order.fill();
        openPosition(order, time, order.getPrice());
    }

    /**
     * Если ордер не исполнен, не отменен и если он может быть исполнен ->
     * ордер становится исполнен, добавляем открытую позицию
     */
    private void executeLimitOrders(long time, double price) {
        Iterator<Order> iterator = limitOrders.iterator();
        while (iterator.hasNext()) {
            Order order = iterator.next();
            if (!order.isFilled() && !order.isCanceled() && order.isExecutable(time, price)) {
                order.fill();
                openPosition(order, time, price);
                iterator.remove();
            }
        }
    }

    /**
     * Если открытая позиция достигает стоп лосса или тейк профита ->
     * закрываем и обновляем депозит
     */
    private void checkAndClosePositions(long time, double price) {
        positions.stream()
                .filter(Position::isOpen)
                .forEach(position -> {
                    switch (position.getOrder().getType()) {
                        case LONG -> {
                            if (position.getTakeProfit() <= price) {
                                closePosition(position, account, time, price);
                            } else if (position.getStopLoss() >= price) {
                                closePosition(position, account, time, price);
                            }
                        }
                        case SHORT -> {
                            if (position.getTakeProfit() >= price) {
                                closePosition(position, account, time, price);
                            } else if (position.getStopLoss() <= price) {
                                closePosition(position, account, time, price);
                            }
                        }
                    }
                });
    }

    private void openPosition(Order order, long time, double price) {
        Position position = new Position(order, price, time, account.calculatePositionSize());
        positions.add(position);
        Log.log(String.format("%s is opened with price %f on %s", position.getOrder().getType(), price, Instant.ofEpochMilli(time)));
    }

    public static void closePosition(Position position, Account account, long time, double price) {
        position.close(time, price);
        Log.log(String.format("%s is closed with PL = ||||   %f   |||| on %s",
                position.getOrder().getType(), position.getProfitLoss(), Instant.ofEpochMilli(time)));
        account.updateBalance(position.getProfitLoss());
    }

    public List<Position> getOpenPositions() {
        return positions.stream().filter(Position::isOpen).collect(Collectors.toList());
    }

    public List<Position> getPositions() {
        return positions;
    }

    public List<Order> getLimitOrders() {
        return limitOrders.stream().filter(order -> !order.isFilled()).collect(Collectors.toList());
    }
}


