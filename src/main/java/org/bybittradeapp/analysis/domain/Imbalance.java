package org.bybittradeapp.analysis.domain;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.bybittradeapp.logging.Log.DATETIME_FORMATTER;

public class Imbalance implements Serializable {

    public enum Type { UP, DOWN }

    private final Type type;
    private long startTime;
    private double startPrice;
    private double endPrice;
    private long endTime;
    private long completeTime;
    private double completePrice;

    public Imbalance(long startTime, double startPrice, long endTime, double endPrice, Type type) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.startPrice = startPrice;
        this.endPrice = endPrice;
        this.type = type;
    }

    public Type getType() {
        return type;
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

    public double size() {
        return switch (type) {
            case UP -> endPrice - startPrice;
            case DOWN -> startPrice - endPrice;
        };
    }

    public long timeSize() {
        return endTime - startTime;
    }

    public double speed() {
        return size() / timeSize();
    }

    @Override
    public String toString() {
        return String.format("""
                        Imbalance
                           startTime :: %s
                           startPrice :: %.2f
                           endTime :: %s
                           endPrice :: %.2f
                        """,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneOffset.UTC).format(DATETIME_FORMATTER),
                startPrice,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneOffset.UTC).format(DATETIME_FORMATTER),
                endPrice);
    }
}
