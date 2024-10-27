package org.bybittradeapp.marketdata.service;

import org.bybittradeapp.logging.Log;
import org.bybittradeapp.ui.utils.Serializer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static org.bybittradeapp.Main.*;
import static org.bybittradeapp.logging.Log.logProgress;

public class MarketDataLoader<T extends TreeMap<Long, ? extends Object>> {

    private static final long START_TIME = Instant.now().minus(HISTORICAL_DATA_SIZE, ChronoUnit.DAYS).toEpochMilli();
    private static final int MAX_ROWS_LIMIT = 1000;

    private final Serializer<T> serializer;
    private final long interval;
    private final BiFunction<Long, Integer, T> function;

    private T data;
    private final long fullSize;
    private final long startTime;
    private final AtomicLong step;
    private double progress;

    public MarketDataLoader(Serializer<T> serializer,
                            long interval,
                            BiFunction<Long, Integer, T> function
    ) {
        this.serializer = serializer;
        this.interval = interval;
        this.data = serializer.deserialize();
        this.startTime = Instant.now().toEpochMilli();
        this.step = new AtomicLong(0L);
        this.function = function;
        if (data == null) {
            data = (T) new TreeMap<Long, Object>();
        }

        if (data.isEmpty()) {
            this.fullSize = (Instant.now().toEpochMilli() - START_TIME) / interval;
        } else {
            this.fullSize = (Instant.now().toEpochMilli() - data.firstKey()) / interval;
        }
        this.progress = ((double) data.size()) / ((double) fullSize);

        if (SKIP_MARKET_DATA_UPDATE && data != null && !data.isEmpty()) {
            return;
        }

        Long latestSavedElement = null;
        if (!data.isEmpty()) {
            latestSavedElement = data.lastKey();
            Log.debug(String.format("found %d (%.2f%%) of %d. Latest was ", data.size(), progress * 100., fullSize), latestSavedElement);
        } else {
            Log.debug(String.format("saved rows not found. Size to get %d", fullSize));
        }

        getDataProcess(latestSavedElement);
    }

    private void getDataProcess(Long latestEntry) {
        if (latestEntry == null) {
            latestEntry = START_TIME;
        }

        boolean process = true;
        while (process) {
            double unprocessedEntriesCount = ((double) (Instant.now().toEpochMilli() - latestEntry)) / (double) interval;
            int limit;
            if (unprocessedEntriesCount > 1. && unprocessedEntriesCount < MAX_ROWS_LIMIT) {
                limit = (int) Math.ceil(unprocessedEntriesCount);
            } else if (unprocessedEntriesCount <= 1.) {
                process = false;
                continue;
            } else {
                limit = MAX_ROWS_LIMIT;
            }

            Map result = function.apply(latestEntry, limit);
            data.putAll(result);

            this.progress = ((double) data.size()) / ((double) fullSize);
            logProgress(startTime, step, progress, "data-loading");

            latestEntry = data.lastKey();

            if (result.size() < MAX_ROWS_LIMIT) {
                serializer.serialize(data);
                process = false;
            }
        }
    }

    public T getData() {
        return data;
    }
}
