package org.bybittradeapp.domain;

import java.awt.*;

public class Line {
    private long x1, x2;
    private float y1, y2;
    private int width;
    private Color color;

    public Line() {
        this.x1 = 0L;
        this.y1 = 0f;
        this.x2 = 0L;
        this.y2 = 0f;
        this.width = 0;
    }

    public Line(long x1, float y1, long x2, float y2, Color color, int width) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.width = width;
        this.color = color;
    }

    public long getX1() {
        return x1;
    }

    public void setX1(long x1) {
        this.x1 = x1;
    }

    public long getX2() {
        return x2;
    }

    public void setX2(long x2) {
        this.x2 = x2;
    }

    public float getY1() {
        return y1;
    }

    public void setY1(float y1) {
        this.y1 = y1;
    }

    public float getY2() {
        return y2;
    }

    public void setY2(float y2) {
        this.y2 = y2;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return "Line{" +
                "x1=" + x1 +
                ", x2=" + x2 +
                ", y1=" + y1 +
                ", y2=" + y2 +
                ", width=" + width +
                ", color=" + color +
                '}';
    }
}