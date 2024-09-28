package org.bybittradeapp.backtest.domain;

import java.io.Serializable;

import static org.bybittradeapp.backtest.service.ExchangeSimulator.TRADE_COMMISSION;

public class Position implements Serializable {
    private final Order order;
    private final double openPrice;
    private final double takeProfit;
    private double stopLoss;
    private double closePrice;
    private final long createTime;
    private long closeTime;
    private double openCommission;
    private double closeCommission;

    /**
     * position size (in BTC)
     */
    private final double amount;
    private boolean isOpen = true;

    public Position(Order order, double price, long time) {
        this.order = order;
        this.openPrice = price;
        this.createTime = time;
        this.amount = order.getMoneyAmount() / price;
        this.takeProfit = order.getTakeProfit();
        this.stopLoss = order.getStopLoss();
        this.openCommission = order.getMoneyAmount() * TRADE_COMMISSION;
    }

    /**
     * Закрыть позицию
     */
    public void close(long time, double price) {
        this.closePrice = price;
        this.closeTime = time;
        this.isOpen = false;
        this.closeCommission = amount * price * TRADE_COMMISSION;
    }

    /**
     * Посчитать прибыль/убыток
     */
    public double getProfitLoss() {
        return switch (order.getType()) {
            case LONG -> (closePrice - openPrice) * amount;
            case SHORT -> (openPrice - closePrice) * amount;
        };
    }

    public boolean isOpen() {
        return isOpen;
    }

    public double getTakeProfit() {
        return takeProfit;
    }

    public double getStopLoss() {
        return stopLoss;
    }

    public void setStopLoss(double stopLoss) {
        this.stopLoss = stopLoss;
    }

    public Order getOrder() {
        return order;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public long getCreateTime() {
        return createTime;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public boolean isZeroLoss() {
        return switch (order.getType()) {
            //TODO
            case LONG -> openPrice <= stopLoss;
            case SHORT -> openPrice >= stopLoss;
        };
    }

    public boolean isClosed() {
        return !isOpen;
    }

    public double getOpenCommission() {
        return openCommission;
    }

    public double getCloseCommission() {
        return closeCommission;
    }

    public void setClosePrice(double closePrice) {
        this.closePrice = closePrice;
    }

    public double getAmount() {
        return amount;
    }
}


