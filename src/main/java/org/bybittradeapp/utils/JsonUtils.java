package org.bybittradeapp.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bybittradeapp.domain.Imbalance;
import org.bybittradeapp.domain.MarketKlineEntry;
import org.bybittradeapp.domain.Zone;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import static org.bybittradeapp.Main.mapper;

public class JsonUtils {
    public static final String TRADING_VUE_PATH = "C:\\Users\\dimas\\IdeaProjects\\trading-vue-js";

    public static void updateMarketDataJson(List<MarketKlineEntry> uiMarketData) {
        long zoneDelay = 10800000L;
        System.out.println("ohlcv with " + uiMarketData.size() + " uiMarketData");

        final ArrayNode ohlcv = mapper.createArrayNode();
        uiMarketData.forEach(entry -> {
            ArrayNode entryNode = mapper.createArrayNode();
            entryNode.add(entry.getStartTime() + zoneDelay);
            entryNode.add(entry.getOpenPrice());
            entryNode.add(entry.getHighPrice());
            entryNode.add(entry.getLowPrice());
            entryNode.add(entry.getClosePrice());
            ohlcv.add(entryNode);
        });

        try {
            File file = new File(TRADING_VUE_PATH + "\\data\\data.json");
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

    public static void updateAnalysedDataJson(List<Zone> zones,
                                              List<Imbalance> imbalances,
                                              List<MarketKlineEntry> uiMarketData) {
        long zoneDelay = 10800000L;
        System.out.println("onchart with " + zones.size() + " zones and " + imbalances.size() + " imbalances");
        final ArrayNode onchart = mapper.createArrayNode();

        for (Imbalance imbalance : imbalances) {

            Long timeLeft;
            Long timeRight;
            if (imbalance.getType() == Imbalance.Type.UP) {
                timeLeft = uiMarketData.stream()
                        .map(MarketKlineEntry::getStartTime)
                        .filter(startTime -> startTime < imbalance.getMin().getStartTime() + zoneDelay)
                        .max(Comparator.comparing(Long::longValue))
                        .orElse(null);

                timeRight = uiMarketData.stream()
                        .map(MarketKlineEntry::getStartTime)
                        .filter(startTime -> startTime > imbalance.getMax().getStartTime() + zoneDelay)
                        .min(Comparator.comparing(Long::longValue))
                        .orElse(null);
            } else {
                timeLeft = uiMarketData.stream()
                        .map(MarketKlineEntry::getStartTime)
                        .filter(startTime -> startTime < imbalance.getMax().getStartTime() + zoneDelay)
                        .max(Comparator.comparing(Long::longValue))
                        .orElse(null);

                timeRight = uiMarketData.stream()
                        .map(MarketKlineEntry::getStartTime)
                        .filter(startTime -> startTime > imbalance.getMin().getStartTime() + zoneDelay)
                        .min(Comparator.comparing(Long::longValue))
                        .orElse(null);
            }

            if (timeLeft == null || timeRight == null) {
                continue;
            }

            String color = imbalance.getType() == Imbalance.Type.UP ? "#79999e42" : "#ef535042";

            ArrayNode p1NodeUp = mapper.createArrayNode();
            p1NodeUp.add(timeLeft);
            p1NodeUp.add(imbalance.getMax().getHighPrice() + 250.);

            ArrayNode p2NodeUp = mapper.createArrayNode();
            p2NodeUp.add(timeRight);
            p2NodeUp.add(imbalance.getMax().getHighPrice() + 250.);

            Settings settingsUp = new Settings(p1NodeUp, p2NodeUp, 15, color);
            Segment segmentUp = new Segment("imb", "Segment", mapper.createArrayNode(), settingsUp);

            ObjectNode segmentNodeUp = mapper.valueToTree(segmentUp);
            onchart.add(segmentNodeUp);

            ArrayNode p1NodeDown = mapper.createArrayNode();
            p1NodeDown.add(timeLeft);
            p1NodeDown.add(imbalance.getMin().getLowPrice() - 250.);

            ArrayNode p2NodeDown = mapper.createArrayNode();
            p2NodeDown.add(timeRight);
            p2NodeDown.add(imbalance.getMin().getLowPrice() - 250.);

            Settings settingsDown = new Settings(p1NodeDown, p2NodeDown, 15, color);
            Segment segmentDown = new Segment("imb", "Segment", mapper.createArrayNode(), settingsDown);

            ObjectNode segmentNodeDown = mapper.valueToTree(segmentDown);
            onchart.add(segmentNodeDown);
        }

        for (Zone zone : zones) {

            MarketKlineEntry time = uiMarketData.stream()
                        .min(Comparator.comparing(entry -> Math.abs(entry.getStartTime() - zone.getTimestamp() + zoneDelay)))
                        .orElse(null);

            if (time == null || uiMarketData.stream().noneMatch(data -> data.getStartTime() == time.getStartTime() + 4 * 60 * 60 * 1000)) {
                continue;
            }

            String color = zone.getType() == Zone.Type.SUPPORT ? "#79999e42" : "#ef535042";

            ArrayNode p1NodeUp = mapper.createArrayNode();
            p1NodeUp.add(time.getStartTime());
            p1NodeUp.add(zone.getPrice());

            ArrayNode p2NodeUp = mapper.createArrayNode();
            p2NodeUp.add(time.getStartTime() + 4 * 60 * 60 * 1000);
            p2NodeUp.add(zone.getPrice());

            Settings settingsUp = new Settings(p1NodeUp, p2NodeUp, 5, color);
            Segment segmentUp = new Segment("zone", "Segment", mapper.createArrayNode(), settingsUp);

            ObjectNode segmentNodeUp = mapper.valueToTree(segmentUp);
            onchart.add(segmentNodeUp);
        }

        try {
            File file = new File(TRADING_VUE_PATH + "\\data\\data.json");
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

    private record Segment(String name, String type, ArrayNode data, Settings settings) {
    }

    private record Settings(ArrayNode p1, ArrayNode p2, int lineWidth, String color) {
    }
}
