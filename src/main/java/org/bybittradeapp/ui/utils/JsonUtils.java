package org.bybittradeapp.ui.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.backtest.domain.OrderType;
import org.bybittradeapp.backtest.domain.Position;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketEntry;
import org.bybittradeapp.ui.domain.MarketKlineEntry;
import org.bybittradeapp.analysis.domain.Extremum;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.bybittradeapp.Main.MAPPER;

/**
 * Временный класс только для отображения в UI
 */
public class JsonUtils {
    public static final String DATA_JSON_FILE_PATH = "/home/dmitriy/Projects/trading-vue-js/data/data.json";

    private static final Serializer<List<Position>> positionSerializer = new Serializer<>("/src/main/resources/results/positions/");
    private static final Serializer<List<Imbalance>> imbalanceSerializer = new Serializer<>("/src/main/resources/results/imbalances/");
    private static final Serializer<List<Extremum>> extremumSerializer = new Serializer<>("/src/main/resources/results/extremums/");

    public static void updateUiMarketData(TreeMap<Long, MarketKlineEntry> uiMarketData) {
        Log.debug("ohlcv with:");
        Log.debug(uiMarketData.size() + " uiMarketData");

        final ArrayNode ohlcv = MAPPER.createArrayNode();
        uiMarketData.forEach((key, value) -> {
            ArrayNode entryNode = MAPPER.createArrayNode();
            entryNode.add(value.getStartTime());
            entryNode.add(value.getOpenPrice());
            entryNode.add(value.getHighPrice());
            entryNode.add(value.getLowPrice());
            entryNode.add(value.getClosePrice());
            ohlcv.add(entryNode);
        });

        try {
            File file = new File(DATA_JSON_FILE_PATH);
            JsonNode rootNode = MAPPER.readTree(file);

            if (rootNode.has("ohlcv")) {
                ((ObjectNode) rootNode).set("ohlcv", ohlcv);
            }
            if (rootNode.has("onchart")) {
                ((ObjectNode) rootNode).set("onchart", MAPPER.createArrayNode());
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, rootNode);

        } catch (IOException e) {
            Log.debug("updateMarketData throws exception: " +
                    e.getMessage() + "\n" +
                    Arrays.toString(e.getStackTrace()));
            throw new RuntimeException(e);
        }
    }

    public static void updateMarketData(TreeMap<Long, MarketEntry> marketData) {
        var secondsMarketKLineEntries = getUiDataFromMarketEntries(marketData);
        JsonUtils.updateUiMarketData(secondsMarketKLineEntries);
    }

    @NotNull
    private static TreeMap<Long, MarketKlineEntry> getUiDataFromMarketEntries(TreeMap<Long, MarketEntry> marketData) {
        return marketData
                .entrySet()
                .stream()
                .map(entry -> {
                    var uiEntry = new MarketKlineEntry();
                    uiEntry.setStartTime(entry.getKey());
                    uiEntry.setLowPrice(entry.getValue().low());
                    uiEntry.setHighPrice(entry.getValue().high());
                    uiEntry.setOpenPrice((entry.getValue().low() + entry.getValue().high()) * 0.5);
                    uiEntry.setClosePrice((entry.getValue().low() + entry.getValue().high()) * 0.5);
                    return uiEntry;
                })
                .collect(Collectors.toMap(
                        MarketKlineEntry::getStartTime,
                        Function.identity(),
                        (first, second) -> first,
                        TreeMap::new
                ));
    }

    public static void updateAnalysedData(List<Extremum> extrema,
                                            List<Imbalance> imbalances,
                                            List<Position> positions,
                                            TreeMap<Long, MarketEntry> marketData) {
        updateAnalysedUiData(extrema, imbalances, positions, getUiDataFromMarketEntries(marketData));
    }

