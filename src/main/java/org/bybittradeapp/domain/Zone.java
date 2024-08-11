package org.bybittradeapp.domain;

import java.util.Objects;

public class Zone {
    public enum Type {
        SUPPORT,
        RESISTANCE;
        @Override
        public String toString() {
            return switch (this) {
                case SUPPORT -> "Support";
                case RESISTANCE -> "Resistance";
            };
        }
    }

    private long timestamp;
    private double price;
    private Type type;

    public Zone(long timestamp, double price, Type type) {
        this.timestamp = timestamp;
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

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Zone zone = (Zone) o;
        return timestamp == zone.timestamp && Double.compare(zone.price, price) == 0 && type == zone.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, price, type);
    }
}
