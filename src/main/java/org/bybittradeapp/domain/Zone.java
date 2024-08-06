package org.bybittradeapp.domain;

import java.util.List;

public class Zone {
    double min;
    double max;
    List<TimePeriod> times;

    public class TimePeriod {
        long startTime;
        long finishTime;
    }
}
