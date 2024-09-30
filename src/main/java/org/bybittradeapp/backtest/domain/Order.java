package org.bybittradeapp.backtest.domain;

import java.io.Serializable;

public class Order implements Serializable {

    /**
     * Тип ордера: вверх или вниз
     */
    private OrderType type;

    /**
     * Тип ордера: рыночный или лимитный. Рыночный исполняется прямо в момент создания,
     * лимитный исполняется по цене исполнения (executionPrice)
     */
    private ExecutionType executionType;
    private double executionPrice;

    /**
     * Сумма денег ордера. Кредитное плечо уже учтено
     */
    private double moneyAmount;
    private boolean filled = false;
    private boolean canceled = false;
    private double takeProfitPrice = -1;
    private double stopLossPrice = -1;
    private long createTime;

    public Order() { }

    /**
     * Исполнить ордер
     */
    public void fill() {
        this.filled = true;
    }

    /**
     * Отменить ордер
     */
    public void cancel() {
        this.canceled = true;
    }

    /**
     * Проверяет если лимитный ордер можно исполнить.
     * Возвращает ошибку если метод вызван на рыночном ордере.
     */
    public boolean isExecutable(double currentPrice) {
        if (this.executionType == ExecutionType.LIMIT) {
            if (this.type == OrderType.LONG) {
                return this.executionPrice >= currentPrice;
            } else {
                return this.executionPrice <= currentPrice;
            }
        } else {
            throw new RuntimeException("Market order executes at create time. It cannot be executable or not.");
        }
    }

    /**
     * Устанавливает цены закрытия позиции
     * @param stopLoss цена автоматического закрытия позиции в минус
     * @param takeProfit цена автоматического закрытия позиции в плюс
     */
    public void setTP_SL(double takeProfit, double stopLoss) {
        this.takeProfitPrice = takeProfit;
        this.stopLossPrice = stopLoss;
    }

    public OrderType getType() {
        return type;
    }

    public ExecutionType getExecutionType() {
        return executionType;
    }

    public boolean isFilled() {
        return filled;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public double getTakeProfitPrice() {
        return takeProfitPrice;
    }

    public double getStopLossPrice() {
        return stopLossPrice;
    }

    public void setExecutionPrice(double executionPrice) {
        this.executionPrice = executionPrice;
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

