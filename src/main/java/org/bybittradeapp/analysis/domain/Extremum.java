package org.bybittradeapp.analysis.domain;

import java.io.Serializable;
import java.util.Objects;

public class Extremum implements Serializable {
    public enum Type {
        MIN,
        MAX;
        @Override
        public String toString() {
            return switch (this) {
                case MIN -> "Min";
                case MAX -> "Max";
            };
        }
    }

    private long timestamp;
    private double price;
    private final Type type;

    public Extremum(long time, double price, Type type) {
        this.timestamp = time;
        this.price = price;
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Extremum extremum = (Extremum) o;
        return timestamp == extremum.timestamp && Double.compare(extremum.price, price) == 0 && type == extremum.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, price, type);
    }

    @Override
    public String toString() {
        return "Extremum{" +
                "timestamp=" + timestamp +
                ", price=" + price +
                ", type=" + type +
                '}';
    }
}
