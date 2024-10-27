package org.bybittradeapp.backtest.domain;

import org.bybittradeapp.logging.Log;

public class Account {
    private static final int BALANCE = 10000;
    private static final double RISK_LEVEL = 50.0;
    private static final int CREDIT_LEVEL = 12;

    private double balance;
    private final double riskPercentage;
    private final long credit;

    public Account() {
        this.balance = BALANCE;
        this.riskPercentage = RISK_LEVEL;
        this.credit = CREDIT_LEVEL;
        Log.info(String.format("""
                        account parameters:
                            balance :: %d$
                            risk :: %.2f
                            credit :: %d""",
                BALANCE, RISK_LEVEL, CREDIT_LEVEL));
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
        Log.debug(String.format("balance updated: %.2f$", this.balance));
    }
}

