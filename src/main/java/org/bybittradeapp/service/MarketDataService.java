package org.bybittradeapp.service;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import org.bybittradeapp.domain.MarketKlineEntry;
import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.bybittradeapp.Main.TEST_OPTION;
import static org.bybittradeapp.Main.mapper;

public class MarketDataService {
    private static final long START_TIMESTAMP = Instant.now().minus(60, ChronoUnit.DAYS).toEpochMilli();
    private static final int MAX_ROWS_LIMIT = 1000;
    private static final String PATH_RESOURCES = "C:\\Users\\dimas\\IdeaProjects\\bybit-trade-app\\src\\main\\resources";

    private final MarketInterval marketInterval = MarketInterval.ONE_MINUTE;
    private final TreeMap<Long, MarketKlineEntry> marketData = _deserialize();
    private final Timer timer = new Timer();
    private final StrategyService strategyService = new StrategyService(this);

    public void start() {
        if (TEST_OPTION) {
            updateMarketData();
            strategyService.performCheck();
        } else {
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    updateMarketData();
                    strategyService.performCheck();
                }
            };
            timer.scheduleAtFixedRate(task, 0, 45000L);
        }
    }

    public void stop() {
        timer.cancel();
    }

    public void updateMarketData() {
        Long latestSavedElement = marketData.keySet().stream()
                .max(Comparator.comparing(Long::longValue))
                .orElse(null);
        getMarketDataProcess(latestSavedElement);
    }

    private void getMarketDataProcess(Long latestSavedElementTimestamp) {

        if (latestSavedElementTimestamp == null) {
            latestSavedElementTimestamp = START_TIMESTAMP;
        }

        BybitApiMarketRestClient client = BybitApiClientFactory
                .newInstance(BybitApiConfig.MAINNET_DOMAIN, false)
                .newMarketDataRestClient();

        boolean isInProcess = true;
        while (isInProcess) {
            double diffMinutes = ((double) (Instant.now().toEpochMilli() - latestSavedElementTimestamp)) / (1000. * 60.);
            int limit;
            if (diffMinutes > 1. && diffMinutes < MAX_ROWS_LIMIT) {
                limit = (int) Math.ceil(diffMinutes);
            } else if (diffMinutes <= 1.) {
                isInProcess = false;
                continue;
            } else {
                limit = MAX_ROWS_LIMIT;
            }

            // request to market data
            MarketDataRequest marketKLineRequest = MarketDataRequest.builder()
                    .category(CategoryType.LINEAR)
                    .symbol("BTCUSDT")
                    .marketInterval(marketInterval)
                    .start(latestSavedElementTimestamp)
                    .limit(limit)
                    .build();

            // get response
            var marketKlineResultRaw = client.getMarketLinesData(marketKLineRequest);
            var marketKlineResultGenericResponse = mapper.convertValue(marketKlineResultRaw, GenericResponse.class);
            var marketKlineResult = mapper.convertValue(marketKlineResultGenericResponse.getResult(), MarketKlineResult.class);

            marketData.putAll(
                    marketKlineResult.getMarketKlineEntries()
                            .stream()
                            .collect(Collectors.toMap(
                                    com.bybit.api.client.domain.market.response.kline.MarketKlineEntry::getStartTime,
                                    MarketDataService::toMarketKlineEntry
                            ))
            );

            latestSavedElementTimestamp = marketKlineResult.getMarketKlineEntries().get(0).getStartTime();

            if (marketKlineResult.getMarketKlineEntries().size() < MAX_ROWS_LIMIT) {
                _serialize();
                isInProcess = false;
            }
        }
    }

    @NotNull
    public static MarketKlineEntry toMarketKlineEntry(@NotNull com.bybit.api.client.domain.market.response.kline.MarketKlineEntry entry) {
        MarketKlineEntry result = new MarketKlineEntry();
        result.setStartTime(entry.getStartTime());
        result.setOpenPrice(Double.parseDouble(entry.getOpenPrice()));
        result.setClosePrice(Double.parseDouble(entry.getClosePrice()));
        result.setHighPrice(Double.parseDouble(entry.getHighPrice()));
        result.setLowPrice(Double.parseDouble(entry.getLowPrice()));
        return result;
    }

    public Long getMarketInterval() {
        if (!List.of("D", "W", "M").contains(marketInterval.getIntervalId()))
            return Long.parseLong(marketInterval.getIntervalId());
        else return 1L;
    }

    public TreeMap<Long, MarketKlineEntry> getMarketData() {
        return marketData;
    }

    private void _serialize() {
        try {
            try (FileOutputStream fileOutputStream = new FileOutputStream(PATH_RESOURCES + "\\data");
                 ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
                objectOutputStream.writeObject(marketData);
                System.out.println("marketData with size: " + marketData.size() + " serialized");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private TreeMap<Long, MarketKlineEntry> _deserialize() {
        try (FileInputStream fileInputStream = new FileInputStream(PATH_RESOURCES + "\\data");
             ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            System.out.println("marketData deserialized");
            return (TreeMap<Long, MarketKlineEntry>) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return new TreeMap<>();
        }
    }
}
