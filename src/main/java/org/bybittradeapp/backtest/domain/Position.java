package org.bybittradeapp.backtest.domain;

import java.io.Serializable;

public class Position implements Serializable {
    private final Order order;
    private final double openPrice;
    private final double takeProfit;
    private double stopLoss;
    private double closePrice;
    private final long createTime;
    private long closeTime;

    /**
     * position size (in BTC)
     */
    private final double amount;
    private boolean isOpen = true;

    public Position(Order order, double price, long time, double amountOfMoney) {
        this.order = order;
        this.openPrice = price;
        this.createTime = time;
        this.amount = amountOfMoney / price;
        this.takeProfit = order.getTakeProfit();
        this.stopLoss = order.getStopLoss();
    }

    /**
     * Закрыть позицию
     */
    public void close(long time, double price) {
        this.closePrice = price;
        this.closeTime = time;
        this.isOpen = false;
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
}


