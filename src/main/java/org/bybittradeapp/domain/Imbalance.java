package org.bybittradeapp.domain;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

public class Imbalance {
    public enum Type { UP, DOWN }
    public enum Status { PROGRESS, COMPLETE }
    @NotNull
    private MarketKlineEntry min;
    @NotNull
    private MarketKlineEntry max;
    private Type type;
    private Status status;

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

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Imbalance imbalance = (Imbalance) o;
        return min.getLowPrice() == imbalance.min.getLowPrice()
                && max.getHighPrice() == imbalance.max.getHighPrice()
                && type == imbalance.type
                && status == imbalance.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min.getLowPrice(), max.getHighPrice(), type, status);
    }

    @Override
    public String toString() {
        return type +
                " {min=" + min.getLowPrice() + " at=" + Instant.ofEpochMilli(min.getStartTime()) +
                ", max=" + max.getHighPrice() + " at=" + Instant.ofEpochMilli(max.getStartTime()) +
                '}';
    }
}
