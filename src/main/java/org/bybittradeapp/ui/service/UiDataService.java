package org.bybittradeapp.ui.service;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import org.bybittradeapp.marketdata.domain.MarketKlineEntry;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;

import static org.bybittradeapp.Main.HISTORICAL_DATA_SIZE;
import static org.bybittradeapp.Main.SYMBOL;
import static org.bybittradeapp.Main.mapper;
import static org.bybittradeapp.ui.utils.JsonUtils.updateMarketData;

public class UiDataService {
    private static final long START_TIMESTAMP = Instant.now().minus(HISTORICAL_DATA_SIZE, ChronoUnit.DAYS).toEpochMilli();
    private static final int MAX_ROWS_LIMIT = 1000;
    private final ArrayList<MarketKlineEntry> uiMarketData = new ArrayList<>();
    private final MarketInterval marketInterval;

    public UiDataService(MarketInterval marketInterval) {
        this.marketInterval = marketInterval;
        getMarketDataProcess();
    }

    private void getMarketDataProcess() {
        long latestSavedElementTimestamp = START_TIMESTAMP;

        BybitApiMarketRestClient client = BybitApiClientFactory
                .newInstance(BybitApiConfig.MAINNET_DOMAIN, false)
                .newMarketDataRestClient();

        boolean process = true;
        while (process) {
            double unprocessedEntriesCount = ((double) (Instant.now().toEpochMilli() - latestSavedElementTimestamp)) / (double) toMills(marketInterval);
            int limit;
            if (unprocessedEntriesCount > 1. && unprocessedEntriesCount < MAX_ROWS_LIMIT) {
                limit = (int) Math.ceil(unprocessedEntriesCount);
            } else if (unprocessedEntriesCount <= 1.) {
                process = false;
                continue;
            } else {
                limit = MAX_ROWS_LIMIT;
            }

            // request to market data
            MarketDataRequest marketKLineRequest = MarketDataRequest.builder()
                    .category(CategoryType.LINEAR)
                    .symbol(SYMBOL)
                    .marketInterval(marketInterval)
                    .start(latestSavedElementTimestamp)
                    .limit(limit)
                    .build();

            // get response
            var marketKlineResultRaw = client.getMarketLinesData(marketKLineRequest);
            var marketKlineResultGenericResponse = mapper.convertValue(marketKlineResultRaw, GenericResponse.class);
            var marketKlineResult = mapper.convertValue(marketKlineResultGenericResponse.getResult(), MarketKlineResult.class);

            uiMarketData.addAll(
                    marketKlineResult.getMarketKlineEntries()
                            .stream()
                            .map(this::toMarketKlineEntry)
                            .toList());

            latestSavedElementTimestamp = marketKlineResult.getMarketKlineEntries().get(0).getStartTime();

            if (marketKlineResult.getMarketKlineEntries().size() < MAX_ROWS_LIMIT) {
                uiMarketData.sort(Comparator.comparing(MarketKlineEntry::getStartTime));
                updateMarketData(uiMarketData);
                process = false;
            }
        }
    }

    private static long toMills(MarketInterval interval) {
        return switch (interval) {
            case MONTHLY -> 30L * 24L * 60L * 60L * 1000L;
            case WEEKLY -> 7L * 24L * 60L * 60L * 1000L;
            case DAILY -> 24L * 60L * 60L * 1000L;
            case TWELVE_HOURLY -> 12L * 60L * 60L * 1000L;
            case SIX_HOURLY -> 6L * 60L * 60L * 1000L;
            case FOUR_HOURLY -> 4L * 60L * 60L * 1000L;
            case TWO_HOURLY -> 2L * 60L * 60L * 1000L;
            case HOURLY -> 60L * 60L * 1000L;
            case HALF_HOURLY -> 30L * 60L * 1000L;
            case FIFTEEN_MINUTES -> 15L * 60L * 1000L;
            case FIVE_MINUTES -> 5L * 60L * 1000L;
            case THREE_MINUTES -> 3L * 60L * 1000L;
            case ONE_MINUTE -> 60L * 1000L;
        };
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

    public ArrayList<MarketKlineEntry> getMarketData() {
        return uiMarketData;
    }
}
