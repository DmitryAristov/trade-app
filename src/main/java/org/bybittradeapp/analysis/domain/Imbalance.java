package org.bybittradeapp.analysis.domain;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Map;

public class Imbalance implements Serializable {
    public enum Type { UP, DOWN }

    private final long start;
    private final Type type;
    private double min;
    private long end;
    private double max;
    private long completeTime;

    public Imbalance(@NotNull Map.Entry<Long, Double> min, @NotNull Map.Entry<Long, Double> max) {
        this.min = min.getValue();
        this.max = max.getValue();
        if (min.getKey() < max.getKey()) {
            this.start = min.getKey();
            this.end = max.getKey();
            this.type = Type.UP;
        } else {
            this.start = max.getKey();
            this.end = min.getKey();
            this.type = Type.DOWN;
        }
    }

    public double getMin() {
        return min;
    }

    public void setMin(double min) {
        this.min = min;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public double getMax() {
        return max;
    }

    public void setMax(double max) {
        this.max = max;
    }

    public Type getType() {
        return type;
    }

    public void setCompleteTime(long completeTime) {
        this.completeTime = completeTime;
    }
}
