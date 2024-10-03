package org.bybittradeapp.analysis.service;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.bybittradeapp.Main.*;

public class VolatilityService {

    /**
     * Период обновления волатильности и средней цены (1000мс * 60с * 60м * 24ч = 1 день)
     */
    private static final long UPDATE_TIME_PERIOD_MILLS = 24L * 60L * 60L * 1000L;
    private static final int VOLATILITY_CALCULATE_DAYS_COUNT = 60;
    private static final int AVERAGE_PRICE_CALCULATE_DAYS_COUNT = 5;

    private long lastUpdateTime = -1L;

    private final List<VolatilityListener> listeners = new ArrayList<>();

    public VolatilityService() {  }

    public void onTick(long currentTime, MarketEntry currentEntry) {
        if (currentTime - lastUpdateTime > UPDATE_TIME_PERIOD_MILLS) {
            double volatility = calculateVolatility(currentTime);
            double average = calculateAverage(currentTime);
            Log.log(String.format("calculated new volatility=%.2f%% and average=%.2f$", volatility * 100., average));
            listeners.forEach(listener -> listener.notify(volatility, average));
            lastUpdateTime = currentTime;
        }
    }

    /**
     * Метод определяет волатильность актива
     */
    private double calculateVolatility(long currentTime) {
        List<MarketEntry> marketData = getMarketData(currentTime - VOLATILITY_CALCULATE_DAYS_COUNT * 24L * 60L * 60L * 1000L,
                VOLATILITY_CALCULATE_DAYS_COUNT, MarketInterval.DAILY);

        if (marketData == null || marketData.size() < 2) {
            return 0.;
        }

        List<Double> changes = new ArrayList<>();
        for (MarketEntry marketDatum : marketData) {
            double priceDiff = marketDatum.high() - marketDatum.low();
            double priceAverage = (marketDatum.high() + marketDatum.low()) / 2.;
            changes.add(priceDiff / priceAverage);
        }
        return changes.stream().reduce(0., Double::sum) / changes.size();
    }

    /**
     * Метод определяет среднюю цену актива
     */
    private double calculateAverage(long currentTime) {
        List<MarketEntry> marketData = getMarketData(currentTime - AVERAGE_PRICE_CALCULATE_DAYS_COUNT * 24L * 60L * 60L * 1000L,
                AVERAGE_PRICE_CALCULATE_DAYS_COUNT * 24 * 4, MarketInterval.FIFTEEN_MINUTES);

        if (marketData == null || marketData.size() < 2) {
            return 0.;
        }
        var subMarketData = marketData.subList(Math.max(marketData.size() - AVERAGE_PRICE_CALCULATE_DAYS_COUNT, 0), marketData.size());

        double sum = 0;
        for (MarketEntry marketDatum : subMarketData) {
            double priceAverage = (marketDatum.high() + marketDatum.low()) / 2.;
            sum += priceAverage;
        }
        return sum / subMarketData.size();
    }

    private List<MarketEntry> getMarketData(long start, int limit, MarketInterval interval) {

        BybitApiMarketRestClient client = BybitApiClientFactory
                .newInstance(BybitApiConfig.MAINNET_DOMAIN, false)
                .newMarketDataRestClient();

        // request to market data
        MarketDataRequest marketKLineRequest = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol(SYMBOL)
                .start(start)
                .marketInterval(interval)
                .limit(limit)
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

    public void subscribe(VolatilityListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void subscribeAll(List<VolatilityListener> listeners_) {
        listeners_.forEach(this::subscribe);
    }

    public void unsubscribe(VolatilityListener listener) {
        listeners.remove(listener);
    }
}
