package org.tradeapp.ui.utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.tradeapp.analysis.domain.Imbalance;
import org.tradeapp.backtest.domain.OrderType;
import org.tradeapp.backtest.domain.Position;
import org.tradeapp.logging.Log;
import org.tradeapp.marketdata.domain.MarketEntry;
import org.tradeapp.ui.domain.MarketKlineEntry;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.tradeapp.Main.MAPPER;

/**
 * Временный класс только для отображения в UI
 */
public class TradingVueJsonUpdater {

    private final String filePath;


    public TradingVueJsonUpdater(String filePath) {
        this.filePath = filePath;
    }

    private static final Serializer<List<Position>> positionSerializer = new Serializer<>("/src/main/resources/results/positions/");
    private static final Serializer<List<Imbalance>> imbalanceSerializer = new Serializer<>("/src/main/resources/results/imbalances/");

    public void updateUiMarketData(TreeMap<Long, MarketKlineEntry> uiMarketData) {
        Log.debug("ohlcv with:");
        Log.debug(uiMarketData.size() + " uiMarketData");

        final ArrayNode ohlcv = MAPPER.createArrayNode();
        uiMarketData.forEach((key, value) -> {
            ArrayNode entryNode = MAPPER.createArrayNode();
            entryNode.add(value.getOpenTime());
            entryNode.add(value.getOpenPrice());
            entryNode.add(value.getHighPrice());
            entryNode.add(value.getLowPrice());
            entryNode.add(value.getClosePrice());
            entryNode.add(value.getVolume());
            ohlcv.add(entryNode);
        });

        try {
            File file = new File(filePath);
            JsonNode rootNode = MAPPER.readTree(file);

            if (rootNode.has("ohlcv")) {
                ((ObjectNode) rootNode).set("ohlcv", ohlcv);
            }
            if (rootNode.has("onchart")) {
                ((ObjectNode) rootNode).set("onchart", MAPPER.createArrayNode());
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, rootNode);

        } catch (IOException e) {
            Log.debug(e);
        }
    }

    public void updateMarketData(TreeMap<Long, MarketEntry> marketData) {
        var uiMarketData = marketData
                .entrySet()
                .stream()
                .map(entry -> {
                    var uiEntry = new MarketKlineEntry();
                    uiEntry.setOpenTime(entry.getKey());
                    uiEntry.setLowPrice(entry.getValue().low());
                    uiEntry.setHighPrice(entry.getValue().high());
                    uiEntry.setOpenPrice((entry.getValue().low() + entry.getValue().high()) * 0.5);
                    uiEntry.setClosePrice((entry.getValue().low() + entry.getValue().high()) * 0.5);
                    uiEntry.setVolume(entry.getValue().volume());
                    return uiEntry;
                })
                .collect(Collectors.toMap(
                        MarketKlineEntry::getOpenTime,
                        Function.identity(),
                        (first, second) -> first,
                        TreeMap::new
                ));

        updateUiMarketData(uiMarketData);
    }

    public void updateAnalysedData(List<Imbalance> imbalances, List<Position> positions) {
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
            File file = new File(filePath);
            JsonNode rootNode = MAPPER.readTree(file);

            if (rootNode.has("onchart")) {
                ((ObjectNode) rootNode).set("onchart", onchart);
            } else {
                ((ObjectNode) rootNode).putIfAbsent("onchart", onchart);
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, rootNode);

        } catch (IOException e) {
            Log.debug(e);
        }
    }

    private static void parseImbalances(List<Imbalance> imbalances,
                                        ArrayNode onchart) {

        for (Imbalance imbalance : imbalances) {

            /* две вертикальные линии показывающие границы имбаланса по времени */
            {
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
            }

            /* две горизонтальные линии показывающие границы имбаланса по цене + центр */
            {
                ArrayNode p1NodeStart = MAPPER.createArrayNode();
                p1NodeStart.add(imbalance.getStartTime());
                p1NodeStart.add(imbalance.getStartPrice());

                ArrayNode p2NodeStart = MAPPER.createArrayNode();
                p2NodeStart.add(imbalance.getEndTime());
                p2NodeStart.add(imbalance.getStartPrice());

                Settings settingsStart = new Settings();
                settingsStart.p1 = p1NodeStart;
                settingsStart.p2 = p2NodeStart;
                settingsStart.lineWidth = 2;
                settingsStart.color = "#000000";
                settingsStart.legend = true;

                /* подробные данные для отображения в легенде */
                String onchartStartName = String.format("%s speed = %.2f$/minute || price = %.2f$ || time = %ds || computed duration = %.2fms",
                        imbalance.getType(),
                        imbalance.speed() * 60_000L,
                        imbalance.size(),
                        imbalance.duration(),
                        imbalance.getComputedDuration());
                Onchart onchartStart = new Onchart(onchartStartName, "Segment", MAPPER.createArrayNode(), settingsStart);

                ObjectNode segmentNodeStart = MAPPER.valueToTree(onchartStart);
                onchart.add(segmentNodeStart);

                ArrayNode p1NodeCenter = MAPPER.createArrayNode();
                p1NodeCenter.add(imbalance.getStartTime());
                p1NodeCenter.add((imbalance.getStartPrice() + imbalance.getEndPrice()) / 2.);

                ArrayNode p2NodeCenter = MAPPER.createArrayNode();
                p2NodeCenter.add(imbalance.getEndTime());
                p2NodeCenter.add((imbalance.getStartPrice() + imbalance.getEndPrice()) / 2.);

                Settings settingsCenter = new Settings();
                settingsCenter.p1 = p1NodeCenter;
                settingsCenter.p2 = p2NodeCenter;
                settingsCenter.lineWidth = 2;
                settingsCenter.color = "#000000";
                settingsCenter.legend = false;

                Onchart onchartCenter = new Onchart("", "Segment", MAPPER.createArrayNode(), settingsCenter);

                ObjectNode segmentNodeCenter = MAPPER.valueToTree(onchartCenter);
                onchart.add(segmentNodeCenter);

                ArrayNode p1NodeEnd = MAPPER.createArrayNode();
                p1NodeEnd.add(imbalance.getStartTime());
                p1NodeEnd.add(imbalance.getEndPrice());

                ArrayNode p2NodeEnd = MAPPER.createArrayNode();
                p2NodeEnd.add(imbalance.getEndTime());
                p2NodeEnd.add(imbalance.getEndPrice());

                Settings settingsEnd = new Settings();
                settingsEnd.p1 = p1NodeEnd;
                settingsEnd.p2 = p2NodeEnd;
                settingsEnd.lineWidth = 2;
                settingsEnd.color = "#000000";
                settingsEnd.legend = false;

                Onchart onchartEnd = new Onchart("", "Segment", MAPPER.createArrayNode(), settingsEnd);

                ObjectNode segmentNodeEnd = MAPPER.valueToTree(onchartEnd);
                onchart.add(segmentNodeEnd);
            }

            /* две горизонтальные линии показывающие границы имбаланса по цене + центр */
            {
                ArrayNode data = MAPPER.createArrayNode();

                ArrayNode first = MAPPER.createArrayNode();
                first.add(imbalance.getStartTime());
                first.add(0);
                first.add(Double.parseDouble(String.format("%.2f", imbalance.getStartPrice())));
                first.add(String.format("start price = %.2f$",
                        imbalance.getType() == Imbalance.Type.UP ? imbalance.getStartPrice() - imbalance.size() * 0.02 : imbalance.getStartPrice()));
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
