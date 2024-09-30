package org.bybittradeapp.backtest.domain;

public class Account {
    private static final int BALANCE = 10000;
    private static final double RISK_LEVEL = 65.0;
    private static final int CREDIT_LEVEL = 5;

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
        return balance * (riskPercentage / 100.0) * credit;
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

