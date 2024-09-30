package org.bybittradeapp.backtest.service;

import org.bybittradeapp.backtest.domain.Account;
import org.bybittradeapp.backtest.domain.ExecutionType;
import org.bybittradeapp.backtest.domain.Order;
import org.bybittradeapp.backtest.domain.Position;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.stream.Collectors;

/**
 * Простой симулятор биржи для бессрочной фьючерсной торговли.
 * Поддерживает два типа ордеров: лимитные и рыночные, и два типа торговли: вверх или вниз.
 * Поддерживает комиссии за открытие и закрытие позиций.
 */
public class ExchangeSimulator {
    public static final double TRADE_FEE = 0.001;

    private final List<Order> limitOrders;
    private final List<Position> positions;
    private final Account account;

    public ExchangeSimulator(Account account) {
        this.limitOrders = new ArrayList<>();
        this.positions = new ArrayList<>();
        this.account = account;
    }

    public void onTick(long currentTime, MarketEntry marketEntry) {
        double currentPrice = (marketEntry.high() + marketEntry.low()) / 2.;
        executeLimitOrders(currentTime, currentPrice);
        checkAndClosePositions(currentTime, currentPrice);
    }

    /**
     * Лимитные ордера добавляем в список для отслеживания
     * Рыночные ордера исполняем сразу
     */
    public void submitOrder(Order order, long currentTime, double currentPrice) {
        if (order.getExecutionType() == ExecutionType.MARKET) {
            executeMarketOrder(order, currentTime, currentPrice);
        } else {
            limitOrders.add(order);
        }
    }

    private void executeMarketOrder(Order order, long currentTime, double currentPrice) {
        order.fill();
        openPosition(order, currentTime, currentPrice);
    }

    /**
     * Если ордер не исполнен, не отменен и если он может быть исполнен ->
     * ордер становится исполнен, добавляем открытую позицию
     */
    private void executeLimitOrders(long currentTime, double currentPrice) {
        Iterator<Order> iterator = limitOrders.iterator();
        while (iterator.hasNext()) {
            Order order = iterator.next();
            if (!order.isFilled() && !order.isCanceled() && order.isExecutable(currentPrice)) {
                order.fill();
                openPosition(order, currentTime, currentPrice);
                iterator.remove();
            }
        }
    }

    /**
     * Если открытая позиция достигает стоп лосса или тейк профита ->
     * закрываем и обновляем депозит
     */
    private void checkAndClosePositions(long currentTime, double currentPrice) {
        positions.stream()
                .filter(Position::isOpen)
                .forEach(position -> {
                    switch (position.getOrder().getType()) {
                        case LONG -> {
                            if (position.getTakeProfitPrice() <= currentPrice) {
                                // если текущая цена выше чем цена автоматического тейка -> закрываем позицию с прибылью
                                closePosition(position, currentTime, currentPrice);
                            } else if (position.getStopLossPrice() >= currentPrice) {
                                // если текущая цена ниже чем цена автоматического лосса -> закрываем позицию с прибылью
                                closePosition(position, currentTime, currentPrice);
                            } else {
                                // если позиция не была закрыта автоматически,
                                // обновляем цену закрытия согласно текущей цене и комиссию за закрытие
                                position.setClosePrice(currentPrice);
                            }
                        }
                        case SHORT -> {
                            if (position.getTakeProfitPrice() >= currentPrice) {
                                // если текущая цена ниже чем цена автоматического тейка -> закрываем позицию с прибылью
                                closePosition(position, currentTime, currentPrice);
                            } else if (position.getStopLossPrice() <= currentPrice) {
                                // если текущая цена выше чем цена автоматического лосса -> закрываем позицию с убытком
                                closePosition(position, currentTime, currentPrice);
                            } else {
                                // если позиция не была закрыта автоматически,
                                // обновляем цену закрытия согласно текущей цене и комиссию за закрытие
                                position.setClosePrice(currentPrice);
                            }
                        }
                    }
                });
    }

    private void openPosition(Order order, long time, double price) {
        Position position = new Position(order, price, time);
        positions.add(position);
        account.updateBalance(position);
        Log.log(String.format("%s OPENED ||| price: %.2f ||| money: %.2f ||| fee: %.2f ||| balance: %.2f ||| on %s",
                position.getOrder().getType(),
                price,
                position.getOrder().getMoneyAmount(),
                position.getOpenFee(),
                account.getBalance(),
                Instant.ofEpochMilli(time)));
    }

    public void closePosition(Position position, long time, double price) {
        position.close(time, price);
        account.updateBalance(position);
        if (position.getProfitLoss() > 0.) {
            Log.log(String.format("%s CLOSED ||| P&L++++: %.2f ||| price: %.2f ||| fee: %.2f ||| balance: %.2f ||| on %s",
                    position.getOrder().getType(),
                    position.getProfitLoss(),
                    position.getClosePrice(),
                    position.getCloseFee(),
                    account.getBalance(),
                    Instant.ofEpochMilli(time)));
        } else {
            Log.log(String.format("%s CLOSED ||| P&L----: %.2f ||| price: %.2f ||| fee: %.2f ||| balance: %.2f ||| on %s",
                    position.getOrder().getType(),
                    position.getProfitLoss(),
                    position.getClosePrice(),
                    position.getCloseFee(),
                    account.getBalance(),
                    Instant.ofEpochMilli(time)));
        }
    }

    public List<Position> getOpenPositions() {
        return positions.stream().filter(Position::isOpen).collect(Collectors.toList());
    }

    public List<Position> getPositions() {
        return positions;
    }
}


