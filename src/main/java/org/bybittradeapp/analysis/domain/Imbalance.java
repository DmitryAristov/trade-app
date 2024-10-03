package org.bybittradeapp.analysis.domain;

import java.io.Serializable;
import java.time.Instant;

public class Imbalance implements Serializable {

    public enum Type { UP, DOWN }

    private Type type;
    private long startTime;
    private double startPrice;
    private double endPrice;
    private long endTime;
    private long completeTime;
    private double completePrice;
    private int completesCount = 0;
    private int combinesCount = 0;

    public Imbalance(long startTime, double startPrice, long endTime, double endPrice) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.startPrice = startPrice;
        this.endPrice = endPrice;
        this.type = startPrice - endPrice > 0 ? Type.DOWN : Type.UP;
    }

    public Imbalance(long startTime, double startPrice, long endTime, double endPrice, Type type) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.startPrice = startPrice;
        this.endPrice = endPrice;
        this.type = type;
    }

    public Imbalance() {  }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public double getStartPrice() {
        return startPrice;
    }

    public void setStartPrice(double startPrice) {
        this.startPrice = startPrice;
    }

    public double getEndPrice() {
        return endPrice;
    }

    public void setEndPrice(double endPrice) {
        this.endPrice = endPrice;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(long completeTime) {
        this.completeTime = completeTime;
    }

    public double getCompletePrice() {
        return completePrice;
    }

    public void setCompletePrice(double completePrice) {
        this.completePrice = completePrice;
    }

    public void incrementCompletesCount() {
        this.completesCount++;
    }

    public int getCompletesCount() {
        return completesCount;
    }

    public void setCombinesCount(int combinesCount) {
        this.combinesCount = combinesCount;
    }

    public int getCombinesCount() {
        return combinesCount;
    }

    @Override
    public String toString() {
        return "Imbalance{" +
                "startTime=" + Instant.ofEpochMilli(startTime) +
                ", startPrice=" + startPrice +
                ", endTime=" + Instant.ofEpochMilli(endTime) +
                ", endPrice=" + endPrice +
                ", type=" + type +
                '}';
    }
}