    public static void updateAnalysedUiData(List<Extremum> extrema,
                                            List<Imbalance> imbalances,
                                            List<Position> positions,
                                            TreeMap<Long, MarketKlineEntry> uiMarketData) {
        Log.debug("onchart with:");
        Log.debug(extrema.size() + " zones");
        Log.debug(imbalances.size() + " imbalances");
        Log.debug(positions.size() + " positions");

        final ArrayNode onchart = MAPPER.createArrayNode();

        if (uiMarketData.isEmpty()) {
            return;
        }
        if (!imbalances.isEmpty()) {
            parseImbalances(imbalances, uiMarketData, onchart);
        }

        if (!positions.isEmpty()) {
            parsePositions(positions, uiMarketData, onchart);
        }

        if (!extrema.isEmpty()) {
            parseExtremums(extrema, uiMarketData, onchart);
        }

        try {
            File file = new File(DATA_JSON_FILE_PATH);
            JsonNode rootNode = MAPPER.readTree(file);

            if (rootNode.has("onchart")) {
                ((ObjectNode) rootNode).set("onchart", onchart);
            } else {
                ((ObjectNode) rootNode).putIfAbsent("onchart", onchart);
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, rootNode);

        } catch (IOException e) {
            Log.debug("updateAnalysedUiData throws exception: " +
                    e.getMessage() + "\n" +
                    Arrays.toString(e.getStackTrace()));
            throw new RuntimeException(e);
        }
    }

    private static void parseImbalances(List<Imbalance> imbalances,
                                        TreeMap<Long, MarketKlineEntry> uiMarketData,
                                        ArrayNode onchart) {

        for (Imbalance imbalance : imbalances) {
            Optional<Long> startTime = uiMarketData.values().stream()
                    .map(MarketKlineEntry::getStartTime)
                    .filter(startTime_ -> startTime_ < imbalance.getStartTime())
                    .max(Comparator.comparing(Long::longValue));

            Optional<Long> endTime = uiMarketData.values().stream()
                    .map(MarketKlineEntry::getStartTime)
                    .filter(endTime_ -> endTime_ > imbalance.getEndTime())
                    .min(Comparator.comparing(Long::longValue));

            Optional<Long> completeTime = uiMarketData.values().stream()
                    .map(MarketKlineEntry::getStartTime)
                    .filter(completeTime_ -> completeTime_ > imbalance.getCompleteTime())
                    .min(Comparator.comparing(Long::longValue));

            if (startTime.isEmpty() || endTime.isEmpty() || completeTime.isEmpty()) {
                continue;
            }

            String color = imbalance.getType() == Imbalance.Type.UP ? "#002d9e" : "#b8077d";

            ArrayNode p1NodeUp = MAPPER.createArrayNode();
            p1NodeUp.add(startTime.get());
            p1NodeUp.add(imbalance.getStartPrice());

            ArrayNode p2NodeUp = MAPPER.createArrayNode();
            p2NodeUp.add(endTime.get());
            p2NodeUp.add(imbalance.getStartPrice());

            Settings settingsUp = new Settings(p1NodeUp, p2NodeUp, 5, color);
            Segment segmentUp = new Segment("", "Segment", MAPPER.createArrayNode(), settingsUp);

            ObjectNode segmentNodeUp = MAPPER.valueToTree(segmentUp);
            onchart.add(segmentNodeUp);

            ArrayNode p1NodeStart = MAPPER.createArrayNode();
            p1NodeStart.add(startTime.get());
            p1NodeStart.add(imbalance.getEndPrice());

            ArrayNode p2NodeEnd = MAPPER.createArrayNode();
            p2NodeEnd.add(endTime.get());
            p2NodeEnd.add(imbalance.getEndPrice());

            Settings settingsDown = new Settings(p1NodeStart, p2NodeEnd, 10, color);
            Segment segmentDown = new Segment("", "Segment", MAPPER.createArrayNode(), settingsDown);

            ObjectNode segmentNodeDown = MAPPER.valueToTree(segmentDown);
            onchart.add(segmentNodeDown);

            ArrayNode p1NodeComplete = MAPPER.createArrayNode();
            p1NodeComplete.add(endTime.get());
            p1NodeComplete.add(imbalance.getCompletePrice());

            ArrayNode p2NodeComplete = MAPPER.createArrayNode();
            p2NodeComplete.add(completeTime.get());
            p2NodeComplete.add(imbalance.getCompletePrice());

            Settings settingsComplete = new Settings(p1NodeComplete, p2NodeComplete, 10, color);
            Segment segmentComplete = new Segment("completed", "Segment", MAPPER.createArrayNode(), settingsComplete);

            ObjectNode segmentNodeComplete = MAPPER.valueToTree(segmentComplete);
            onchart.add(segmentNodeComplete);
        }
    }

