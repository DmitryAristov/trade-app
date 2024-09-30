package org.bybittradeapp.analysis.service;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import kotlin.Pair;
import org.bybittradeapp.marketdata.domain.MarketEntry;
import org.bybittradeapp.ui.utils.Serializer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.bybittradeapp.Main.*;

public class VolatilityService {

    private final Serializer<Pair<Double, Double>> serializer;
    private double volatility = 0;
    private double average = 0;

    /**
     * Метод определяет волатильность и среднюю цену актива
     */
    public VolatilityService() {
        this.serializer = new Serializer<>("/home/dmitriy/Projects/bybit-trade-app/src/main/resources/volatility-data/");
        Pair<Double, Double> volData = serializer.deserialize();
        if (SKIP_MARKET_DATA_UPDATE && volData != null && volData.getFirst() != 0. && volData.getSecond() != 0.) {
            volatility = volData.getFirst();
            average = volData.getSecond();
            return;
        }

        List<MarketEntry> marketData = getDailyMarketData();
        if (marketData == null || marketData.size() < 2) {
            return;
        }

        double sum = 0;
        List<Double> changes = new ArrayList<>();
        for (MarketEntry marketDatum : marketData) {
            double priceDiff = marketDatum.high() - marketDatum.low();
            double priceAverage = (marketDatum.high() + marketDatum.low()) / 2.;
            sum += priceAverage;
            changes.add(priceDiff / priceAverage);
        }
        this.volatility = changes.stream().reduce(0., Double::sum) / changes.size();
        this.average = sum / marketData.size();
        serializer.serialize(new Pair<>(volatility, average));
    }

    private List<MarketEntry> getDailyMarketData() {

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
                .map(this::toMarketEntry)
                .toList();

    }

    @NotNull
    private MarketEntry toMarketEntry(@NotNull com.bybit.api.client.domain.market.response.kline.MarketKlineEntry entry) {
        return new MarketEntry(Double.parseDouble(entry.getHighPrice()), Double.parseDouble(entry.getLowPrice()));
    }

    public double getVolatility() {
        return volatility;
    }

    public double getAverage() {
        return average;
    }
}
