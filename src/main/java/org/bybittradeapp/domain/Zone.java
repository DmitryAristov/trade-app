package org.bybittradeapp.domain;

import java.awt.*;

import static org.bybittradeapp.service.SupportResistanceService.resistanceZoneColor;
import static org.bybittradeapp.service.SupportResistanceService.supportZoneColor;

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

    private long left, right;
    private float top, bottom;
    private boolean breakout;
    private boolean test;
    private boolean retest;
    private boolean liquidation;
    private float margin;

    private Color color;
    private Type type;

    public Zone(Type type) {
        this.left = 0L;
        this.top = 0f;
        this.right = 0L;
        this.bottom = 0f;
        this.breakout = false;
        this.test = false;
        this.retest = false;
        this.liquidation = false;
        this.margin = 0f;
        this.type = type;
        this.color = type == Type.SUPPORT ? supportZoneColor : resistanceZoneColor;
    }

    public Zone(long left,
                float top,
                long right,
                float bottom,
                Color color,
                boolean breakout,
                boolean test,
                boolean retest,
                boolean liquidation,
                float margin,
                Type type
    ) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.color = color;
        this.breakout = breakout;
        this.test = test;
        this.retest = retest;
        this.liquidation = liquidation;
        this.margin = margin;
        this.type = type;
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
    public long getLeft() {
        return left;
    }

    public void setLeft(long left) {
        this.left = left;
    }

    public long getRight() {
        return right;
    }

    public void setRight(long right) {
        this.right = right;
    }

    public float getTop() {
        return top;
    }

    public void setTop(float top) {
        this.top = top;
    }

    public float getBottom() {
        return bottom;
    }

    public void setBottom(float bottom) {
        this.bottom = bottom;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
    @Override
    public String toString() {
        return type.toString() +
                " zone, breakout=" + breakout +
                ", test=" + test +
                ", retest=" + retest +
                ", liquidation=" + liquidation +
                ", left=" + left +
                ", right=" + right +
                ", top=" + top +
                ", bottom=" + bottom;
    }
}
