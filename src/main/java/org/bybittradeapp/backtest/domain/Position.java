package org.bybittradeapp.backtest.domain;

public class Position {
    private final Order order;
    private final double openPrice;
    private final double takeProfit;
    private final double stopLoss;
    private double closePrice;
    private final double amount;
    private boolean isOpen = true;

    public Position(Order order, double openPrice, double amount) {
        this.order = order;
        this.openPrice = openPrice;
        this.amount = amount;
        this.takeProfit = order.getTakeProfit();
        this.stopLoss = order.getStopLoss();
    }

    /**
     * Закрыть позицию
     */
    public void close(double closePrice) {
        this.closePrice = closePrice;
        this.isOpen = false;
    }

    /**
     * Посчитать прибыль/убыток
     */
    public double getProfitLoss() {
        return switch (order.getType()) {
            case LONG -> (closePrice - openPrice) * (amount / openPrice);
            case SHORT -> (openPrice - closePrice) * (amount / openPrice);
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

    public Order getOrder() {
        return order;
    }

    @Override
    public String toString() {
        return "Position{" +
                "openPrice=" + openPrice +
                ", amount=" + amount +
                ", takeProfit=" + takeProfit +
                ", stopLoss=" + stopLoss +
                '}';
    }
}


