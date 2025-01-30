package org.tradeapp.marketdata.service;

import org.tradeapp.logging.Log;
import org.tradeapp.marketdata.domain.MarketEntry;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class FileMarketDataLoader {

    public static final int HISTORICAL_DATA_SIZE = 7 * 365 + 170;
    private static final long START_TIME = Instant.now().minus(HISTORICAL_DATA_SIZE, ChronoUnit.DAYS).toEpochMilli();
    private static final int MAX_ROWS_LIMIT = 1000;
    private final Log log = new Log();

    private final String filePath;
    private final long interval = 1000;
    private final long fullSize;
    private long currentSize = 0;
    private final long startTime = Instant.now().toEpochMilli();
    private final AtomicLong step = new AtomicLong(0L);

    public FileMarketDataLoader(String filePath) {
        this.filePath = filePath;
        this.fullSize = (Instant.now().toEpochMilli() - START_TIME) / interval;
    }

    public void updateOrDownloadData() {
        Long latestEntry = readLatestSavedEntry();

        if (latestEntry == null) {
            latestEntry = START_TIME;
        } else {
            latestEntry += 1000L;
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
            TreeMap<Long, MarketEntry> result = ExchangeRequestService.performBinanceVMarketDataRequest("1s", latestEntry, limit);
            appendEntries(result);

            currentSize += result.size();

            double progress = ((double) currentSize) / ((double) fullSize);
            log.logProgress(startTime, step, progress, "market-data-downloading", latestEntry);

            latestEntry = result.lastKey();

            if (result.size() < MAX_ROWS_LIMIT) {
                process = false;
            }
        }
    }

    private Long readLatestSavedEntry() {
        var lastYearEntrySet = readAllEntries(2025);
        if (lastYearEntrySet.isEmpty()) {
            return null;
        }
        return lastYearEntrySet.lastKey();
    }

    private void appendEntries(TreeMap<Long, MarketEntry> data) {
        ZonedDateTime zonedDateTime = Instant.ofEpochMilli(data.firstKey()).atZone(ZoneId.of("UTC"));
        int year = zonedDateTime.getYear();
        Path path = Paths.get(filePath + "_" + year + ".txt");

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            data.forEach((time, entry) -> {
                try {
                    writer.write(formatEntry(time, entry.high(), entry.low(), entry.volume()));
                    writer.newLine();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write line");
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to write lines");
        }
    }

    public TreeMap<Long, MarketEntry> readAllEntries(int year) {
        TreeMap<Long, MarketEntry> resultMap = new TreeMap<>();

        Path path = Paths.get(filePath + "_" + year + ".txt");
        try (Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> {
                Map.Entry<Long, MarketEntry> parsedEntry = parseEntry(line);
                if (parsedEntry != null) {
                    resultMap.put(parsedEntry.getKey(), parsedEntry.getValue());
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to read lines");
        }
        return resultMap;
    }

    private String formatEntry(Long timestamp, double high, double low, double volume) {
        return String.format("%d,%.2f,%.2f,%.2f", timestamp, high, low, volume);
    }

    private Map.Entry<Long, MarketEntry> parseEntry(String line) {
        try {
            String[] parts = line.split(",");
            if (parts.length != 4) {
                return null;
            }
            Long timestamp = Long.parseLong(parts[0]);
            double high = Double.parseDouble(parts[1]);
            double low = Double.parseDouble(parts[2]);
            double volume = Double.parseDouble(parts[3]);
            return new AbstractMap.SimpleEntry<>(timestamp, new MarketEntry(high, low, volume));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Failed to parse line");
        }
    }
}
