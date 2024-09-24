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
import org.apache.commons.lang3.StringUtils;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.IntervalType;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.bybittradeapp.Main.*;
import static org.bybittradeapp.logging.Log.logProgress;

public class MarketDataService {

    private static final long START_TIME = Instant.now().minus(HISTORICAL_DATA_SIZE, ChronoUnit.DAYS).toEpochMilli();
    private static final int MAX_ROWS_LIMIT = 1000;
    public static final String PATH_RESOURCES = "/home/dmitriy/Projects/bybit-trade-app/src/main/resources";

    private final IntervalType intervalType;
    private final TreeMap<Long, Double> marketData;
    private final long fullSize;
    private final long startTime;
    private final AtomicLong step;
    private double progress;

    public MarketDataService() {
        this.intervalType = IntervalType.SECOND;
        this.marketData = _deserialize();

        if (marketData.isEmpty()) {
            this.fullSize = (Instant.now().toEpochMilli() - START_TIME) / 1000L;
        } else {
            this.fullSize = (Instant.now().toEpochMilli() - marketData.firstKey()) / 1000L;
        }
        this.progress = ((double) marketData.size()) / ((double) fullSize);
        this.startTime = Instant.now().toEpochMilli();
        this.step = new AtomicLong(0L);

        if (SKIP_MARKET_DATA_UPDATE && !marketData.isEmpty()) return;

        Long latestSavedElement = null;
        if (!marketData.isEmpty()) {
            latestSavedElement = marketData.lastKey();
            Log.log(String.format("found %d (%.2f%%) of %d. Latest was %s", marketData.size(), progress * 100., fullSize, Instant.ofEpochMilli(latestSavedElement)));
        } else {
            Log.log(String.format("saved rows not found. Size to get %d", fullSize));
        }
        getMarketDataProcess(latestSavedElement);
    }

    private void getMarketDataProcess(Long latestSavedElementTimestamp) {

        if (latestSavedElementTimestamp == null) {
            latestSavedElementTimestamp = START_TIME;
        }

        BybitApiMarketRestClient client = BybitApiClientFactory.newInstance(BybitApiConfig.MAINNET_DOMAIN, false)
                .newMarketDataRestClient();

        boolean process = true;
        while (process) {
            double unprocessedEntriesCount = ((double) (Instant.now().toEpochMilli() - latestSavedElementTimestamp)) / (double) intervalType.getMills();
            int limit;
            if (unprocessedEntriesCount > 1. && unprocessedEntriesCount < MAX_ROWS_LIMIT) {
                limit = (int) Math.ceil(unprocessedEntriesCount);
            } else if (unprocessedEntriesCount <= 1.) {
                process = false;
                continue;
            } else {
                limit = MAX_ROWS_LIMIT;
            }

            TreeMap<Long, Double> result = switch (intervalType) {
                case SECOND -> performBinanceMarketDataRequest(latestSavedElementTimestamp, limit);
                case MINUTE -> performBybitMarketDataRequest(latestSavedElementTimestamp, limit, client);
            };

            marketData.putAll(result);

            this.progress = ((double) marketData.size()) / ((double) fullSize);
            logProgress(startTime, step, progress, "marketdata");

            latestSavedElementTimestamp = result.lastKey();

            if (result.size() < MAX_ROWS_LIMIT) {
                _serialize(marketData);
                process = false;
            }
        }
    }

    private void _serialize(TreeMap<Long, Double> marketMap) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(PATH_RESOURCES + "/market_data_" + intervalType.toString());
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)
        ) {
            Log.log("serializing...");
            objectOutputStream.writeObject(marketMap);
            Log.log("marketData [" + marketMap.size() + "] serialized");
        } catch (IOException ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private TreeMap<Long, Double> _deserialize() {
        try (FileInputStream fileInputStream = new FileInputStream(PATH_RESOURCES + "/market_data_" + intervalType.toString());
             ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)
        ) {
            Log.log("deserializing...");
            TreeMap<Long, Double> result = (TreeMap<Long, Double>) objectInputStream.readObject();
            Log.log("marketData [" + result.size() + "] deserialized");
            return result;
        } catch (ClassNotFoundException | IOException e) {
            return new TreeMap<>();
        }
    }

    public TreeMap<Long, Double> performBinanceMarketDataRequest(long start, int limit) {

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

            List<List<Object>> klineDataList = mapper.readValue(response.toString(), new TypeReference<>() {});
            TreeMap<Long, Double> priceMap = new TreeMap<>();

            for (List<Object> kline : klineDataList) {
                long openTime = ((Number) kline.get(0)).longValue();

                double highPrice = Double.parseDouble((String) kline.get(2));
                double lowPrice = Double.parseDouble((String) kline.get(3));

                double averagePrice = (highPrice + lowPrice) / 2.;

                priceMap.put(openTime, averagePrice);
            }
            return priceMap;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public TreeMap<Long, Double> getMarketData() {
        return marketData;
    }

    private TreeMap<Long, Double> performBybitMarketDataRequest(Long latestSavedElementTimestamp, int limit, BybitApiMarketRestClient client) {
        // request to market data
        MarketDataRequest marketKLineRequest = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol(SYMBOL)
                .marketInterval(MarketInterval.ONE_MINUTE)
                .start(latestSavedElementTimestamp)
                .limit(limit)
                .build();

        // get response
        var marketKlineResultRaw = client.getMarketLinesData(marketKLineRequest);
        var marketKlineResultGenericResponse = mapper.convertValue(marketKlineResultRaw, GenericResponse.class);
        var marketKlineResult = mapper.convertValue(marketKlineResultGenericResponse.getResult(), MarketKlineResult.class);
        TreeMap<Long, Double> priceMap = new TreeMap<>();
        marketKlineResult.getMarketKlineEntries()
                .forEach(entry -> priceMap.put(entry.getStartTime(),
                        (Double.parseDouble(entry.getHighPrice()) + Double.parseDouble(entry.getLowPrice())) / 2.));

        return priceMap;
    }
}



