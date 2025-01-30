package org.tradeapp.marketdata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import org.tradeapp.marketdata.domain.MarketEntry;
import org.tradeapp.ui.domain.MarketKlineEntry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.tradeapp.backtest.constants.Constants.*;


/**
 * Класс-утилита для отправки запросов на биржу
 */
public class ExchangeRequestService {

//    /**
//     * Bybit API предоставляет минимум минутные исторические данные
//     */
//    public static TreeMap<Long, MarketKlineEntry> performBinanceUiMarketDataRequest(long start, int limit) {
//        List<List<Object>> klineDataList = performBinanceMarketDataRequest(UI_DATA_INTERVAL_STRING, start, limit);
//
//        TreeMap<Long, MarketKlineEntry> priceMap = new TreeMap<>();
//        for (List<Object> kline : klineDataList) {
//            MarketKlineEntry marketKlineEntry = getMarketKlineEntry(kline);
//            priceMap.put(marketKlineEntry.getOpenTime(), marketKlineEntry);
//        }
//        return priceMap;
//    }

    /**
     * Binance API предоставляет ежесекундные исторические данные
     */
    public static TreeMap<Long, MarketEntry> performBinanceMarketDataRequest(long start, int limit) {
        return performBinanceVMarketDataRequest("1s", start, limit);
    }

    public static TreeMap<Long, MarketEntry> performBinanceVMarketDataRequest(String interval, long start, int limit) {
        List<List<Object>> klineDataList = performBinanceMarketDataRequest(interval, start, limit);

        TreeMap<Long, MarketEntry> priceMap = new TreeMap<>();
        for (List<Object> kline : klineDataList) {
            long openTime = ((Number) kline.get(0)).longValue();

            double highPrice = Double.parseDouble((String) kline.get(2));
            double lowPrice = Double.parseDouble((String) kline.get(3));
            double volume = Double.parseDouble((String) kline.get(5));

            priceMap.put(openTime, new MarketEntry(highPrice, lowPrice, volume));
        }
        return priceMap;
    }

    private static MarketKlineEntry getMarketKlineEntry(List<Object> kline) {
        long openTime = ((Number) kline.get(0)).longValue();
        double openPrice = Double.parseDouble((String) kline.get(1));
        double highPrice = Double.parseDouble((String) kline.get(2));
        double lowPrice = Double.parseDouble((String) kline.get(3));
        double closePrice = Double.parseDouble((String) kline.get(4));
        double volume = Double.parseDouble((String) kline.get(5));

        MarketKlineEntry marketKlineEntry = new MarketKlineEntry();
        marketKlineEntry.setOpenTime(openTime);
        marketKlineEntry.setOpenPrice(openPrice);
        marketKlineEntry.setClosePrice(closePrice);
        marketKlineEntry.setHighPrice(highPrice);
        marketKlineEntry.setLowPrice(lowPrice);
        marketKlineEntry.setVolume(volume);
        return marketKlineEntry;
    }

    public static List<List<Object>> performBinanceMarketDataRequest(String interval, long start, int limit) {
        String requestUrl = String.format("https://api.binance.com/api/v3/klines?" +
                        "symbol=%s&" +
                        "interval=%s&" +
                        "limit=%s&" +
                        "startTime=%s",
                SYMBOL, interval, limit, start);

        try {
            URI uri = URI.create(requestUrl);
            URL url = uri.toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String response = reader.lines().collect(Collectors.joining());
                    return mapper.readValue(response, new TypeReference<>() {});
                }
            } else {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    String errorResponse = reader.lines().collect(Collectors.joining());
                    throw new RuntimeException(errorResponse);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
