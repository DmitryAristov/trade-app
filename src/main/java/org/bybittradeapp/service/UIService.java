package org.bybittradeapp.service;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import org.bybittradeapp.domain.Imbalance;
import org.bybittradeapp.domain.MarketKlineEntry;
import org.bybittradeapp.domain.Trend;
import org.bybittradeapp.domain.Zone;
import org.bybittradeapp.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.bybittradeapp.Main.TEST_OPTION;
import static org.bybittradeapp.Main.mapper;
import static org.bybittradeapp.utils.JsonUtils.updateMarketDataJson;

public class UIService {
    private static final long START_TIMESTAMP = Instant.now().minus(240, ChronoUnit.DAYS).toEpochMilli();
    private static final int MAX_ROWS_LIMIT = 1000;
    private final TreeMap<Long, MarketKlineEntry> uiMarketData = new TreeMap<>();
    private final MarketInterval marketInterval;
    private final Timer timer = new Timer();


    public UIService() {
        this.marketInterval = MarketInterval.DAILY;
    }

    public UIService(MarketInterval marketInterval) {
        this.marketInterval = marketInterval;
    }

    public void start() {
        if (TEST_OPTION) {
            updateMarketData();
            return;
        }
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                updateMarketData();
            }
        };
        long taskPeriod = switch (marketInterval) {
            case ONE_MINUTE -> 60000L;
            case THREE_MINUTES -> 180000L;
            default -> 300000L;
        };
        timer.scheduleAtFixedRate(task, 0, taskPeriod);
    }

    public void stop() {
        timer.cancel();
    }

    public void updateMarketData() {
        Long latestSavedElement = uiMarketData.keySet().stream()
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
            long diffMills = (Instant.now().toEpochMilli() - latestSavedElementTimestamp);
            double diffFourHours = ((double) diffMills) / (1000. * 60. * 60. * 4.);
            int limit;
            if (diffFourHours > 1. && diffFourHours < MAX_ROWS_LIMIT) {
                limit = (int) Math.ceil(diffFourHours);
            } else if (diffFourHours <= 1.) {
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

            uiMarketData.putAll(
                    marketKlineResult.getMarketKlineEntries()
                            .stream()
                            .collect(Collectors.toMap(
                                    com.bybit.api.client.domain.market.response.kline.MarketKlineEntry::getStartTime,
                                    this::toMarketKlineEntry
                            ))
            );

            latestSavedElementTimestamp = marketKlineResult.getMarketKlineEntries().get(0).getStartTime();

            if (marketKlineResult.getMarketKlineEntries().size() < MAX_ROWS_LIMIT) {
                isInProcess = false;
                updateMarketDataJson(uiMarketData);
            }
        }
    }

    @NotNull
    private MarketKlineEntry toMarketKlineEntry(@NotNull com.bybit.api.client.domain.market.response.kline.MarketKlineEntry entry) {
        MarketKlineEntry result = new MarketKlineEntry();
        result.setStartTime(entry.getStartTime());
        result.setOpenPrice(Double.parseDouble(entry.getOpenPrice()));
        result.setClosePrice(Double.parseDouble(entry.getClosePrice()));
        result.setHighPrice(Double.parseDouble(entry.getHighPrice()));
        result.setLowPrice(Double.parseDouble(entry.getLowPrice()));
        return result;
    }

    public void updateAnalysedDataJson(List<Zone> zones, Set<Imbalance> imbalances, Trend trend) {
        JsonUtils.updateAnalysedDataJson(zones, imbalances, trend, uiMarketData);
    }
}
