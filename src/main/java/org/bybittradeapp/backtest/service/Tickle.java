package org.bybittradeapp.backtest.service;

public interface Tickle {
    void onTick(long time, double price);
}
