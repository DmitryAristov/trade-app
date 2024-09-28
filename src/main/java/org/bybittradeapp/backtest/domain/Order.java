package org.bybittradeapp.backtest.domain;

import java.io.Serializable;

public class Order implements Serializable {

    private OrderType type;
    private ExecutionType executionType;
    private double price;
    private double moneyAmount;
    private boolean filled = false;
    private boolean canceled = false;
    private double takeProfit = -1;
    private double stopLoss = -1;
    private long createTime;

    public Order() { }

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
    public boolean isExecutable(long time, double price) {
        if (this.getType() == OrderType.LONG) {
            return this.getPrice() >= price;
        } else {
            return this.getPrice() <= price;
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

    public void setType(OrderType type) {
        this.type = type;
    }

    public void setExecutionType(ExecutionType executionType) {
        this.executionType = executionType;
    }

    public double getMoneyAmount() {
        return moneyAmount;
    }

    public void setMoneyAmount(double moneyAmount) {
        this.moneyAmount = moneyAmount;
    }
}

