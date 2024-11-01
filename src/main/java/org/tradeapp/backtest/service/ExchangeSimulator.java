package org.tradeapp.backtest.service;

import org.tradeapp.backtest.domain.Account;
import org.tradeapp.backtest.domain.ExecutionType;
import org.tradeapp.backtest.domain.Order;
import org.tradeapp.backtest.domain.Position;
import org.tradeapp.logging.Log;
import org.tradeapp.marketdata.domain.MarketEntry;

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
    public static final double MARKET_ORDER_TRADE_FEE = 0.00036;

    private final List<Order> limitOrders;
    private final List<Position> positions;
    private final Account account;

    public ExchangeSimulator(Account account) {
        this.limitOrders = new ArrayList<>();
        this.positions = new ArrayList<>();
        this.account = account;
    }

    public void onTick(long currentTime, MarketEntry currentEntry) {
        executeLimitOrders(currentTime, currentEntry);
        checkAndClosePositions(currentTime, currentEntry);
    }

    /**
     * Лимитные ордера добавляем в список для отслеживания
     * Рыночные ордера исполняем сразу
     */
    public void submitOrder(Order order, long currentTime, MarketEntry currentEntry) {
        if (order.getExecutionType() == ExecutionType.MARKET) {
            executeMarketOrder(order, currentTime, currentEntry);
        } else {
            limitOrders.add(order);
        }
    }

    private void executeMarketOrder(Order order, long currentTime, MarketEntry currentEntry) {
        order.fill();
        openPosition(order, currentTime, currentEntry);
    }

    /**
     * Если ордер не исполнен, не отменен и если он может быть исполнен ->
     * ордер становится исполнен, добавляем открытую позицию
     */
    private void executeLimitOrders(long currentTime, MarketEntry currentEntry) {
        Iterator<Order> iterator = limitOrders.iterator();
        while (iterator.hasNext()) {
            Order order = iterator.next();
            if (!order.isFilled() && !order.isCanceled() && order.isExecutable(currentEntry)) {
                order.fill();
                openPosition(order, currentTime, currentEntry);
                iterator.remove();
            }
        }
    }

    /**
     * Если открытая позиция достигает стоп лосса или тейк профита ->
     * закрываем и обновляем депозит
     */
    private void checkAndClosePositions(long currentTime, MarketEntry currentEntry) {
        positions.stream()
                .filter(Position::isOpen)
                .forEach(position -> {
                    switch (position.getOrder().getType()) {
                        case LONG -> {
                            if (position.getTakeProfitPrice() <= currentEntry.high()) {
                                // если текущая цена выше чем цена автоматического тейка -> закрываем позицию с прибылью
                                closeTake(position, currentTime);
                            } else if (position.getStopLossPrice() >= currentEntry.low()) {
                                // если текущая цена ниже чем цена автоматического лосса -> закрываем позицию с убытком
                                closeLoss(position, currentTime);
                            } else {
                                // если позиция не была закрыта автоматически,
                                // обновляем цену закрытия согласно текущей цене и комиссию за закрытие
                                position.setClosePrice(currentEntry.high());
                            }
                        }
                        case SHORT -> {
                            if (position.getTakeProfitPrice() >= currentEntry.low()) {
                                // если текущая цена ниже чем цена автоматического тейка -> закрываем позицию с прибылью
                                closeTake(position, currentTime);
                            } else if (position.getStopLossPrice() <= currentEntry.high()) {
                                // если текущая цена выше чем цена автоматического лосса -> закрываем позицию с убытком
                                closeLoss(position, currentTime);
                            } else {
                                // если позиция не была закрыта автоматически,
                                // обновляем цену закрытия согласно текущей цене и комиссию за закрытие
                                position.setClosePrice(currentEntry.low());
                            }
                        }
                    }
                });
    }

    private void openPosition(Order order, long currentTime, MarketEntry currentEntry) {
        Position position = new Position(order, currentEntry, currentTime);
        positions.add(position);
        account.updateBalance(position);
        Log.debug(String.format("%s OPENED ||| price: %.2f$ ||| money: %.2f$ ||| fee: %.2f$ ||| balance: %.2f$ |||",
                        position.getOrder().getType(),
                        position.getOpenPrice(),
                        position.getOrder().getMoneyAmount(),
                        position.getOpenFee(),
                        account.getBalance()),
                currentTime);
    }

    public void closeTake(Position position, long currentTime) {
        position.close(currentTime, position.getTakeProfitPrice());
        account.updateBalance(position);
        Log.debug(String.format("%s CLOSED ||| P&L++++: %.2f$ ||| price: %.2f$ ||| fee: %.2f$ ||| balance: %.2f$ |||",
                        position.getOrder().getType(),
                        position.getProfitLoss(),
                        position.getClosePrice(),
                        position.getCloseFee(),
                        account.getBalance()),
                currentTime);

    }

    public void closeLoss(Position position, long currentTime) {
        position.close(currentTime, position.getStopLossPrice());
        account.updateBalance(position);
        Log.debug(String.format("%s CLOSED ||| P&L----: %.2f$ ||| price: %.2f$ ||| fee: %.2f$ ||| balance: %.2f$ |||",
                        position.getOrder().getType(),
                        position.getProfitLoss(),
                        position.getClosePrice(),
                        position.getCloseFee(),
                        account.getBalance()),
                currentTime);
    }

    public List<Position> getOpenPositions() {
        return positions.stream().filter(Position::isOpen).collect(Collectors.toList());
    }

    public List<Position> getPositions() {
        return positions;
    }
}


