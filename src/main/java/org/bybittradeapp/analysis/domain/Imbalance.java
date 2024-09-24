package org.bybittradeapp.analysis.domain;

import java.io.Serializable;

public class Imbalance implements Serializable {


    public enum Type { UP, DOWN }

    private Type type;
    private long startTime;
    private double startPrice;
    private double endPrice;
    private long endTime;
    private long completeTime;

    public Imbalance(long startTime, double startPrice, long endTime, double endPrice) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.startPrice = startPrice;
        this.endPrice = endPrice;
        this.type = startPrice - endPrice > 0 ? Type.DOWN : Type.UP;
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

    public static Imbalance of(Imbalance imbalance) {
        Imbalance result = new Imbalance();
        result.setStartTime(imbalance.getStartTime());
        result.setStartPrice(imbalance.getStartPrice());
        result.setEndTime(imbalance.getEndTime());
        result.setEndPrice(imbalance.getEndPrice());
        result.setType(imbalance.getType());
        result.setCompleteTime(imbalance.getCompleteTime());

        return result;
    }
}
