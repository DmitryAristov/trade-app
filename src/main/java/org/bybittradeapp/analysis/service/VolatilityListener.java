package org.bybittradeapp.analysis.service;

public interface VolatilityListener {
    void notify(double volatility, double average);
}
