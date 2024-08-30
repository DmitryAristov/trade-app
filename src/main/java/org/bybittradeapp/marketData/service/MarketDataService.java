package org.bybittradeapp.marketData.service;

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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.bybittradeapp.Main.HISTORICAL_DATA_SIZE;
import static org.bybittradeapp.Main.SYMBOL;
import static org.bybittradeapp.Main.mapper;

public class MarketDataService {
    private static final long START_TIMESTAMP = Instant.now().minus(HISTORICAL_DATA_SIZE, ChronoUnit.DAYS).toEpochMilli();
    private static final int MAX_ROWS_LIMIT = 1000;
    private static final String PATH_RESOURCES = "C:\\Users\\dimas\\IdeaProjects\\bybit-trade-app\\src\\main\\resources";

    private final ArrayList<MarketKlineEntry> marketData = _deserialize();

    public void updateMarketData() {
        Long latestSavedElement = null;
        if (!marketData.isEmpty()) {
            latestSavedElement = marketData.get(marketData.size() - 1).getStartTime();
            System.out.println("last market data " + Instant.ofEpochMilli(latestSavedElement));
        }
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
            double diffMinutes = ((double) (Instant.now().toEpochMilli() - latestSavedElementTimestamp)) / (1000. * 60.);
            int limit;
            if (diffMinutes > 1. && diffMinutes < MAX_ROWS_LIMIT) {
                limit = (int) Math.ceil(diffMinutes);
            } else if (diffMinutes <= 1.) {
                isInProcess = false;
                continue;
            } else {
                limit = MAX_ROWS_LIMIT;
            }

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

            marketData.addAll(
                    marketKlineResult.getMarketKlineEntries()
                            .stream()
                            .map(MarketDataService::toMarketKlineEntry)
                            .toList());
            System.out.println("market data size " + marketData.size());

            latestSavedElementTimestamp = marketKlineResult.getMarketKlineEntries().get(0).getStartTime();

            if (marketKlineResult.getMarketKlineEntries().size() < MAX_ROWS_LIMIT) {
                marketData.sort(Comparator.comparing(MarketKlineEntry::getStartTime));
                _serialize(marketData);
                isInProcess = false;
            }
        }
    }

    @NotNull
    public static MarketKlineEntry toMarketKlineEntry(@NotNull com.bybit.api.client.domain.market.response.kline.MarketKlineEntry entry) {
        MarketKlineEntry result = new MarketKlineEntry();
        result.setStartTime(entry.getStartTime());
        result.setHighPrice(Double.parseDouble(entry.getHighPrice()));
        result.setLowPrice(Double.parseDouble(entry.getLowPrice()));
        return result;
    }

    private void _serialize(List<MarketKlineEntry> marketData) {
        try {
            try (FileOutputStream fileOutputStream = new FileOutputStream(PATH_RESOURCES + "\\market_data");
                 ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
                objectOutputStream.writeObject(marketData);
                System.out.println("marketData with size: " + marketData.size() + " serialized");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private ArrayList<MarketKlineEntry> _deserialize() {
        try (FileInputStream fileInputStream = new FileInputStream(PATH_RESOURCES + "\\market_data");
             ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            System.out.println("marketData deserialized");
            return (ArrayList<MarketKlineEntry>) objectInputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return new ArrayList<>();
        }
    }

    public ArrayList<MarketKlineEntry> getMarketData() {
        return marketData;
    }
}
