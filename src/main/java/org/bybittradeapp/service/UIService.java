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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;

import static org.bybittradeapp.Main.DAYS_TO_CHECK;
import static org.bybittradeapp.Main.mapper;
import static org.bybittradeapp.utils.JsonUtils.updateMarketDataJson;

public class UIService {
    private static final long START_TIMESTAMP = Instant.now().minus(DAYS_TO_CHECK, ChronoUnit.DAYS).toEpochMilli();
    private static final int MAX_ROWS_LIMIT = 1000;
    private final ArrayList<MarketKlineEntry> uiMarketData = new ArrayList<>();
    private final MarketInterval marketInterval;

    public UIService(MarketInterval marketInterval) {
        this.marketInterval = marketInterval;
    }

    public void updateMarketData() {
        Long latestSavedElement = uiMarketData.stream()
                .map(MarketKlineEntry::getStartTime)
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

            uiMarketData.addAll(
                    marketKlineResult.getMarketKlineEntries()
                            .stream()
                            .map(this::toMarketKlineEntry)
                            .toList());

            latestSavedElementTimestamp = marketKlineResult.getMarketKlineEntries().get(0).getStartTime();

            if (marketKlineResult.getMarketKlineEntries().size() < MAX_ROWS_LIMIT) {
                uiMarketData.sort(Comparator.comparing(MarketKlineEntry::getStartTime));
                updateMarketDataJson(uiMarketData);
                isInProcess = false;
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

    public ArrayList<MarketKlineEntry> getUiMarketData() {
        return uiMarketData;
    }
}
