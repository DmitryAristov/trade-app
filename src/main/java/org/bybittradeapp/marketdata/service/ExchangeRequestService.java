package org.bybittradeapp.marketdata.service;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;
import org.bybittradeapp.ui.domain.MarketKlineEntry;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.bybittradeapp.Main.*;


/**
 * Класс-утилита для отправки запросов на биржу
 */
public class ExchangeRequestService {

    /**
     * Bybit API предоставляет минимум минутные исторические данные
     */
    public static TreeMap<Long, MarketKlineEntry> performBybitMarketDataRequest(long start, int limit) {
        MarketDataRequest marketKLineRequest = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol(SYMBOL)
                .marketInterval(UI_DATA_INTERVAL)
                .start(start)
                .limit(limit)
                .build();
        BybitApiMarketRestClient client = BybitApiClientFactory
                .newInstance(BybitApiConfig.MAINNET_DOMAIN, false)
                .newMarketDataRestClient();
        var marketKlineResultRaw = client.getMarketLinesData(marketKLineRequest);
        var marketKlineResultGenericResponse = MAPPER.convertValue(marketKlineResultRaw, GenericResponse.class);
        var marketKlineResult = MAPPER.convertValue(marketKlineResultGenericResponse.getResult(), MarketKlineResult.class);
        return marketKlineResult.getMarketKlineEntries()
                .stream()
                .collect(Collectors.toMap(
                        com.bybit.api.client.domain.market.response.kline.MarketKlineEntry::getStartTime,
                        ExchangeRequestService::toMarketKlineEntry,
                        (first, second) -> first,
                        TreeMap::new)
                );
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

    /**
     * Binance API предоставляет ежесекундные исторические данные
     */
    public static TreeMap<Long, MarketEntry> performBinanceMarketDataRequest(long start, int limit) {

        String requestUrl = String.format("https://api.binance.com/api/v3/klines?" + "symbol=%s&interval=1s&limit=%s&startTime=%s", SYMBOL, limit, start);

        try {
            URI uri = URI.create(requestUrl);
            URL url = uri.toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            List<List<Object>> klineDataList = MAPPER.readValue(response.toString(), new TypeReference<>() {});
            TreeMap<Long, MarketEntry> priceMap = new TreeMap<>();

            for (List<Object> kline : klineDataList) {
                long openTime = ((Number) kline.get(0)).longValue();

                double highPrice = Double.parseDouble((String) kline.get(2));
                double lowPrice = Double.parseDouble((String) kline.get(3));

                priceMap.put(openTime, new MarketEntry(highPrice, lowPrice));
            }
            return priceMap;
        } catch (Exception e) {
            Log.debug("performBinanceMarketDataRequest throws exception: " +
                    e.getMessage() + "\n" +
                    Arrays.toString(e.getStackTrace()));
            throw new RuntimeException(e);
        }
    }

    public static long toMills(MarketInterval interval) {
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
}
