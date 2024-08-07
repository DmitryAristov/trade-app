package org.bybittradeapp.domain;

import java.awt.Color;

public class Box {
    private long left, right;
    private float top, bottom;
    private Color color;

    public Box() {
        this.left = 0L;
        this.top = 0L;
        this.right = 0L;
        this.bottom = 0L;
    }

    public Box(long left, float top, long right, float bottom, Color color) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
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

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    @Override
    public String toString() {
        return "Box{" +
                "left=" + left +
                ", right=" + right +
                ", top=" + top +
                ", bottom=" + bottom +
                ", color=" + color +
                '}';
    }
}
