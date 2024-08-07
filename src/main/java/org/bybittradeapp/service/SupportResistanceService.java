package org.bybittradeapp.service;

import org.bybittradeapp.domain.MarketKlineEntry;
import org.bybittradeapp.domain.Zone;
import org.bybittradeapp.domain.Box;
import org.bybittradeapp.domain.Line;
import org.bybittradeapp.domain.PivotPoint;
import org.bybittradeapp.utils.BybitService;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import static org.bybittradeapp.Main.mapper;

public class SupportResistanceService {
    public static int detectionLengthAdjustment = 7;
    public static float zoneMargin = 1.5f;

    private final TreeMap<Long, MarketKlineEntry> marketData = BybitService.getDailyMarketData(365);

    public List<Zone> getZones() {
        List<Map.Entry<Long, MarketKlineEntry>> entryList = new ArrayList<>(marketData.entrySet());

        PivotPoint pivotPoint = new PivotPoint();
        List<Zone> resistanceZones = new LinkedList<>(List.of(
                new Zone(
                        new Box(), null, new Line(), false, false, false, false, 0)));
        List<Zone> supportZones = new LinkedList<>(List.of(
                new Zone(
                        new Box(), null, new Line(), false, false, false, false, 0)));

        IntStream.range(0, entryList.size())
                .forEach(i -> {
                    Map.Entry<Long, MarketKlineEntry> entry = entryList.get(i);
                    Long key = entry.getKey();
                    MarketKlineEntry value = entry.getValue();

                    Zone lastResistance;
                    Zone lastSupport;
                    Zone lastResistanceTest;
                    Zone lastSupportTest;
                    int marketState = 0;

                    float highestValue;
                    float lowestValue;
                    if (i >= detectionLengthAdjustment) {
                        highestValue = entryList.subList(i - detectionLengthAdjustment, i).stream()
                                .map(entry_ -> (float) entry_.getValue().getHighPrice())
                                .max(Comparator.comparing(Float::floatValue))
                                .orElse(Float.NaN);
                    } else {
                        highestValue = entryList.stream()
                                .map(entry_ -> (float) entry_.getValue().getHighPrice())
                                .max(Comparator.comparing(Float::floatValue))
                                .orElse(Float.NaN);
                    }

                    if (i >= detectionLengthAdjustment) {
                        lowestValue = entryList.subList(i - detectionLengthAdjustment, i).stream()
                                .map(entry_ -> (float) entry_.getValue().getLowPrice())
                                .min(Comparator.comparing(Float::floatValue))
                                .orElse(Float.NaN);
                    } else {
                        lowestValue = entryList.stream()
                                .map(entry_ -> (float) entry_.getValue().getLowPrice())
                                .min(Comparator.comparing(Float::floatValue))
                                .orElse(Float.NaN);
                    }

                    boolean pivotHigh = false;
                    if (i >= detectionLengthAdjustment && i < entryList.size() - detectionLengthAdjustment) {
                        pivotHigh = entryList.subList(i - detectionLengthAdjustment, i + detectionLengthAdjustment).stream()
                                .anyMatch(entry_ -> entry_.getValue().getHighPrice() > value.getHighPrice());
                    }

                    if (pivotHigh) {
                        pivotPoint.setH1(pivotPoint.getH());
                        pivotPoint.setH((float) value.getHighPrice());
                        pivotPoint.setX1(pivotPoint.getX());
                        pivotPoint.setX(key);
                        pivotPoint.setHx(false);

                        if (resistanceZones.size() > 1) {
                            lastResistance = resistanceZones.get(0);
                            lastResistanceTest = resistanceZones.get(1);

                            if (pivotPoint.getH() < lastResistance.getZone().getBottom() * (1 - lastResistance.getMargin() * 0.17 * zoneMargin) || pivotPoint.getH() > lastResistance.getZone().getTop() * (1 + lastResistance.getMargin() * 0.17 * zoneMargin)) {
                                if (pivotPoint.getX() < lastResistance.getZone().getLeft() && pivotPoint.getX() + detectionLengthAdjustment > lastResistance.getZone().getLeft() && value.getClosePrice() < lastResistance.getZone().getBottom()) {
                                    // no action
                                } else {
                                    if (pivotPoint.getH() < lastResistanceTest.getZone().getBottom() * (1 - lastResistanceTest.getMargin() * 0.17 * zoneMargin) || pivotPoint.getH() > lastResistanceTest.getZone().getTop() * (1 + lastResistanceTest.getMargin() * 0.17)) {
                                        resistanceZones.add(0, new Zone(new Box(pivotPoint.getX(), pivotPoint.getH(), key, (float) (pivotPoint.getH() * (1 - ((highestValue - lowestValue) / highestValue) * 0.17 * zoneMargin)), Color.RED),
                                                new Box(0, 0, 0, 0, Color.BLACK), new Line(pivotPoint.getX(), pivotPoint.getH(), key, pivotPoint.getH(), Color.RED, 2),
                                                false, false, false, false, (highestValue - lowestValue) / highestValue));
                                        lastResistanceTest.setTest(false);
                                    } else {
                                        lastResistanceTest.getZone().setRight(key);
                                        lastResistanceTest.getLine().setX2(key);
                                    }
                                }
                            } else if (lastResistance.getZone().getTop() != lastResistanceTest.getZone().getTop()) {
                                lastResistance.getZone().setRight(key);
                                lastResistance.getLine().setX2(key);
                            }
                        } else {
                            resistanceZones.add(0, new Zone(new Box(pivotPoint.getX(), pivotPoint.getH(), key, (float) (pivotPoint.getH() * (1 - ((highestValue - lowestValue) / highestValue) * 0.17 * zoneMargin)), Color.RED),
                                    new Box(0, 0, 0, 0, Color.BLACK), new Line(pivotPoint.getX(), pivotPoint.getH(), key, pivotPoint.getH(), Color.RED, 2),
                                    false, false, false, false, (highestValue - lowestValue) / highestValue));
                            lastSupport = supportZones.get(0);
                            lastSupport.setTest(false);
                        }
                    }

                    if (value.getClosePrice() > pivotPoint.getH() && !pivotPoint.isHx()) {
                        pivotPoint.setHx(true);
                        marketState = 1;
                    }

                    boolean pivotLow = false;
                    if (i >= detectionLengthAdjustment && i < entryList.size() - detectionLengthAdjustment) {
                        pivotLow = entryList.subList(i - detectionLengthAdjustment, i + detectionLengthAdjustment).stream()
                                .anyMatch(entry_ -> entry_.getValue().getLowPrice() < value.getLowPrice());
                    }

                    if (pivotLow) {
                        pivotPoint.setL1(pivotPoint.getL());
                        pivotPoint.setL((float) value.getLowPrice());
                        pivotPoint.setX1(pivotPoint.getX());
                        pivotPoint.setX(key);
                        pivotPoint.setLx(false);

                        if (supportZones.size() > 2) {
                            lastSupport = supportZones.get(0);
                            lastSupportTest = supportZones.get(1);
                            lastResistanceTest = resistanceZones.get(1);

                            if (pivotPoint.getL() < lastSupport.getZone().getBottom() * (1 - lastSupport.getMargin() * 0.17 * zoneMargin) || pivotPoint.getL() > lastSupport.getZone().getTop() * (1 + lastSupport.getMargin() * 0.17 * zoneMargin)) {
                                if (pivotPoint.getX() < lastSupport.getZone().getLeft() && pivotPoint.getX() + detectionLengthAdjustment > lastSupport.getZone().getLeft() && value.getClosePrice() > lastSupport.getZone().getTop()) {
                                    // no action
                                } else {
                                    if (pivotPoint.getL() < lastSupportTest.getZone().getBottom() * (1 - lastSupportTest.getMargin() * 0.17 * zoneMargin) || pivotPoint.getL() > lastSupportTest.getZone().getTop() * (1 + lastSupportTest.getMargin() * 0.17 * zoneMargin)) {
                                        supportZones.add(0, new Zone(new Box(pivotPoint.getX(), (float) (pivotPoint.getL() * (1 + ((highestValue - lowestValue) / highestValue) * 0.17 * zoneMargin)), key, pivotPoint.getL(), Color.GREEN),
                                                new Box(0, 0, 0, 0, Color.BLACK), new Line(pivotPoint.getX(), pivotPoint.getL(), key, pivotPoint.getL(), Color.GREEN, 2),
                                                false, false, false, false, (highestValue - lowestValue) / highestValue));
                                        lastSupportTest.setTest(false);
                                    } else {
                                        lastSupportTest.getZone().setRight(key);
                                        lastSupportTest.getLine().setX2(key);
                                    }
                                }
                            } else if (lastSupport.getZone().getBottom() != lastResistanceTest.getZone().getBottom()) {
                                lastSupport.getZone().setRight(key);
                                lastSupport.getLine().setX2(key);
                            }
                        } else {
                            supportZones.add(0, new Zone(new Box(pivotPoint.getX(), (float) (pivotPoint.getL() * (1 + ((highestValue - lowestValue) / highestValue) * 0.17 * zoneMargin)), key, pivotPoint.getL(), Color.GREEN),
                                    new Box(0, 0, 0, 0, Color.BLACK), new Line(pivotPoint.getX(), pivotPoint.getL(), key, pivotPoint.getL(), Color.GREEN, 2),
                                    false, false, false, false, (highestValue - lowestValue) / highestValue));
                            lastResistance = resistanceZones.get(0);
                            lastResistance.setTest(false);
                        }
                    }

                    if (value.getClosePrice() < pivotPoint.getL() && !pivotPoint.isLx()) {
                        pivotPoint.setLx(true);
                        marketState = -1;
                    }

                    // Handle Breakouts and Tests
                    if (!resistanceZones.isEmpty()) {
                        lastResistance = resistanceZones.get(0);
                        lastSupport = supportZones.get(0);

                        if (value.getClosePrice() > lastResistance.getZone().getTop() * (1 + lastResistance.getMargin() * 0.17) && !lastResistance.isBreakout()) {
                            lastResistance.getZone().setRight(key);
                            lastResistance.getLine().setX2(key);
                            lastResistance.setBreakout(true);
                            lastResistance.setRetest(false);

                            supportZones.add(0, new Zone(new Box(key, lastResistance.getZone().getTop(), key + 1, lastResistance.getZone().getBottom(), Color.GREEN),
                                    new Box(0, 0, 0, 0, Color.BLACK), new Line(key, lastResistance.getZone().getBottom(), key + 1, lastResistance.getZone().getBottom(), Color.GREEN, 2),
                                    false, false, false, false, lastResistance.getMargin()));
                        } else if (lastSupport.isBreakout() && value.getOpenPrice() < lastResistance.getZone().getTop() && value.getHighPrice() > lastResistance.getZone().getBottom() && value.getClosePrice() < lastResistance.getZone().getBottom() && !lastResistance.isRetest()) {
                            lastResistance.setRetest(true);
                            lastResistance.getZone().setRight(key);
                            lastResistance.getLine().setX2(key);
                        } else if (value.getHighPrice() > lastResistance.getZone().getBottom() && value.getClosePrice() < lastResistance.getZone().getTop() && !lastResistance.isTest() && !lastResistance.isRetest() && !lastResistance.isBreakout() && !lastSupport.isBreakout()) {
                            lastResistance.setTest(true);
                            lastResistance.getZone().setRight(key);
                            lastResistance.getLine().setX2(key);
                        } else if (value.getHighPrice() > lastResistance.getZone().getBottom() * (1 - lastResistance.getMargin() * 0.17) && !lastResistance.isBreakout()) {
                            if (value.getHighPrice() > lastResistance.getZone().getBottom()) {
                                lastResistance.getZone().setRight(key);
                            }
                            lastResistance.getLine().setX2(key);
                        }
                    }

                    if (resistanceZones.size() > 1) {
                        lastResistance = resistanceZones.get(0);
                        lastResistanceTest = resistanceZones.get(1);
                        lastSupportTest = supportZones.get(1);

                        if (lastResistance.getZone().getTop() != lastResistanceTest.getZone().getTop()) {
                            if (value.getClosePrice() > lastResistanceTest.getZone().getTop() * (1 + lastResistanceTest.getMargin() * 0.17) && !lastResistanceTest.isBreakout()) {
                                lastResistanceTest.getZone().setRight(key);
                                lastResistanceTest.getLine().setX2(key);
                                lastResistanceTest.setBreakout(true);
                                lastResistanceTest.setRetest(false);

                                supportZones.add(0, new Zone(new Box(key, lastResistanceTest.getZone().getTop(), key + 1, lastResistanceTest.getZone().getBottom(), Color.GREEN),
                                        new Box(0, 0, 0, 0, Color.BLACK), new Line(key, lastResistanceTest.getZone().getBottom(), key + 1, lastResistanceTest.getZone().getBottom(), Color.GREEN, 2),
                                        false, false, false, false, lastResistanceTest.getMargin()));
                            } else if (lastSupportTest.isBreakout() && value.getOpenPrice() < lastResistanceTest.getZone().getTop() && value.getHighPrice() > lastResistanceTest.getZone().getBottom() && value.getClosePrice() < lastResistanceTest.getZone().getBottom() && !lastResistanceTest.isRetest()) {
                                lastResistanceTest.setRetest(true);
                                lastResistanceTest.getZone().setRight(key);
                                lastResistanceTest.getLine().setX2(key);
                            } else if (value.getHighPrice() > lastResistanceTest.getZone().getBottom() && value.getClosePrice() < lastResistanceTest.getZone().getTop() && !lastResistanceTest.isTest() && !lastResistanceTest.isBreakout() && !lastSupportTest.isBreakout()) {
                                lastResistanceTest.setTest(true);
                                lastResistanceTest.getZone().setRight(key);
                                lastResistanceTest.getLine().setX2(key);
                            } else if (value.getHighPrice() > lastResistanceTest.getZone().getBottom() * (1 - lastResistanceTest.getMargin() * 0.17) && !lastResistanceTest.isBreakout()) {
                                if (value.getHighPrice() > lastResistanceTest.getZone().getBottom()) {
                                    lastResistanceTest.getZone().setRight(key);
                                }
                                lastResistanceTest.getLine().setX2(key);
                            }
                        }
                    }

                    if (!supportZones.isEmpty()) {
                        lastSupport = supportZones.get(0);
                        lastResistance = resistanceZones.get(0);

                        if (value.getClosePrice() < lastSupport.getZone().getBottom() * (1 - lastSupport.getMargin() * 0.17) && !lastSupport.isBreakout()) {
                            lastSupport.getZone().setRight(key);
                            lastSupport.getLine().setX2(key);
                            lastSupport.setBreakout(true);
                            lastSupport.setRetest(false);

                            resistanceZones.add(0, new Zone(new Box(key, lastSupport.getZone().getTop(), key + 1, lastSupport.getZone().getBottom(), Color.RED),
                                    new Box(0, 0, 0, 0, Color.BLACK), new Line(key, lastSupport.getZone().getTop(), key + 1, lastSupport.getZone().getTop(), Color.RED, 2),
                                    false, false, false, false, lastSupport.getMargin()));
                        } else if (lastResistance.isBreakout() && value.getOpenPrice() > lastSupport.getZone().getBottom() && value.getLowPrice() < lastSupport.getZone().getTop() && value.getClosePrice() > lastSupport.getZone().getTop() && !lastSupport.isRetest()) {
                            lastSupport.setRetest(true);
                            lastSupport.getZone().setRight(key);
                            lastSupport.getLine().setX2(key);
                        } else if (value.getLowPrice() < lastSupport.getZone().getTop() && value.getClosePrice() > lastSupport.getZone().getBottom() && !lastSupport.isTest() && !lastSupport.isBreakout() && !lastResistance.isBreakout()) {
                            lastSupport.setTest(true);
                            lastSupport.getZone().setRight(key);
                            lastSupport.getLine().setX2(key);
                        } else if (value.getLowPrice() < lastSupport.getZone().getTop() * (1 + lastSupport.getMargin() * 0.17) && !lastSupport.isBreakout()) {
                            if (value.getLowPrice() < lastSupport.getZone().getTop()) {
                                lastSupport.getZone().setRight(key);
                            }
                            lastSupport.getLine().setX2(key);
                        }
                    }

                    if (supportZones.size() > 2) {
                        lastSupport = supportZones.get(0);
                        lastSupportTest = supportZones.get(1);
                        lastResistanceTest = resistanceZones.get(1);

                        if (lastSupport.getZone().getBottom() != lastSupportTest.getZone().getBottom()) {
                            if (value.getClosePrice() < lastSupportTest.getZone().getBottom() * (1 - lastSupportTest.getMargin() * 0.17) && !lastSupportTest.isBreakout()) {
                                lastSupportTest.getZone().setRight(key);
                                lastSupportTest.getLine().setX2(key);
                                lastSupportTest.setBreakout(true);
                                lastSupportTest.setRetest(false);

                                resistanceZones.add(0, new Zone(new Box(key, lastSupportTest.getZone().getTop(), key + 1, lastSupportTest.getZone().getBottom(), Color.RED),
                                        new Box(0, 0, 0, 0, Color.BLACK), new Line(key, lastSupportTest.getZone().getTop(), key + 1, lastSupportTest.getZone().getTop(), Color.RED, 2),
                                        false, false, false, false, lastSupportTest.getMargin()));
                            } else if (lastResistanceTest.isBreakout() && value.getOpenPrice() > lastSupportTest.getZone().getBottom() && value.getLowPrice() < lastSupportTest.getZone().getTop() && value.getClosePrice() > lastSupportTest.getZone().getTop() && !lastSupportTest.isRetest()) {
                                lastSupportTest.setRetest(true);
                                lastSupportTest.getZone().setRight(key);
                                lastSupportTest.getLine().setX2(key);
                            } else if (value.getLowPrice() < lastSupportTest.getZone().getTop() && value.getClosePrice() > lastSupportTest.getZone().getBottom() && !lastSupportTest.isTest() && !lastSupportTest.isBreakout() && !lastResistanceTest.isBreakout()) {
                                lastSupportTest.setTest(true);
                                lastSupportTest.getZone().setRight(key);
                                lastSupportTest.getLine().setX2(key);
                            } else if (value.getLowPrice() < lastSupportTest.getZone().getTop() * (1 + lastSupportTest.getMargin() * 0.17) && !lastSupportTest.isBreakout()) {
                                if (value.getLowPrice() < lastSupportTest.getZone().getTop()) {
                                    lastSupportTest.getZone().setRight(key);
                                }
                                lastSupportTest.getLine().setX2(key);
                            }
                        }
                    }

                });

        List<Zone> result = new ArrayList<>();
        result.addAll(supportZones.stream().filter(zone -> zone.getZone() != null).toList());
        result.addAll(resistanceZones.stream().filter(zone -> zone.getZone() != null).toList());

        try {
            System.out.println(mapper.writeValueAsString(result));
        } catch (Exception ignored) {  }
        return result;
    }
}
