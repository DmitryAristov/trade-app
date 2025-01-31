package org.tradeapp.backtest.domain;

import static org.tradeapp.backtest.constants.Settings.*;

public class Account {

    private double balance;
    private final double riskPercentage;
    private final long credit;

    public Account() {
        this.balance = BALANCE;
        this.riskPercentage = RISK_LEVEL;
        this.credit = CREDIT_LEVEL;
           }

    public double getBalance() {
        return balance;
    }

    /**
     * Процент от депозита для каждой сделки (в долларах с учетом займа)
     * Если баланс 10000$, то метод вернет 32500$
     */
    public double calculatePositionSize() {
        return balance * riskPercentage * credit;
    }

    /**
     * Обновляем баланс аккаунта при открытии/закрытии позиции.
     * При открытии отнимаем комиссию за открытие, при закрытии прибавляем профит и отнимаем комиссию за закрытие
     */
    public void updateBalance(Position position) {
        if (position.isOpen()) {
            this.balance -= position.getOpenFee();
        } else {
            this.balance += position.getProfitLoss() - position.getCloseFee();
        }
    }
}

