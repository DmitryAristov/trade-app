package org.bybittradeapp.backtest.domain;

public class Account {
    private double balance;
    private final double riskPercentage;
    private final long credit;

    public Account(double initialBalance, double riskPercentage, long credit) {
        this.balance = initialBalance;
        this.riskPercentage = riskPercentage;
        this.credit = credit;
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

    public void updateBalance(double profitLoss) {
        System.out.println("Balance changed: " + balance + " " + profitLoss + " = " + (balance + profitLoss));
        this.balance += profitLoss * credit;
    }
}

