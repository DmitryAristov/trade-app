package org.bybittradeapp.analysis.service;

//TODO(2) написать сервис определения тренда
public class TrendService implements VolatilityListener {

    public enum Trend { UP, NEUTRAL, DOWN;}

    public TrendService() {  }

    public Trend getTrend(int fromElement) {
        return Trend.NEUTRAL;
    }

    @Override
    public void notify(double volatility, double average) {

    }
}
