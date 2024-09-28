package org.bybittradeapp.backtest.domain;

public class Account {
    private static final int BALANCE = 4000;
    private static final double RISK_LEVEL = 50.0;
    private static final int CREDIT_LEVEL = 3;

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
     * Процент от депозита для каждой сделки
     */
    public double calculatePositionSize() {
        return balance * (riskPercentage / 100.0);
    }

    public void updateBalance(Position position) {
        if (position.isOpen()) {
            this.balance -= position.getOpenCommission();
            return;
        }
        if (position.isClosed()) {
            this.balance += position.getProfitLoss() * credit - position.getCloseCommission();
        }
    }

    public long getCredit() {
        return credit;
    }
}

