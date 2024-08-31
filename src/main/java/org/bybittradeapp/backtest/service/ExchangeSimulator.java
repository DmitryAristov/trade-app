package org.bybittradeapp.backtest.service;

import org.bybittradeapp.backtest.domain.Account;
import org.bybittradeapp.backtest.domain.Order;
import org.bybittradeapp.backtest.domain.Position;
import org.bybittradeapp.marketData.domain.MarketKlineEntry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

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
    public void onTick(MarketKlineEntry entry) {
        executeLimitOrders(entry);
        checkAndClosePositions(entry);
    }

    /**
     * Лимитные ордера добавляем в список для отслеживания
     * Рыночные ордера исполняем сразу
     */
    public void submitOrder(Order order) {
        System.out.println("Order is placed: " + order.toString());
        if (order.getExecutionType() == Order.ExecutionType.MARKET) {
            executeMarketOrder(order);
        } else {
            limitOrders.add(order);
        }
    }

    /**
     * Если ордер не исполнен, не отменен и если он может быть исполнен ->
     * ордер становится исполнен, добавляем открытую позицию
     */
    private void executeLimitOrders(MarketKlineEntry entry) {
        Iterator<Order> iterator = limitOrders.iterator();
        while (iterator.hasNext()) {
            Order order = iterator.next();
            if (!order.isFilled() && !order.isCanceled() && order.isExecutable(entry)) {
                order.fill();
                openPosition(order, entry.getClosePrice());
                iterator.remove();
            }
        }
    }

    /**
     * Если открытая позиция достигает стоп лосса или тейк профита ->
     * закрываем и обновляем депозит
     */
    private void checkAndClosePositions(MarketKlineEntry entry) {
        positions.stream()
                .filter(Position::isOpen)
                .forEach(position -> {
                    switch (position.getOrder().getType()) {
                        case LONG -> {
                            if (position.getTakeProfit() <= entry.getHighPrice()) {
                                closePosition(position, entry);
                            } else if (position.getStopLoss() >= entry.getLowPrice()) {
                                closePosition(position, entry);
                            }
                        }
                        case SHORT -> {
                            if (position.getTakeProfit() >= entry.getLowPrice()) {
                                closePosition(position, entry);
                            } else if (position.getStopLoss() <= entry.getHighPrice()) {
                                closePosition(position, entry);
                            }
                        }
                    }
                });
    }

    private void executeMarketOrder(Order order) {
        order.fill();
        openPosition(order, order.getPrice());
    }

    private void openPosition(Order order, double openPrice) {
        Position position = new Position(order, openPrice, account.calculatePositionSize());
        positions.add(position);
        System.out.println("Position is opened: " + position.toString());
    }

    private void closePosition(Position position, MarketKlineEntry entry) {
        position.close(entry.getPrice());
        System.out.println("Position " + position.toString() + " is closed with TP&SL: " + position.getProfitLoss());
        account.updateBalance(position.getProfitLoss());
    }

    public List<Position> getOpenPositions() {
        return positions.stream().filter(Position::isOpen).collect(Collectors.toList());
    }

    public List<Order> getLimitOrders() {
        return limitOrders.stream().filter(order -> !order.isFilled()).collect(Collectors.toList());
    }
}


