package org.tradeapp.ui.domain;

import org.tradeapp.ui.utils.TimeFormatter;

import java.io.Serializable;

/**
 * Для отображения в UI в виде "свечей"
 */
public class MarketKlineEntry implements Serializable {
    private long openTime;
    private double highPrice;
    private double lowPrice;
    private double openPrice;
    private double closePrice;
    private double volume;

    public long getOpenTime() {
        return openTime;
    }

    public void setOpenTime(long openTime) {
        this.openTime = openTime;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(double openPrice) {
        this.openPrice = openPrice;
    }

    public double getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(double highPrice) {
        this.highPrice = highPrice;
    }

    public double getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(double lowPrice) {
        this.lowPrice = lowPrice;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(double closePrice) {
        this.closePrice = closePrice;
    }

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    @Override
    public String toString() {
        return String.format("""
                        MarketKlineEntry
                           startTime :: %s
                           openPrice :: %.2f$
                           highPrice :: %.2f$
                           lowPrice :: %.2f$""",
                TimeFormatter.format(openTime),
                openPrice,
                highPrice,
                lowPrice);
    }
}
