package org.bybittradeapp.domain;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

public class Imbalance {
    public enum Type { UP, DOWN }
    public enum Status { PROGRESS, FINISHED }
    @NotNull
    MarketKlineEntry min;
    @NotNull
    MarketKlineEntry max;
    Type type;
    Status status;

    public Imbalance(@NotNull MarketKlineEntry min, @NotNull MarketKlineEntry max) {
        this.min = min;
        this.max = max;
    }

    @NotNull
    public MarketKlineEntry getMin() {
        return min;
    }

    public void setMin(@NotNull MarketKlineEntry min) {
        this.min = min;
    }

    @NotNull
    public MarketKlineEntry getMax() {
        return max;
    }

    public void setMax(@NotNull MarketKlineEntry max) {
        this.max = max;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type +
                " {min=" + min.getLowPrice() + " at=" + Instant.ofEpochMilli(min.getStartTime()) +
                ", max=" + max.getHighPrice() + " at=" + Instant.ofEpochMilli(max.getStartTime()) +
                '}';
    }
}
