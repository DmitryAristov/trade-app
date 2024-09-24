package org.bybittradeapp.marketdata.domain;

public enum IntervalType {
    SECOND(1000L), MINUTE(60000L);

    private final long mills;

    IntervalType(long mills) {
        this.mills = mills;
    }

    public long getMills() {
        return mills;
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
