package org.bybittradeapp.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bybittradeapp.domain.Imbalance;
import org.bybittradeapp.domain.MarketKlineEntry;
import org.bybittradeapp.domain.Trend;
import org.bybittradeapp.domain.Zone;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.bybittradeapp.Main.mapper;
import static org.bybittradeapp.service.SupportResistanceService.resistanceZoneColor;
import static org.bybittradeapp.service.SupportResistanceService.supportZoneColor;

public class JsonUtils {
    public static final String TRADING_VUE_PATH = "C:\\Users\\dimas\\IdeaProjects\\trading-vue-js";

    public static void updateMarketDataJson(TreeMap<Long, MarketKlineEntry> uiMarketData) {
        Long zoneDelay = 10800000L;

        final ArrayNode ohlcv = mapper.createArrayNode();
        uiMarketData.forEach((key, value) -> {
            ArrayNode entryNode = mapper.createArrayNode();
            entryNode.add(key + zoneDelay);
            entryNode.add(value.getOpenPrice());
            entryNode.add(value.getHighPrice());
            entryNode.add(value.getLowPrice());
            entryNode.add(value.getClosePrice());
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
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, rootNode);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void updateAnalysedDataJson(List<Zone> zones, Set<Imbalance> imbalances,
                                              Trend trend, TreeMap<Long, MarketKlineEntry> uiMarketData) {
        long zoneDelay = 10800000L;
        final ArrayNode onchart = mapper.createArrayNode();
        long minUiTime = uiMarketData.keySet().stream().min(Comparator.comparing(Long::longValue)).get();

        for (Zone zone : zones) {
            if (minUiTime - zone.getLeft() > 1000 * 60 * 60 * 24) {
                continue;
            }

            ArrayNode p1Node = mapper.createArrayNode();
            p1Node.add(zone.getLeft());
            p1Node.add((zone.getTop() + zone.getBottom()) / 2.);

            ArrayNode p2Node = mapper.createArrayNode();
            p2Node.add(zone.getRight());
            p2Node.add((zone.getTop() + zone.getBottom()) / 2.);
            Settings settings = new Settings(p1Node, p2Node, (int) (zone.getMargin() * 200.), zone.getColor());

            Segment segment = new Segment("zone", "Segment", mapper.createArrayNode(), settings);

            ObjectNode segmentNode = mapper.valueToTree(segment);
            onchart.add(segmentNode);
        }

        for (Imbalance imbalance : imbalances) {
            Optional<Long> timeMin = uiMarketData.keySet().stream()
                    .min(Comparator.comparing(key ->
                            Math.abs(key - imbalance.getMin().getStartTime() + zoneDelay)));
            Optional<Long> timeMax = uiMarketData.keySet().stream()
                    .min(Comparator.comparing(key ->
                            Math.abs(key - imbalance.getMax().getStartTime() + zoneDelay)));

            if (timeMin.isEmpty() || timeMax.isEmpty()) {
                continue;
            }
            ArrayNode p1Node = mapper.createArrayNode();
            p1Node.add(timeMin.get());
            p1Node.add((imbalance.getMax().getHighPrice() + imbalance.getMin().getLowPrice()) / 2.);

            ArrayNode p2Node = mapper.createArrayNode();
            p2Node.add(timeMax.get());
            p2Node.add((imbalance.getMax().getHighPrice() + imbalance.getMin().getLowPrice()) / 2.);

            Settings settings = new Settings(p1Node, p2Node, (int) ((imbalance.getMax().getHighPrice() - imbalance.getMin().getLowPrice()) / 2.),
                    imbalance.getType() == Imbalance.Type.UP ? supportZoneColor : resistanceZoneColor
            );
            Segment segment = new Segment("imb", "Segment", mapper.createArrayNode(), settings);

            ObjectNode segmentNode = mapper.valueToTree(segment);
            onchart.add(segmentNode);
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

    private record Segment (String name, String type, ArrayNode data, Settings settings) {}
    private record Settings (ArrayNode p1, ArrayNode p2, int lineWidth, Color color) {}

    public static String getColorHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static Color getColorFromHex(String hex) {
        hex = hex.startsWith("#") ? hex.substring(1) : hex;

        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);

        return new Color(r, g, b);
    }
}
