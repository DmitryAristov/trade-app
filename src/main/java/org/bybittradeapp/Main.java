package org.bybittradeapp;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bybittradeapp.domain.Imbalance;
import org.bybittradeapp.domain.MarketKlineEntry;
import org.bybittradeapp.domain.Zone;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.stream.Collectors;


public class Main {
    private static final ObjectMapper mapper = new ObjectMapper();

    // for testing conditions on the whole time period
    private static final boolean TEST_OPTION = true;
    private static final int PERIOD_OF_DAYS_TO_ANALYSE = 60;

    // TODO: implement volatility dependency
    // BTC price minimum diff for imbalance condition
    private static final double BTC_PRICE_THRESHOLD = 4000.0;
    private static final double BTC_SUPPORT_ZONE_SEARCH_WINDOW_PERCENTS = 1.;

    // time intervals imbalance, support and resistance zones search in hours
    private static final int IMBALANCE_SEARCH_TIME_PERIOD = 12;
    private static final int SUPPORT_RESISTANCE_ZONES_SEARCH_TIME_PERIOD = 960;
    // time period of days to upload BTC data

    // candles interval for UI
    private static final MarketInterval interval = MarketInterval.HOURLY;
    private static final int INTERVAL_INT = Integer.parseInt(interval.getIntervalId());

    // market data map { key = timestamp (ms), value = one candle data }
    private static final TreeMap<Long, MarketKlineEntry> marketDataEntriesMap = new TreeMap<>();
    private static long START_TIME = Instant.now().minus(PERIOD_OF_DAYS_TO_ANALYSE, ChronoUnit.DAYS).toEpochMilli();


    private static Imbalance imbalance;
    private static Set<Zone> zones;

    public static void main(String[] args) {
        boolean isInProcess = true;
        while (isInProcess) {
            isInProcess = getMarketDataProcess();
        }

        if (TEST_OPTION) {
            checkMarketConditionsTest();
        } else {
            createCheckTask();
        }
    }

    /**
     * Check if imbalance continues, update the same imbalance and setup progress status
     * if imbalance is finished (2-3 hours if it is not update min/max) - change imbalance status to finished
     */
    private static void checkMarketConditions() {

        imbalance = getImbalance(0);
        zones = getZones(0);



        // after all gets, check strategy
    }

    private static void checkMarketConditionsTest() {
        for (int i = 0; i < marketDataEntriesMap.size(); i++) {
            imbalance = getImbalance(i);
            zones = getZones(i);
        }
    }

