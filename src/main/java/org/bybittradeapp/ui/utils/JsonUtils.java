package org.bybittradeapp.ui.utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
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
import java.io.Serializable;
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

    public static void updateMarketData(TreeMap<Long, MarketEntry> marketData) {
        var uiMarketData = getUiDataFromMarketEntries(marketData);

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

    public static void updateAnalysedData(List<Imbalance> imbalances, List<Position> positions) {
        Log.debug("onchart with:");
        Log.debug(imbalances.size() + " imbalances");
        Log.debug(positions.size() + " positions");

        final ArrayNode onchart = MAPPER.createArrayNode();

        if (!imbalances.isEmpty()) {
            parseImbalances(imbalances, onchart);
        }

        if (!positions.isEmpty()) {
            parsePositions(positions, onchart);
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
                                        ArrayNode onchart) {

        for (Imbalance imbalance : imbalances) {

            /* две вертикальные линии показывающие границы имбаланса */
            ArrayNode data = MAPPER.createArrayNode();
            ArrayNode start = MAPPER.createArrayNode();
            start.add(imbalance.getStartTime());
            start.add("Imbalance");
            start.add(0);
            start.add("#080469");
            start.add(0.05);

            ArrayNode end = MAPPER.createArrayNode();
            end.add(imbalance.getEndTime());
            end.add("Imbalance");
            end.add(1);
            end.add("#080469");
            end.add(0.05);

            data.add(start);
            data.add(end);

            Settings settingsSplitters = new Settings();
            settingsSplitters.lineWidth = 2;
            settingsSplitters.lineColor = "#080469";
            settingsSplitters.legend = false;

            Onchart splitter = new Onchart("", "Splitters", data, settingsSplitters);

            ObjectNode onchartSplitter = MAPPER.valueToTree(splitter);
            onchart.add(onchartSplitter);


            /* две надписи - одна вначале имбаланса с его начальной ценой, вторая в конце */
            ArrayNode p1NodeStart = MAPPER.createArrayNode();
            p1NodeStart.add(imbalance.getStartTime() - 1000);
            p1NodeStart.add(imbalance.getStartPrice());

            ArrayNode p2NodeStart = MAPPER.createArrayNode();
            p2NodeStart.add(imbalance.getStartTime());
            p2NodeStart.add(imbalance.getStartPrice());

            Settings settingsStart = new Settings();
            settingsStart.p1 = p1NodeStart;
            settingsStart.p2 = p2NodeStart;
            settingsStart.lineWidth = 5;
            settingsStart.color = "#000000";
            settingsStart.legend = true;

            /* подробные данные для отображения в легенде */
            String onchartStartName = String.format("%s speed = %.2f$/minute || price = %.2f$ || time = %ds || computed duration = %.2fms",
                    imbalance.getType(),
                    imbalance.speed() * 60_000L,
                    imbalance.size(),
                    imbalance.timeSize(),
                    imbalance.getComputedDuration());
            Onchart onchartStart = new Onchart(onchartStartName, "Segment", MAPPER.createArrayNode(), settingsStart);

            ObjectNode segmentNodeStart = MAPPER.valueToTree(onchartStart);
            onchart.add(segmentNodeStart);

            data = MAPPER.createArrayNode();

            ArrayNode first = MAPPER.createArrayNode();
            first.add(imbalance.getStartTime());
            first.add(0);
            first.add(Double.parseDouble(String.format("%.2f", imbalance.getStartPrice())));
            first.add(String.format("start price = %.2f$",
                    imbalance.getType() == Imbalance.Type.UP ? imbalance.getStartPrice() : imbalance.getStartPrice() - imbalance.size() * 0.02));
            data.add(first);

            ArrayNode second = MAPPER.createArrayNode();
            second.add(imbalance.getEndTime());
            second.add(0);
            second.add(Double.parseDouble(String.format("%.2f", imbalance.getEndPrice())));
            second.add(String.format("end price = %.2f$",
                    imbalance.getType() == Imbalance.Type.UP ? imbalance.getEndPrice() : imbalance.getEndPrice() - imbalance.size() * 0.02));
            data.add(second);

            Settings settingsPriceLabels = new Settings();
            settingsPriceLabels.labelColor = "#000000";
            settingsPriceLabels.markerSize = 1;
            settingsPriceLabels.legend = false;

            Onchart onchartPriceLabels = new Onchart("", "Trades", data, settingsPriceLabels);

            ObjectNode segmentNodePriceLabels = MAPPER.valueToTree(onchartPriceLabels);
            onchart.add(segmentNodePriceLabels);
        }
    }

    private static void parsePositions(List<Position> positions, ArrayNode onchart) {

        for (Position position : positions) {
            ArrayNode data = MAPPER.createArrayNode();

            ArrayNode open = MAPPER.createArrayNode();
            open.add(position.getOpenTime());
            open.add(position.getOrder().getType() == OrderType.LONG ? 1 : 0);
            open.add(Double.parseDouble(String.format("%.2f", position.getOpenPrice())));
            open.add(String.format("OPEN with price %.2f$", position.getOpenPrice()));
            data.add(open);

            ArrayNode close = MAPPER.createArrayNode();
            close.add(position.getCloseTime());
            close.add(position.getOrder().getType() == OrderType.LONG ? 1 : 0);
            close.add(Double.parseDouble(String.format("%.2f", position.getClosePrice())));
            String profit = position.getProfitLoss() < 0 ? "loss" : "profit";
            close.add(String.format("CLOSE with %s %.2f$", profit, position.getProfitLoss()));
            data.add(close);

            Settings settingsOpen = new Settings();
            settingsOpen.labelColor = "#000000";
            settingsOpen.markerSize = 8;
            settingsOpen.legend = false;

            Onchart onchartOpen = new Onchart("Trade", "Trades", data, settingsOpen);
            ObjectNode segmentNodeOpen = MAPPER.valueToTree(onchartOpen);
            onchart.add(segmentNodeOpen);
        }

        double summaryProfitLoss = positions.stream().map(Position::getProfitLoss).reduce(0., Double::sum);
        String onchartName = String.format("Summary profit loss = %.2f$", summaryProfitLoss);
        Settings settingsProfitLoss = new Settings();
        settingsProfitLoss.markerSize = 1;
        settingsProfitLoss.legend = true;
        Onchart onchartProfitLoss = new Onchart(onchartName, "Segment", MAPPER.createArrayNode(), settingsProfitLoss);
        ObjectNode segmentNodeProfitLoss = MAPPER.valueToTree(onchartProfitLoss);
        onchart.add(segmentNodeProfitLoss);
    }

    public static void serializeAll(List<Imbalance> imbalances, List<Position> positions) {
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
            return new ArrayList<>();
        }
    }

    private record Onchart(String name, String type, ArrayNode data, Settings settings) { }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class Settings {
        public Settings() { }
        ArrayNode p1, p2;
        Integer lineWidth, markerSize;
        String color, lineColor, labelColor;
        Boolean legend;
    }
}
