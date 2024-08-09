package org.bybittradeapp.service;

import org.bybittradeapp.domain.MarketKlineEntry;
import org.bybittradeapp.domain.Zone;
import org.bybittradeapp.domain.PivotPoint;
import org.bybittradeapp.utils.BybitService;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import static org.bybittradeapp.Main.uiService;
import static org.bybittradeapp.utils.JsonUtils.getColorFromHex;

public class SupportResistanceService {
    private static final long DAILY_TIME_INTERVAL_MS = 86400000;

    public static int detectionLengthAdjustment = 8;
    public static float zoneMargin = 1f;
    public static final Color supportZoneColor = getColorFromHex("#ef535042");
    public static final Color resistanceZoneColor = getColorFromHex("#79999e42");

    private final TreeMap<Long, MarketKlineEntry> marketData = BybitService.getDailyMarketData(400);

    public List<Zone> getZones() {
        List<Map.Entry<Long, MarketKlineEntry>> entryList = new ArrayList<>(marketData.entrySet());

        PivotPoint pivotPoint = new PivotPoint();
        List<Zone> resistanceZones = new LinkedList<>(List.of(new Zone(Zone.Type.RESISTANCE)));
        List<Zone> supportZones = new LinkedList<>(List.of(new Zone(Zone.Type.SUPPORT)));

        IntStream.range(1, entryList.size())
                .forEach(i -> {
                    Map.Entry<Long, MarketKlineEntry> entry = entryList.get(i);
                    Long key = entry.getKey();
                    MarketKlineEntry value = entry.getValue();
                    Map.Entry<Long, MarketKlineEntry> previousEntry = entryList.get(i - 1);
                    Long previousKey = previousEntry.getKey();
                    MarketKlineEntry previousValue = previousEntry.getValue();

                    Zone lastResistance;
                    Zone lastSupport;
                    Zone lastResistanceTest;
                    Zone lastSupportTest;
                    int marketState = 0;

                    float highestValue = Float.MIN_VALUE;
                    float lowestValue = Float.MAX_VALUE;

                    for (int j = Math.max(i - detectionLengthAdjustment, 0); j <= Math.min(i + detectionLengthAdjustment, entryList.size() - 1); j++) {
                        float highPrice = (float) entryList.get(j).getValue().getHighPrice();
                        float lowPrice = (float) entryList.get(j).getValue().getLowPrice();
                        if (highPrice > highestValue) highestValue = highPrice;
                        if (lowPrice < lowestValue) lowestValue = lowPrice;
                    }
                    boolean isPivotHigh = true;
                    if (i >= detectionLengthAdjustment && i < entryList.size() - detectionLengthAdjustment) {
                        for (int j = i - detectionLengthAdjustment; j <= i + detectionLengthAdjustment; j++) {
                            if (j != i && entryList.get(j).getValue().getHighPrice() >= value.getHighPrice()) {
                                isPivotHigh = false;
                                break;
                            }
                        }
                    } else {
                        isPivotHigh = false;
                    }

                    if (isPivotHigh) {
                        pivotPoint.setH1(pivotPoint.getH());
                        pivotPoint.setH((float) value.getHighPrice());
                        pivotPoint.setX1(pivotPoint.getX());
                        pivotPoint.setX(entryList.get(i - detectionLengthAdjustment).getKey());
                        pivotPoint.setHx(false);

                        lastSupport = supportZones.get(supportZones.size() - 1);
                        if (resistanceZones.size() > 1) {
                            lastResistance = resistanceZones.get(resistanceZones.size() - 1);
                            lastResistanceTest = resistanceZones.get(resistanceZones.size() - 2);

                            if (pivotPoint.getH() < lastResistance.getBottom() * (1 - lastResistance.getMargin() * .17 * zoneMargin) || pivotPoint.getH() > lastResistance.getTop() * (1 + lastResistance.getMargin() * .17 * zoneMargin)) {
                                if (pivotPoint.getX() < lastResistance.getLeft() && pivotPoint.getX() + detectionLengthAdjustment > lastResistance.getLeft() && value.getClosePrice() < lastResistance.getBottom()) {
                                    // no action
                                } else {
                                    if (pivotPoint.getH() < lastResistanceTest.getBottom() * (1 - lastResistanceTest.getMargin() * .17 * zoneMargin) || pivotPoint.getH() > lastResistanceTest.getTop() * (1 + lastResistanceTest.getMargin() * .17 * zoneMargin)) {
                                        resistanceZones.add(new Zone(pivotPoint.getX(), pivotPoint.getH(), key, (float) (pivotPoint.getH() * (1 - ((highestValue - lowestValue) / highestValue) * .17 * zoneMargin)), resistanceZoneColor,
                                                false, false, false, false, (highestValue - lowestValue) / highestValue, Zone.Type.RESISTANCE));
                                        lastSupport.setTest(false);
                                    } else {
                                        lastResistanceTest.setRight(key);
                                    }
                                }
                            } else if (lastResistance.getTop() != lastResistanceTest.getTop()) {
                                lastResistance.setRight(key);
                            }
                        } else {
                            resistanceZones.add(new Zone(pivotPoint.getX(), pivotPoint.getH(), key, (float) (pivotPoint.getH() * (1. - ((highestValue - lowestValue) / highestValue) * .17 * zoneMargin)), resistanceZoneColor,
                                    false, false, false, false, (highestValue - lowestValue) / highestValue, Zone.Type.RESISTANCE));
                            lastSupport.setTest(false);
                        }
                    }

                    if (previousValue.getClosePrice() > pivotPoint.getH() && !pivotPoint.isHx()) {
                        pivotPoint.setHx(true);
                        marketState = 1;
                    }

                    boolean isPivotLow = true;
                    if (i >= detectionLengthAdjustment && i < entryList.size() - detectionLengthAdjustment) {
                        for (int j = i - detectionLengthAdjustment; j <= i + detectionLengthAdjustment; j++) {
                            if (j != i && entryList.get(j).getValue().getLowPrice() <= value.getLowPrice()) {
                                isPivotLow = false;
                                break;
                            }
                        }
                    } else {
                        isPivotLow = false;
                    }

                    if (isPivotLow) {
                        pivotPoint.setL1(pivotPoint.getL());
                        pivotPoint.setL((float) value.getLowPrice());
                        pivotPoint.setX1(pivotPoint.getX());
                        pivotPoint.setX(entryList.get(i - detectionLengthAdjustment).getKey());
                        pivotPoint.setLx(false);

                        lastResistance = resistanceZones.get(resistanceZones.size() - 1);
                        if (supportZones.size() > 2) {
                            lastSupport = supportZones.get(supportZones.size() - 1);
                            lastSupportTest = supportZones.get(supportZones.size() - 2);

                            if (pivotPoint.getL() < lastSupport.getBottom() * (1 - lastSupport.getMargin() * .17 * zoneMargin) || pivotPoint.getL() > lastSupport.getTop() * (1 + lastSupport.getMargin() * .17 * zoneMargin)) {
                                if (pivotPoint.getX() < lastSupport.getLeft() && pivotPoint.getX() + detectionLengthAdjustment > lastSupport.getLeft() && value.getClosePrice() > lastSupport.getTop()) {
                                    // no action
                                } else {
                                    if (pivotPoint.getL() < lastSupportTest.getBottom() * (1 - lastSupportTest.getMargin() * .17 * zoneMargin) || pivotPoint.getL() > lastSupportTest.getTop() * (1 + lastSupportTest.getMargin() * .17 * zoneMargin)) {
                                        supportZones.add(new Zone(pivotPoint.getX(), (float) (pivotPoint.getL() * (1 + ((highestValue - lowestValue) / highestValue) * .17 * zoneMargin)), key, pivotPoint.getL(), supportZoneColor,
                                                false, false, false, false, (highestValue - lowestValue) / highestValue, Zone.Type.SUPPORT));
                                        lastResistance.setTest(false);
                                    } else {
                                        lastSupportTest.setRight(key);
                                    }
                                }
                            } else if (lastSupport.getBottom() != lastResistance.getBottom()) {
                                lastSupport.setRight(key);
                            }
                        } else {
                            supportZones.add(new Zone(pivotPoint.getX(), (float) (pivotPoint.getL() * (1 + ((highestValue - lowestValue) / highestValue) * .17 * zoneMargin)), key, pivotPoint.getL(), supportZoneColor,
                                    false, false, false, false, (highestValue - lowestValue) / highestValue, Zone.Type.SUPPORT));
                            lastResistance.setTest(false);
                        }
                    }

                    if (previousValue.getClosePrice() < pivotPoint.getL() && !pivotPoint.isLx()) {
                        pivotPoint.setLx(true);
                        marketState = -1;
                    }

                    // Handle Breakouts and Tests
                    if (!resistanceZones.isEmpty()) {
                        lastResistance = resistanceZones.get(resistanceZones.size() - 1);
                        lastSupport = supportZones.get(supportZones.size() - 1);

                        if (previousValue.getClosePrice() > lastResistance.getTop() * (1 + lastResistance.getMargin() * .17) &&
                                !lastResistance.isBreakout()
                        ) {
                            lastResistance.setRight(previousKey);
                            lastResistance.setBreakout(true);
                            lastResistance.setRetest(false);

                            supportZones.add(new Zone(previousKey, lastResistance.getTop(), key + DAILY_TIME_INTERVAL_MS, lastResistance.getBottom(), supportZoneColor,
                                    false, false, false, false, lastResistance.getMargin(), Zone.Type.SUPPORT));
                        } else if (lastSupport.isBreakout() &&
                                previousValue.getOpenPrice() < lastResistance.getTop() &&
                                previousValue.getHighPrice() > lastResistance.getBottom() &&
                                previousValue.getClosePrice() < lastResistance.getBottom() &&
                                !lastResistance.isRetest() &&
                                previousKey != lastResistance.getLeft()
                        ) {
                            lastResistance.setRetest(true);
                            lastResistance.setRight(key);
                        } else if (previousValue.getHighPrice() > lastResistance.getBottom() &&
                                previousValue.getClosePrice() < lastResistance.getTop() &&
                                value.getClosePrice() < lastResistance.getTop() &&
                                !lastResistance.isTest() &&
                                !lastResistance.isRetest() &&
                                !lastResistance.isBreakout() &&
                                !lastSupport.isBreakout() &&
                                previousKey != lastResistance.getLeft()
                        ) {
                            lastResistance.setTest(true);
                            lastResistance.setRight(key);
                        } else if (value.getHighPrice() > lastResistance.getBottom() * (1 - lastResistance.getMargin() * .17) &&
                                !lastResistance.isBreakout()
                        ) {
                            if (value.getHighPrice() > lastResistance.getBottom()) {
                                lastResistance.setRight(key);
                            }
                        }
                    }

                    if (resistanceZones.size() > 1) {
                        lastResistance = resistanceZones.get(resistanceZones.size() - 1);
                        lastResistanceTest = resistanceZones.get(resistanceZones.size() - 2);
                        lastSupportTest = supportZones.get(supportZones.size() - 2);

                        if (lastResistance.getTop() != lastResistanceTest.getTop()) {
                            if (previousValue.getClosePrice() > lastResistanceTest.getTop() * (1 + lastResistanceTest.getMargin() * .17) &&
                                    !lastResistanceTest.isBreakout()
                            ) {
                                lastResistanceTest.setRight(previousKey);
                                lastResistanceTest.setBreakout(true);
                                lastResistanceTest.setRetest(false);

                                supportZones.add(new Zone(previousKey, lastResistanceTest.getTop(), key + DAILY_TIME_INTERVAL_MS, lastResistanceTest.getBottom(), supportZoneColor,
                                        false, false, false, false, lastResistanceTest.getMargin(), Zone.Type.SUPPORT));
                            } else if (lastSupportTest.isBreakout() &&
                                    previousValue.getOpenPrice() < lastResistanceTest.getTop() &&
                                    previousValue.getHighPrice() > lastResistanceTest.getBottom() &&
                                    previousValue.getClosePrice() < lastResistanceTest.getBottom() &&
                                    !lastResistanceTest.isRetest() &&
                                    previousKey != lastResistanceTest.getLeft()
                            ) {
                                lastResistanceTest.setRetest(true);
                                lastResistanceTest.setRight(key);
                            } else if (previousValue.getHighPrice() > lastResistanceTest.getBottom() &&
                                    previousValue.getClosePrice() < lastResistanceTest.getTop() &&
                                    !lastResistanceTest.isTest() &&
                                    !lastResistanceTest.isBreakout() &&
                                    !lastSupportTest.isBreakout() &&
                                    previousKey != lastResistanceTest.getLeft()
                            ) {
                                lastResistanceTest.setTest(true);
                                lastResistanceTest.setRight(key);
                            } else if (value.getHighPrice() > lastResistanceTest.getBottom() * (1 - lastResistanceTest.getMargin() * .17) &&
                                    !lastResistanceTest.isBreakout()
                            ) {
                                if (value.getHighPrice() > lastResistanceTest.getBottom()) {
                                    lastResistanceTest.setRight(key);
                                }
                            }
                        }
                    }

                    if (!supportZones.isEmpty()) {
                        lastSupport = supportZones.get(supportZones.size() - 1);
                        lastResistance = resistanceZones.get(resistanceZones.size() - 1);

                        if (previousValue.getClosePrice() < lastSupport.getBottom() * (1 - lastSupport.getMargin() * .17) &&
                                !lastSupport.isBreakout()
                        ) {
                            lastSupport.setRight(previousKey);
                            lastSupport.setBreakout(true);
                            lastSupport.setRetest(false);

                            resistanceZones.add(new Zone(previousKey, lastSupport.getTop(), key + DAILY_TIME_INTERVAL_MS, lastSupport.getBottom(), resistanceZoneColor,
                                    false, false, false, false, lastSupport.getMargin(), Zone.Type.RESISTANCE));
                        } else if (lastResistance.isBreakout() &&
                                previousValue.getOpenPrice() > lastSupport.getBottom() &&
                                previousValue.getLowPrice() < lastSupport.getTop() &&
                                previousValue.getClosePrice() > lastSupport.getTop() &&
                                !lastSupport.isRetest() &&
                                previousKey != lastSupport.getLeft()
                        ) {
                            lastSupport.setRetest(true);
                            lastSupport.setRight(key);
                        } else if (previousValue.getLowPrice() < lastSupport.getTop() &&
                                previousValue.getClosePrice() > lastSupport.getBottom() &&
                                !lastSupport.isTest() &&
                                !lastSupport.isBreakout() &&
                                !lastResistance.isBreakout() &&
                                previousKey != lastSupport.getLeft()
                        ) {
                            lastSupport.setTest(true);
                            lastSupport.setRight(key);
                        } else if (value.getLowPrice() < lastSupport.getTop() * (1 + lastSupport.getMargin() * .17) &&
                                !lastSupport.isBreakout()
                        ) {
                            if (value.getLowPrice() < lastSupport.getTop()) {
                                lastSupport.setRight(key);
                            }
                        }
                    }

                    if (supportZones.size() > 2) {
                        lastSupport = supportZones.get(supportZones.size() - 1);
                        lastSupportTest = supportZones.get(supportZones.size() - 2);
                        if (resistanceZones.size() > 1) {
                            lastResistanceTest = resistanceZones.get(resistanceZones.size() - 2);
                        } else {
                            lastResistanceTest = new Zone(Zone.Type.RESISTANCE);
                        }

                        if (lastSupport.getBottom() != lastSupportTest.getBottom()) {
                            if (previousValue.getClosePrice() < lastSupportTest.getBottom() * (1 - lastSupportTest.getMargin() * .17) &&
                                    !lastSupportTest.isBreakout()
                            ) {
                                lastSupportTest.setRight(previousKey);
                                lastSupportTest.setBreakout(true);
                                lastSupportTest.setRetest(false);

                                resistanceZones.add(new Zone(previousKey, lastSupportTest.getTop(), key + DAILY_TIME_INTERVAL_MS, lastSupportTest.getBottom(), resistanceZoneColor,
                                        false, false, false, false, lastSupportTest.getMargin(), Zone.Type.RESISTANCE));
                            } else if (lastResistanceTest.isBreakout() &&
                                    previousValue.getOpenPrice() > lastSupportTest.getBottom() &&
                                    previousValue.getLowPrice() < lastSupportTest.getTop() &&
                                    previousValue.getClosePrice() > lastSupportTest.getTop() &&
                                    !lastSupportTest.isRetest() &&
                                    previousKey != lastSupportTest.getLeft()
                            ) {
                                lastSupportTest.setRetest(true);
                                lastSupportTest.setRight(key);
                            } else if (previousValue.getLowPrice() < lastSupportTest.getTop() &&
                                    previousValue.getClosePrice() > lastSupportTest.getBottom() &&
                                    !lastSupportTest.isTest() &&
                                    !lastSupportTest.isBreakout() &&
                                    !lastResistanceTest.isBreakout() &&
                                    previousKey != lastSupportTest.getLeft()
                            ) {
                                lastSupportTest.setTest(true);
                                lastSupportTest.setRight(key);
                            } else if (value.getLowPrice() < lastSupportTest.getTop() * (1 + lastSupportTest.getMargin() * .17) &&
                                    !lastSupportTest.isBreakout()
                            ) {
                                if (value.getLowPrice() < lastSupportTest.getTop()) {
                                    lastSupportTest.setRight(key);
                                }
                            }
                        }
                    }

                });

        List<Zone> result = new ArrayList<>();
        result.addAll(supportZones);
        result.addAll(resistanceZones);


        uiService.updateAnalysedDataJson(result, StrategyService.imbalances, StrategyService.trend);
        return result;
    }
}
