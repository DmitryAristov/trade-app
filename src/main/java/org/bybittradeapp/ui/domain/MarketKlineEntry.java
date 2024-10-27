package org.bybittradeapp.ui.domain;

import org.bybittradeapp.ui.utils.TimeFormatter;

import java.io.Serializable;

/**
 * Для отображения в UI в виде "свечей"
 */
public class MarketKlineEntry implements Serializable {
    private long startTime;
    private double highPrice;
    private double lowPrice;
    private double openPrice;
    private double closePrice;

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
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

    @Override
    public String toString() {
        return String.format("""
                        MarketKlineEntry
                           startTime :: %s
                           openPrice :: %.2f$
                           highPrice :: %.2f$
                           lowPrice :: %.2f$""",
                TimeFormatter.format(startTime),
                openPrice,
                highPrice,
                lowPrice);
    }
}
