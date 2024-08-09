package org.bybittradeapp.domain;

import java.awt.Color;

public class Line {
    private long left, right;
    private float top, bottom;
    private int width;
    private Color color;

    public Line() {
        this.left = 0L;
        this.top = 0f;
        this.right = 0L;
        this.bottom = 0f;
        this.width = 0;
    }

    public Line(long left, float top, long right, float bottom, Color color, int width) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.width = width;
        this.color = color;
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
                "x1=" + left +
                ", x2=" + right +
                ", y1=" + top +
                ", y2=" + bottom +
                ", width=" + width +
                ", color=" + color +
                '}';
    }
}