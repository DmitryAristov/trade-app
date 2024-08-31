package org.bybittradeapp.marketData.domain;

import java.io.Serializable;
import java.time.Instant;

public class MarketKlineEntry implements Serializable {
    private long startTime;
    private double highPrice;
    private double lowPrice;

    // UI purposes only
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
        return "MarketKlineEntry{" + '\'' +
                "startTime=" + Instant.ofEpochMilli(startTime) + '\'' +
                ", openPrice='" + openPrice + '\'' +
                ", highPrice='" + highPrice + '\'' +
                ", lowPrice='" + lowPrice + '\'' +
                '}';
    }

    public double getPrice() {
        return (this.highPrice + this.lowPrice) / 2.;
    }
}
