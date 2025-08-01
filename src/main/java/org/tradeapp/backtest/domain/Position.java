package org.tradeapp.backtest.domain;

import org.tradeapp.ui.utils.TimeFormatter;

import java.io.Serializable;

import static org.tradeapp.backtest.service.ExchangeSimulator.MARKET_ORDER_TRADE_FEE;

public class Position implements Serializable {
    private final Order order;
    private final double openPrice;
    private final long openTime;
    private final double takeProfitPrice;
    private double stopLossPrice;
    private double closePrice;
    private long closeTime;
    /**
     * Размер позиции в биткоинах (BTC) с уже учтенным кредитным плечом
     * 32500$ / цену открытия (10000$) = количество BTC которое покупаем (3.25BTC)
     */
    private final double amountInBTC;
    /**
     * 32500$ * 0.001 = 32.5$ - комиссия за открытие сделки (с учетом плеча)
     */
    private final double openFee;
    /**
     * 3.25BTC * цена закрытия (12000$) * 0.001 = 39$ - комиссия за закрытие сделки (с учетом плеча)
     */
    private double closeFee;
    private boolean isOpen = true;

    /**
     * Если создается рыночный ордер или лимитный ордер может быть исполнен, открывается позиция на основе ордера.
     * Сразу заполняется комиссия за открытие и размер позиции в биткоинах (BTC).
     * При первом вызове чтобы комиссия за закрытие не была равна 0, заполняем ее комиссией за открытие.
     */
    public Position(Order order, MarketEntry currentEntry, long openTime) {
        this.order = order;
        this.openPrice = currentEntry.average();
        this.openTime = openTime;
        this.amountInBTC = order.getMoneyAmount() / openPrice;
        this.takeProfitPrice = order.getTakeProfitPrice();
        this.stopLossPrice = order.getStopLossPrice();
        this.openFee = order.getMoneyAmount() * MARKET_ORDER_TRADE_FEE;
        this.closeFee = order.getMoneyAmount() * MARKET_ORDER_TRADE_FEE;
    }

    /**
     * Закрывает позицию. Дополнительно считаем комиссию за закрытие также, как и за открытие, только с ценой закрытия.
     */
    public void close(long closeTime, double closePrice) {
        this.closePrice = closePrice;
        this.closeTime = closeTime;
        this.isOpen = false;
        this.closeFee = amountInBTC * closePrice * MARKET_ORDER_TRADE_FEE;
    }

    /**
     * Посчитать прибыль/убыток без учета комиссии. Уже включено кредитное плечо
     */
    public double getProfitLoss() {
        return switch (order.getType()) {
            case LONG -> (closePrice - openPrice) * amountInBTC;
            case SHORT -> (openPrice - closePrice) * amountInBTC;
        };
    }

    /**
     * Обновляет предполагаемую цену закрытия и комиссию за закрытие.
     */
    public void setClosePrice(double closePrice) {
        this.closePrice = closePrice;
        this.closeFee = amountInBTC * closePrice * MARKET_ORDER_TRADE_FEE;
    }

    /**
     * Определяет если текущая позиция находится в без-убытке.
     * Это значит что когда она закроется автоматически по стоп цене, прибыль будет больше или равна сумме комиссий за открытие и закрытие.
     * @return true если позиция в без-убытке
     */
    public boolean isZeroLoss() {
        double feesMoneyAmount = (this.openFee + this.closeFee) / this.amountInBTC;
        return switch (this.getOrder().getType()) {
            case LONG -> this.stopLossPrice >= this.openPrice + feesMoneyAmount;
            case SHORT -> this.stopLossPrice <= this.openPrice - feesMoneyAmount;
        };
    }

    /**
     * Открыли сделку LONG по цене 10000$ за 1 BTC количество денег 5000$ (0.5 BTC) с кредитным плечом 5
     *   комиссия за открытие 0.001 * 5000 * 5 = 25$
     *   комиссия за закрытие примерно тоже 25$
     * Тогда цена закрытия для без-убытка должна быть такая, чтобы профит = 25 + 25$ = 50$
     *   (цена закрытия X $ - цена открытия 10000 $) * 0.5 BTC * кредитное плечо (5) > 50 $
     *   (цена закрытия X $ - цена открытия 10000 $) > 50 $ / (0.5 BTC * кредитное плечо (5))
     *   цена закрытия X $ > цена открытия 10000 $ + 50 $ / (0.5 BTC * кредитное плечо (5))
     */
    public void setZeroLoss() {
        double feesMoneyAmount = (this.openFee + this.closeFee) / this.amountInBTC;
        switch (this.order.getType()) {
            case LONG -> this.stopLossPrice = this.openPrice + feesMoneyAmount;
            case SHORT -> this.stopLossPrice = this.openPrice - feesMoneyAmount;
        }
    }

    public boolean isOpen() {
        return isOpen;
    }

    public double getTakeProfitPrice() {
        return takeProfitPrice;
    }

    public double getStopLossPrice() {
        return stopLossPrice;
    }

    public Order getOrder() {
        return order;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public long getOpenTime() {
        return openTime;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public double getOpenFee() {
        return openFee;
    }

    public double getCloseFee() {
        return closeFee;
    }

    public double getClosePrice() {
        return closePrice;
    }

    @Override
    public String toString() {
        return String.format("""
                        Position
                           amountInBTC :: %.4f
                           openTime :: %s
                           openPrice :: %.2f$
                           openFee :: %.2f$
                           closePrice :: %.2f$
                           closeFee :: %.2f$
                           takeProfitPrice :: %.2f$
                           stopLossPrice :: %.2f$""",
                amountInBTC,
                TimeFormatter.format(openTime),
                openPrice,
                openFee,
                closePrice,
                closeFee,
                takeProfitPrice,
                stopLossPrice);
    }
}


