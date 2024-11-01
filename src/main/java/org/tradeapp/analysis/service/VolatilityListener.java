package org.tradeapp.analysis.service;

public interface VolatilityListener {
    void notify(double volatility, double average);
}
