package org.bybittradeapp.ui.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.backtest.domain.OrderType;
import org.bybittradeapp.backtest.domain.Position;
import org.bybittradeapp.logging.Log;
import org.bybittradeapp.marketdata.domain.MarketKlineEntry;
import org.bybittradeapp.analysis.domain.Extremum;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import static org.bybittradeapp.Main.mapper;
import static org.bybittradeapp.marketdata.service.MarketDataService.PATH_RESOURCES;

/**
 * Temporary utility class for UI purposes
 */
public class JsonUtils {
    public static final String TRADING_VUE_PATH = "/home/dmitriy/Projects/trading-vue-js";
    public static final long ZONE_DELAY = 10800000L;

    public static void updateMarketData(List<MarketKlineEntry> uiMarketData) {
        Log.log("ohlcv with:");
        Log.log(uiMarketData.size() + " uiMarketData");

        final ArrayNode ohlcv = mapper.createArrayNode();
        uiMarketData.forEach(entry -> {
            ArrayNode entryNode = mapper.createArrayNode();
            entryNode.add(entry.getStartTime() + ZONE_DELAY);
            entryNode.add(entry.getOpenPrice());
            entryNode.add(entry.getHighPrice());
            entryNode.add(entry.getLowPrice());
            entryNode.add(entry.getClosePrice());
            ohlcv.add(entryNode);
        });

        try {
            File file = new File(TRADING_VUE_PATH + "/data/data.json");
            JsonNode rootNode = mapper.readTree(file);

            if (rootNode.has("ohlcv")) {
                ((ObjectNode) rootNode).set("ohlcv", ohlcv);
            } else {
                ((ObjectNode) rootNode).putIfAbsent("ohlcv", ohlcv);
            }
            if (rootNode.has("onchart")) {
                ((ObjectNode) rootNode).set("onchart", mapper.createArrayNode());
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, rootNode);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void updateAnalysedData(List<Extremum> extrema,
                                          List<Imbalance> imbalances,
                                          List<Position> positions,
                                          List<MarketKlineEntry> uiMarketData) {
        Log.log("onchart with:");
        Log.log(extrema.size() + " zones");
        Log.log(imbalances.size() + " imbalances");
        Log.log(positions.size() + " positions");

        final ArrayNode onchart = mapper.createArrayNode();

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
            File file = new File(TRADING_VUE_PATH + "/data/data.json");
            JsonNode rootNode = mapper.readTree(file);

            if (rootNode.has("onchart")) {
                ((ObjectNode) rootNode).set("onchart", onchart);
            } else {
                ((ObjectNode) rootNode).putIfAbsent("onchart", onchart);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, rootNode);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void parseImbalances(List<Imbalance> imbalances,
                                        List<MarketKlineEntry> uiMarketData,
                                        ArrayNode onchart) {

        for (Imbalance imbalance : imbalances) {
            Optional<Long> timeLeft = uiMarketData.stream()
                    .map(MarketKlineEntry::getStartTime)
                    .filter(startTime -> startTime < imbalance.getStartTime() + ZONE_DELAY)
                    .max(Comparator.comparing(Long::longValue));

            Optional<Long> timeRight = uiMarketData.stream()
                    .map(MarketKlineEntry::getStartTime)
                    .filter(startTime -> startTime > imbalance.getEndTime() + ZONE_DELAY)
                    .min(Comparator.comparing(Long::longValue));
            if (timeRight.isEmpty() || timeLeft.isEmpty()) {
                return;
            }

            String color = imbalance.getType() == Imbalance.Type.UP ? "#002d9e" : "#b8077d";

            ArrayNode p1NodeUp = mapper.createArrayNode();
            p1NodeUp.add(timeLeft.get());
            p1NodeUp.add(imbalance.getStartPrice());

            ArrayNode p2NodeUp = mapper.createArrayNode();
            p2NodeUp.add(timeRight.get());
            p2NodeUp.add(imbalance.getStartPrice());

            Settings settingsUp = new Settings(p1NodeUp, p2NodeUp, 15, color);
            Segment segmentUp = new Segment("imb", "Segment", mapper.createArrayNode(), settingsUp);

            ObjectNode segmentNodeUp = mapper.valueToTree(segmentUp);
            onchart.add(segmentNodeUp);

            ArrayNode p1NodeDown = mapper.createArrayNode();
            p1NodeDown.add(timeLeft.get());
            p1NodeDown.add(imbalance.getEndPrice());

            ArrayNode p2NodeDown = mapper.createArrayNode();
            p2NodeDown.add(timeRight.get());
            p2NodeDown.add(imbalance.getEndPrice());

            Settings settingsDown = new Settings(p1NodeDown, p2NodeDown, 15, color);
            Segment segmentDown = new Segment("imb", "Segment", mapper.createArrayNode(), settingsDown);

            ObjectNode segmentNodeDown = mapper.valueToTree(segmentDown);
            onchart.add(segmentNodeDown);
        }
    }

    private static void parsePositions(List<Position> positions,
                                       List<MarketKlineEntry> uiMarketData,
                                       ArrayNode onchart) {

        for (Position position : positions) {
            Optional<Long> timeLeft = uiMarketData.stream()
                    .map(MarketKlineEntry::getStartTime)
                    .filter(startTime -> startTime < position.getCreateTime() + ZONE_DELAY)
                    .max(Comparator.comparing(Long::longValue));

            Optional<Long> timeRight = uiMarketData.stream()
                    .map(MarketKlineEntry::getStartTime)
                    .filter(startTime -> startTime > position.getCloseTime() + ZONE_DELAY)
                    .min(Comparator.comparing(Long::longValue));
            if (timeRight.isEmpty() || timeLeft.isEmpty()) {
                return;
            }

            String openColor = position.getOrder().getType() == OrderType.LONG ? "#21ba02" : "#db0602";
            String closeColor = position.getOrder().getType() == OrderType.LONG ? "#0f5202" : "#590301";

            ArrayNode p1NodeUp = mapper.createArrayNode();
            p1NodeUp.add(timeLeft.get());
            p1NodeUp.add(position.getOpenPrice());

            ArrayNode p2NodeUp = mapper.createArrayNode();
            p2NodeUp.add(timeLeft.get() + 30L * 60L * 1000L);
            p2NodeUp.add(position.getOpenPrice());

            Settings settingsUp = new Settings(p1NodeUp, p2NodeUp, 15, openColor);
            Segment segmentUp = new Segment("pos", "Segment", mapper.createArrayNode(), settingsUp);

            ObjectNode segmentNodeUp = mapper.valueToTree(segmentUp);
            onchart.add(segmentNodeUp);

            ArrayNode p1NodeDown = mapper.createArrayNode();
            p1NodeDown.add(timeRight.get());
            p1NodeDown.add(position.getClosePrice());

            ArrayNode p2NodeDown = mapper.createArrayNode();
            p2NodeDown.add(timeRight.get() + 30L * 60L * 1000L);
            p2NodeDown.add(position.getClosePrice());

            Settings settingsDown = new Settings(p1NodeDown, p2NodeDown, 15, closeColor);
            Segment segmentDown = new Segment("pos", "Segment", mapper.createArrayNode(), settingsDown);

            ObjectNode segmentNodeDown = mapper.valueToTree(segmentDown);
            onchart.add(segmentNodeDown);
        }
    }

    private static void parseExtremums(List<Extremum> extrema,
                                       List<MarketKlineEntry> uiMarketData,
                                       ArrayNode onchart) {

        for (Extremum extremum : extrema) {

            MarketKlineEntry time = uiMarketData.stream()
                    .min(Comparator.comparing(entry -> Math.abs(entry.getStartTime() - extremum.getTimestamp() + ZONE_DELAY)))
                    .orElse(null);

            if (time == null || uiMarketData.stream().noneMatch(data -> data.getStartTime() == time.getStartTime() + 8L * 60L * 60L * 1000L)) {
                continue;
            }

            String color = extremum.getType() == Extremum.Type.MAX ? "#23a776" : "#e54150";

            ArrayNode p1NodeUp = mapper.createArrayNode();
            p1NodeUp.add(time.getStartTime());
            p1NodeUp.add(extremum.getPrice());

            ArrayNode p2NodeUp = mapper.createArrayNode();
            p2NodeUp.add(time.getStartTime() + 8L * 60L * 60L * 1000L);
            p2NodeUp.add(extremum.getPrice());

            Settings settingsUp = new Settings(p1NodeUp, p2NodeUp, 5, color);
            Segment segmentUp = new Segment("zone", "Segment", mapper.createArrayNode(), settingsUp);

            ObjectNode segmentNodeUp = mapper.valueToTree(segmentUp);
            onchart.add(segmentNodeUp);
        }
    }

    public static void serializeAll(List<Extremum> extremums, List<Imbalance> imbalances, List<Position> positions) {
        if (!extremums.isEmpty()) {
            serialize(extremums, "extremums");
        }

        if (!imbalances.isEmpty()) {
            serialize(imbalances, "imbalances");
        }

        if (!positions.isEmpty()) {
            serialize(positions, "positions");
        }
    }

    private static <T> void serialize(List<T> object, String fileName) {
        try {
            try (FileOutputStream fileOutputStream = new FileOutputStream(PATH_RESOURCES + "/" + fileName);
                 ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)
            ) {
                Log.log("serializing " + fileName);
                objectOutputStream.writeObject(object);
                Log.log(fileName + " [" + object.size() + "] serialized");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> deserialize(String fileName) {
        try (FileInputStream fileInputStream = new FileInputStream(PATH_RESOURCES + "/" + fileName);
             ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)
        ) {
            Log.log("deserializing " + fileName);
            List<T> result = (List<T>) objectInputStream.readObject();
            Log.log(fileName + " [" + result.size() + "] deserialized");
            return result;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private record Segment(String name, String type, ArrayNode data, Settings settings) {
    }

    private record Settings(ArrayNode p1, ArrayNode p2, int lineWidth, String color) {
    }
}