    private static void parsePositions(List<Position> positions,
                                       TreeMap<Long, MarketKlineEntry> uiMarketData,
                                       ArrayNode onchart) {

        for (Position position : positions) {
            Optional<Long> timeLeft = uiMarketData.values().stream()
                    .map(MarketKlineEntry::getStartTime)
                    .filter(startTime -> startTime < position.getOpenTime())
                    .max(Comparator.comparing(Long::longValue));

            Optional<Long> timeRight = uiMarketData.values().stream()
                    .map(MarketKlineEntry::getStartTime)
                    .filter(startTime -> startTime > position.getCloseTime())
                    .min(Comparator.comparing(Long::longValue));
            if (timeRight.isEmpty() || timeLeft.isEmpty()) {
                continue;
            }

            String openColor = position.getOrder().getType() == OrderType.LONG ? "#21ba02" : "#db0602";
            String closeColor = position.getOrder().getType() == OrderType.LONG ? "#0f5202" : "#590301";

            ArrayNode p1NodeUp = MAPPER.createArrayNode();
            p1NodeUp.add(timeLeft.get());
            p1NodeUp.add(position.getOpenPrice());

            ArrayNode p2NodeUp = MAPPER.createArrayNode();
            p2NodeUp.add(timeRight.get());
            p2NodeUp.add(position.getOpenPrice());

            Settings settingsUp = new Settings(p1NodeUp, p2NodeUp, 5, openColor);
            Segment segmentUp = new Segment("pos", "Segment", MAPPER.createArrayNode(), settingsUp);

            ObjectNode segmentNodeUp = MAPPER.valueToTree(segmentUp);
            onchart.add(segmentNodeUp);

            ArrayNode p1NodeDown = MAPPER.createArrayNode();
            p1NodeDown.add(timeLeft.get());
            p1NodeDown.add(position.getClosePrice());

            ArrayNode p2NodeDown = MAPPER.createArrayNode();
            p2NodeDown.add(timeRight.get());
            p2NodeDown.add(position.getClosePrice());

            Settings settingsDown = new Settings(p1NodeDown, p2NodeDown, 5, closeColor);
            Segment segmentDown = new Segment("pos", "Segment", MAPPER.createArrayNode(), settingsDown);

            ObjectNode segmentNodeDown = MAPPER.valueToTree(segmentDown);
            onchart.add(segmentNodeDown);
        }
    }

    private static void parseExtremums(List<Extremum> extrema,
                                       TreeMap<Long, MarketKlineEntry> uiMarketData,
                                       ArrayNode onchart) {

        for (Extremum extremum : extrema) {

            MarketKlineEntry time = uiMarketData.values().stream()
                    .min(Comparator.comparing(entry -> Math.abs(entry.getStartTime() - extremum.getTimestamp())))
                    .orElse(null);

            if (time == null || uiMarketData.values().stream().noneMatch(data -> data.getStartTime() == time.getStartTime() + 8L * 60L * 60L * 1000L)) {
                continue;
            }

            String color = extremum.getType() == Extremum.Type.MAX ? "#23a776" : "#e54150";

            ArrayNode p1NodeUp = MAPPER.createArrayNode();
            p1NodeUp.add(time.getStartTime());
            p1NodeUp.add(extremum.getPrice());

            ArrayNode p2NodeUp = MAPPER.createArrayNode();
            p2NodeUp.add(time.getStartTime() + 8L * 60L * 60L * 1000L);
            p2NodeUp.add(extremum.getPrice());

            Settings settingsUp = new Settings(p1NodeUp, p2NodeUp, 5, color);
            Segment segmentUp = new Segment("zone", "Segment", MAPPER.createArrayNode(), settingsUp);

            ObjectNode segmentNodeUp = MAPPER.valueToTree(segmentUp);
            onchart.add(segmentNodeUp);
        }
    }

    public static void serializeAll(List<Extremum> extremums, List<Imbalance> imbalances, List<Position> positions) {
        if (!extremums.isEmpty()) {
            extremumSerializer.serialize(extremums);
        }

        if (!imbalances.isEmpty()) {
            imbalanceSerializer.serialize(imbalances);
        }

        if (!positions.isEmpty()) {
            positionSerializer.serialize(positions);
        }
    }

    @SuppressWarnings("unchecked")
    public static<T> List<T> deserialize(Class<T> clazz) {
        if (clazz == Imbalance.class) {
            return (List<T>) imbalanceSerializer.deserialize();
        } else if (clazz == Position.class) {
            return (List<T>) positionSerializer.deserialize();
        } else {
            return (List<T>) extremumSerializer.deserialize();
        }
    }

    private record Segment(String name, String type, ArrayNode data, Settings settings) { }

    private record Settings(ArrayNode p1, ArrayNode p2, int lineWidth, String color) { }
}
