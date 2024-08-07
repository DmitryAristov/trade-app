package org.bybittradeapp.domain;

public class PivotPoint {
    private long x;
    private long x1;
    private float h;
    private float h1;
    private float l;
    private float l1;
    private boolean hx;
    private boolean lx;

    public PivotPoint() {
        this.x = 0;
        this.x1 = 0;
        this.h = 0.0f;
        this.h1 = 0.0f;
        this.l = 0.0f;
        this.l1 = 0.0f;
        this.hx = false;
        this.lx = false;
    }

    public long getX() {
        return x;
    }

    public void setX(long x) {
        this.x = x;
    }

    public long getX1() {
        return x1;
    }

    public void setX1(long x1) {
        this.x1 = x1;
    }

    public float getH() {
        return h;
    }

    public void setH(float h) {
        this.h = h;
    }

    public float getH1() {
        return h1;
    }

    public void setH1(float h1) {
        this.h1 = h1;
    }

    public float getL() {
        return l;
    }

    public void setL(float l) {
        this.l = l;
    }

    public float getL1() {
        return l1;
    }

    public void setL1(float l1) {
        this.l1 = l1;
    }

    public boolean isHx() {
        return hx;
    }

    public void setHx(boolean hx) {
        this.hx = hx;
    }

    public boolean isLx() {
        return lx;
    }

    public void setLx(boolean lx) {
        this.lx = lx;
    }
}
