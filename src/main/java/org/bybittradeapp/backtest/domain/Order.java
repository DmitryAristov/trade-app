package org.bybittradeapp.backtest.domain;

import org.bybittradeapp.marketData.domain.MarketKlineEntry;

public class Order {
    public enum OrderType { LONG, SHORT }
    public enum ExecutionType { MARKET, LIMIT }

    private final OrderType type;
    private final ExecutionType executionType;
    private double price;
    private boolean filled = false;
    private boolean canceled = false;
    private double takeProfit = -1;
    private double stopLoss = -1;
    private long createTime;

    public Order(OrderType type, ExecutionType executionType) {
        this.type = type;
        this.executionType = executionType;
    }

    /**
     * Ордер исполнен
     */
    public void fill() {
        this.filled = true;
    }

    /**
     * Ордер отменен
     */
    public void cancel() {
        this.canceled = true;
    }

    /**
     * Проверяет если лимитный ордер можно исполнить
     */
    public boolean isExecutable(MarketKlineEntry entry) {
        if (this.getType() == Order.OrderType.LONG) {
            return this.getPrice() >= entry.getLowPrice();
        } else {
            return this.getPrice() <= entry.getHighPrice();
        }
    }

    /**
     * Устанавливает условия закрытия позиции
     */
    public void setTP_SL(double takeProfit, double stopLoss) {
        this.takeProfit = takeProfit;
        this.stopLoss = stopLoss;
    }

    public OrderType getType() {
        return type;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }

    public double getPrice() {
        return price;
    }

    public boolean isFilled() {
        return filled;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public double getTakeProfit() {
        return takeProfit;
    }

    public double getStopLoss() {
        return stopLoss;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "Order{" +
                "type=" + type +
                ", executionType=" + executionType +
                ", price=" + price +
                ", takeProfit=" + takeProfit +
                ", stopLoss=" + stopLoss +
                '}';
    }
}

