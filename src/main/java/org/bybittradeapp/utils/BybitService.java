package org.bybittradeapp.utils;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import org.bybittradeapp.domain.MarketKlineEntry;
import org.bybittradeapp.service.MarketDataService;

import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.bybittradeapp.Main.mapper;

public class BybitService {

    public static TreeMap<Long, MarketKlineEntry> getDailyMarketData(int limit) {
        BybitApiMarketRestClient client = BybitApiClientFactory
                .newInstance(BybitApiConfig.MAINNET_DOMAIN, false)
                .newMarketDataRestClient();

        MarketDataRequest marketKLineRequest = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol("BTCUSDT")
                .marketInterval(MarketInterval.DAILY)
                .limit(limit)
                .build();

        // get response
        var marketKlineResultRaw = client.getMarketLinesData(marketKLineRequest);
        var marketKlineResultGenericResponse = mapper.convertValue(marketKlineResultRaw, GenericResponse.class);
        var marketKlineResult = mapper.convertValue(marketKlineResultGenericResponse.getResult(), MarketKlineResult.class);
        return marketKlineResult.getMarketKlineEntries()
                .stream()
                .collect(Collectors.toMap(
                        com.bybit.api.client.domain.market.response.kline.MarketKlineEntry::getStartTime,
                        MarketDataService::toMarketKlineEntry,
                        (oldValue, newValue) -> oldValue,
                        TreeMap::new
                ));
    }
}
