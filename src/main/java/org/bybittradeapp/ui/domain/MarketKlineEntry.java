package org.bybittradeapp.ui.domain;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.bybittradeapp.logging.Log.DATETIME_FORMATTER;

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
        return "MarketKlineEntry" + "\n" +
                "   startTime :: " + LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneOffset.UTC).format(DATETIME_FORMATTER) + "\n" +
                "   openPrice :: " + openPrice + "\n" +
                "   highPrice :: " + highPrice + "\n" +
                "   lowPrice :: " + lowPrice + "\n";
    }
}
