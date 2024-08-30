package org.bybittradeapp.analysis.service;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import org.bybittradeapp.marketData.domain.MarketKlineEntry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.bybittradeapp.Main.HISTORICAL_DATA_SIZE;
import static org.bybittradeapp.Main.SYMBOL;
import static org.bybittradeapp.Main.mapper;

public class VolatilityService {

    private double volatility = 0;

    /**
     * Method returns volatility of provided marked data in percents
     */
    public VolatilityService() {
        List<MarketKlineEntry> marketData = getDailyMarketData();
        if (marketData == null || marketData.size() < 2) {
            return;
        }

        List<Double> changes = new ArrayList<>();
        for (MarketKlineEntry marketDatum : marketData) {
            double priceDiff = marketDatum.getHighPrice() - marketDatum.getLowPrice();
            double priceAverage = (marketDatum.getHighPrice() + marketDatum.getLowPrice()) / 2.;
            changes.add(priceDiff / priceAverage);
        }
        this.volatility = changes.stream().reduce(0., Double::sum) / changes.size() * 100.;
    }

    private List<MarketKlineEntry> getDailyMarketData() {

        BybitApiMarketRestClient client = BybitApiClientFactory
                .newInstance(BybitApiConfig.MAINNET_DOMAIN, false)
                .newMarketDataRestClient();

        // request to market data
        MarketDataRequest marketKLineRequest = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol(SYMBOL)
                .marketInterval(MarketInterval.DAILY)
                .limit(HISTORICAL_DATA_SIZE)
                .build();

        // get response
        var marketKlineResultRaw = client.getMarketLinesData(marketKLineRequest);
        var marketKlineResultGenericResponse = mapper.convertValue(marketKlineResultRaw, GenericResponse.class);
        var marketKlineResult = mapper.convertValue(marketKlineResultGenericResponse.getResult(), MarketKlineResult.class);

        return marketKlineResult.getMarketKlineEntries()
                .stream()
                .map(this::toMarketKlineEntry)
                .toList();

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

    public double getVolatility() {
        return volatility;
    }
}
