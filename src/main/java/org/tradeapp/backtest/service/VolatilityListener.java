package org.tradeapp.backtest.service;

public interface VolatilityListener {
    void notify(double volatility, double average, long currentTime);
}
