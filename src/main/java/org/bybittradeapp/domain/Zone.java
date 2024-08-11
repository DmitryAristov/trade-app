package org.bybittradeapp.domain;

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

    private double price;
    private long timestamp;
    private Type type;
}