    private static Imbalance getImbalance(int fromElement) {
        TreeMap<Long, MarketKlineEntry> checkImbalanceMap =
                marketDataEntriesMap.entrySet().stream()
                        .skip(fromElement)
                        .limit(IMBALANCE_SEARCH_TIME_PERIOD * 60 / INTERVAL_INT)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (oldValue, newValue) -> oldValue,
                                TreeMap::new
                        ));
        // if imbalance != null then check if it is continues and update the same imb, set status progress
        return checkMinMaxThreshold(checkImbalanceMap);
    }

    private static Set<Zone> getZones(int fromElement) {
        TreeMap<Long, MarketKlineEntry> checkSupportResistanceZonesMap = marketDataEntriesMap.entrySet().stream()
                .skip(fromElement)
                .limit(SUPPORT_RESISTANCE_ZONES_SEARCH_TIME_PERIOD * 60 / INTERVAL_INT)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue,
                        TreeMap::new
                ));
        return checkSupportZone(checkSupportResistanceZonesMap);
    }

    /**
     * Check if imbalance is present in provided prices map
     * @return Pair of MIN and MAX market entries which has difference greater then threshold
     */
    private static @Nullable Imbalance checkMinMaxThreshold(@NotNull TreeMap<Long, MarketKlineEntry> map) {
        Optional<MarketKlineEntry> marketKlineEntryMaxPriceOpt = map.values().stream()
                .max(Comparator.comparing(MarketKlineEntry::getHighPrice));
        if (marketKlineEntryMaxPriceOpt.isEmpty()) {
            return null;
        }
        MarketKlineEntry marketKlineEntryMaxPrice = marketKlineEntryMaxPriceOpt.get();
        double maxPrice = marketKlineEntryMaxPrice.getHighPrice();

        Optional<MarketKlineEntry> marketKlineEntryMinPriceOpt = map.values().stream()
                .min(Comparator.comparing(MarketKlineEntry::getLowPrice));
        if (marketKlineEntryMinPriceOpt.isEmpty()) {
            return null;
        }
        MarketKlineEntry marketKlineEntryMinPrice = marketKlineEntryMinPriceOpt.get();
        double minPrice = marketKlineEntryMinPrice.getLowPrice();

        // Check if the price difference exceeds the threshold
        if (maxPrice - minPrice > BTC_PRICE_THRESHOLD) {
            Imbalance imbalance = new Imbalance(marketKlineEntryMinPrice, marketKlineEntryMaxPrice);
            if (marketKlineEntryMinPrice.getStartTime() > marketKlineEntryMaxPrice.getStartTime()) {
                imbalance.setType(Imbalance.Type.DOWN);
            } else {
                imbalance.setType(Imbalance.Type.UP);
            }
            System.out.println(imbalance);
            return imbalance;
        }
        return null;
    }

    /**
     * Serach for support and resistance zones inside provided map
     * @return Set of zones
     */
    private static Set<Zone> checkSupportZone(@NotNull TreeMap<Long, MarketKlineEntry> checkMap) {



        // search for zones independently of imbalance, just all zones on the checkMap
        return null;
    }

    private static boolean getMarketDataProcess() {
        System.out.println(Instant.now() + ":: Fetching Market Data");

        // client
        BybitApiMarketRestClient client = BybitApiClientFactory
                .newInstance(BybitApiConfig.MAINNET_DOMAIN, false)
                .newMarketDataRestClient();

        // request to market data
        MarketDataRequest marketKLineRequest = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol("BTCUSDT")
                .marketInterval(interval)
                .start(START_TIME)
                .limit(1000)
                .build();

        // get response
        var marketKlineResultRaw = client.getMarketLinesData(marketKLineRequest);
        var marketKlineResultGenericResponse = mapper.convertValue(marketKlineResultRaw, GenericResponse.class);
        var marketKlineResult = mapper.convertValue(marketKlineResultGenericResponse.getResult(), MarketKlineResult.class);

        marketDataEntriesMap.putAll(
                marketKlineResult.getMarketKlineEntries()
                        .stream()
                        .collect(Collectors.toMap(
                                com.bybit.api.client.domain.market.response.kline.MarketKlineEntry::getStartTime,
                                Main::toMarketKlineEntry
                        ))
        );

        START_TIME = marketKlineResult.getMarketKlineEntries().get(0).getStartTime();

        System.out.println(Instant.now() + ":: Current Iteration End Time = " + Instant.ofEpochMilli(START_TIME));
        System.out.println(Instant.now() + ":: Current Market Data size = " + marketDataEntriesMap.size());

        if (marketKlineResult.getMarketKlineEntries().size() < 1000) {
            updateDataJson();
            return false;
        }
        return true;
    }

    private static void createCheckTask() {
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                System.out.println(Instant.now() + ":: Executing check market conditions");
                checkMarketConditions();
            }
        };
        timer.scheduleAtFixedRate(task, 0, 30000);
    }

    private static void updateDataJson() {
        System.out.println(Instant.now() + ":: Updating data.json with marketData size = " + marketDataEntriesMap.size());
        Long zoneDelay = 10800000L;

        final ArrayNode ohlcv = mapper.createArrayNode();
        marketDataEntriesMap.forEach((key, value) -> {
            ArrayNode entryNode = mapper.createArrayNode();
            entryNode.add(key + zoneDelay);
            entryNode.add(value.getOpenPrice());
            entryNode.add(value.getHighPrice());
            entryNode.add(value.getLowPrice());
            entryNode.add(value.getClosePrice());
            ohlcv.add(entryNode);
        });

        try {
            File file = new File("C:\\Users\\dimas\\IdeaProjects\\trading-vue-js\\data\\data.json");
            JsonNode rootNode = mapper.readTree(file);

            if (rootNode.has("ohlcv")) {
                ((ObjectNode) rootNode).set("ohlcv", ohlcv);
            }

            mapper.writerWithDefaultPrettyPrinter().writeValue(file, rootNode);

            System.out.println(Instant.now() + ":: JSON file updated successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NotNull
    private static MarketKlineEntry toMarketKlineEntry(@NotNull com.bybit.api.client.domain.market.response.kline.MarketKlineEntry entry) {
        MarketKlineEntry result = new MarketKlineEntry();
        result.setStartTime(entry.getStartTime());
        result.setOpenPrice(Double.parseDouble(entry.getOpenPrice()));
        result.setClosePrice(Double.parseDouble(entry.getClosePrice()));
        result.setHighPrice(Double.parseDouble(entry.getHighPrice()));
        result.setLowPrice(Double.parseDouble(entry.getLowPrice()));
        return result;
    }
}

