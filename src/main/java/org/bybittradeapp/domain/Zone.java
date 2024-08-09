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

    private Box zone;
    private Box liquiditySweep;
    private Line line;
    private boolean breakout;
    private boolean test;
    private boolean retest;
    private boolean liquidation;
    private float margin;
    private Type type;

    public Zone(Box zone,
                Box liquiditySweep,
                Line line,
                boolean breakout,
                boolean test,
                boolean retest,
                boolean liquidation,
                float margin,
                Type type
    ) {
        this.zone = zone;
        this.liquiditySweep = liquiditySweep;
        this.line = line;
        this.breakout = breakout;
        this.test = test;
        this.retest = retest;
        this.liquidation = liquidation;
        this.margin = margin;
        this.type = type;
    }

    public Box getZone() {
        return zone;
    }

    public void setZone(Box zone) {
        this.zone = zone;
    }

    public Box getLiquiditySweep() {
        return liquiditySweep;
    }

    public void setLiquiditySweep(Box liquiditySweep) {
        this.liquiditySweep = liquiditySweep;
    }

    public Line getLine() {
        return line;
    }

    public void setLine(Line line) {
        this.line = line;
    }

    public boolean isBreakout() {
        return breakout;
    }

    public void setBreakout(boolean breakout) {
        this.breakout = breakout;
    }

    public boolean isTest() {
        return test;
    }

    public void setTest(boolean test) {
        this.test = test;
    }

    public boolean isRetest() {
        return retest;
    }

    public void setRetest(boolean retest) {
        this.retest = retest;
    }

    public boolean isLiquidation() {
        return liquidation;
    }

    public void setLiquidation(boolean liquidation) {
        this.liquidation = liquidation;
    }

    public float getMargin() {
        return margin;
    }

    public void setMargin(float margin) {
        this.margin = margin;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type.toString() + " zone, breakout=" + breakout + ", test=" + test + ", retest=" + retest + ", liquidation=" + liquidation;
    }
}
