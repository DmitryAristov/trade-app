package org.bybittradeapp.backtest.service;

import org.bybittradeapp.marketData.domain.MarketKlineEntry;

public interface Tickle {
    void onTick(MarketKlineEntry currentEntry);
}
