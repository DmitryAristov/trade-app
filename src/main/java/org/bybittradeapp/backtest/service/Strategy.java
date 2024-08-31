package org.bybittradeapp.backtest.service;

import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.analysis.service.ExtremumService;
import org.bybittradeapp.analysis.service.ImbalanceService;
import org.bybittradeapp.analysis.service.TrendService;
import org.bybittradeapp.backtest.domain.Order;
import org.bybittradeapp.backtest.domain.Position;
import org.bybittradeapp.marketData.domain.MarketKlineEntry;

import java.util.List;
import java.util.stream.Collectors;

public class Strategy implements Tickle {
    /**
     * Стоп лосс цена - в процентах от размера имбаланса
     * Пример:
     *   тип имбаланса = вверх
     *   нижняя цена = 10000$
     *   верхняя цена = 15000$
     *   тип позиции = SHORT
     *   TP = 15000 - (15000 - 10000) * 0.3 = 13500$
     *   SL = 10000 + (15000 - 10000) * 0.1 = 10500$
     */
    private static final double STOP_LOSS_THRESHOLD = 10.0;
    /**
     * Тейк профит цена - в процентах от размера имбаланса
     */
    private static final double TAKE_PROFIT_THRESHOLD = 30.0;
    /**
     * Сколько времени после окончания имбаланса пытаться открыть позицию. 60 минут = 60 секунд = 3600000 мс.
     */
    private static final long AFTER_IMBALANCE_COMPLETE_TIME_TO_OPEN_POSITION = 4200000L;
    /**
     * Время жизни лимитного ордера. После экспирации ордер будет отменен. 10 минут = 600 секунд = 600000 мс.
     */
    private static final long MAX_LIMIT_ORDER_LIVE_TIME = 600000L;
    private static final long MAX_POSITION_LIVE_TIME = 15 * 24 * 60 * 60 * 1000;

    private final ExchangeSimulator simulator;
    private final List<MarketKlineEntry> marketData;
    private final ImbalanceService imbalanceService;
    private final ExtremumService extremumService;
    private final TrendService trendService;

    public Strategy(ExchangeSimulator simulator,
                    List<MarketKlineEntry> marketData,
                    ImbalanceService imbalanceService,
                    ExtremumService extremumService,
                    TrendService trendService) {
        this.simulator = simulator;
        this.marketData = marketData;
        this.imbalanceService = imbalanceService;
        this.extremumService = extremumService;
        this.trendService = trendService;
    }

    @Override
    public void onTick(MarketKlineEntry currentEntry) {

        List<Order> openLimitOrders = simulator.getLimitOrders();
        if (!openLimitOrders.isEmpty()) {
            /*
             * Обрабатываем неисполненные лимитные ордера
             */
            openLimitOrders.stream()
                    .filter(order -> currentEntry.getStartTime() - order.getCreateTime() > MAX_LIMIT_ORDER_LIVE_TIME)
                    .forEach(Order::cancel);
            return;
        }

        List<Position> openPositions = simulator.getOpenPositions();
        if (!openPositions.isEmpty()) {
            /*
             * Обрабатываем открытые позиции
             */

            openPositions.forEach(position -> {
                correctTP_SL(position, currentEntry);
                closeByTimeout(position, currentEntry);
            });
            return;
        }

        var imbalance_ = imbalanceService.getImbalance(currentEntry);
        if (imbalance_.isEmpty()) {
            /*
             * Нет имбаланса - пропускаем тик
             */
            return;
        }

        Imbalance imbalance = imbalance_.get();
        if (imbalance.getStatus() == Imbalance.Status.PROGRESS) {
            /*
             * Имбаланс еще не закончился - пропускаем тик
             */
            return;
        }

        long imbalanceCompleteTime = switch (imbalance.getType()) {
            case UP -> imbalance.getMax().getStartTime();
            case DOWN -> imbalance.getMin().getStartTime();
        };

        /*
         * Если текущее время минус время окончания имбаланса меньше чем 60 минут -> можно открывать позицию в обратную сторону
         */
        if (currentEntry.getStartTime() - imbalanceCompleteTime < AFTER_IMBALANCE_COMPLETE_TIME_TO_OPEN_POSITION) {
            //TODO
            // Перед открытием позиции нужно проверить зоны поддержки и сопротивления.
            // Обычно имбаланс заканчивается на этих зонах (например поддержки) и потом доходит обратно
            // к ближайщей или следующей противоположной (сопротивления) зоне
//            var extremums = extremumService.getExtremums();

            switch (imbalance.getType()) {

                // Если имбаланс был вверх -> открываем Short (на понижение цены)
                case UP -> {
                    Order marketOrder = new Order(Order.OrderType.SHORT, Order.ExecutionType.MARKET);
                    marketOrder.setPrice(currentEntry.getPrice());

                    double tp = currentEntry.getPrice()
                            - TAKE_PROFIT_THRESHOLD * (imbalance.getMax().getHighPrice() - imbalance.getMin().getLowPrice()) / 100;
                    double sl = imbalance.getMax().getHighPrice()
                            + STOP_LOSS_THRESHOLD * (imbalance.getMax().getHighPrice() - imbalance.getMin().getLowPrice()) / 100;

                    // Если текущая цена рыночного ордера больше чем расчетный стоп лосс то не открывать позицию
                    // (для SHORT позиции стоп находится выше чем цена открытия)
                    if (sl <= 0 || sl < currentEntry.getPrice()) {
                        return;
                    }
                    // Если текущая цена рыночного ордера меньше чем расчетный тейк профит то не открывать позицию
                    // (для SHORT позиции тейк находится ниже чем цена открытия)
                    if (tp <= 0 || tp > currentEntry.getPrice()) {
                        return;
                    }
                    marketOrder.setTP_SL(tp, sl);
                    marketOrder.setCreateTime(currentEntry.getStartTime());
                    System.out.println("Imbalance for order: " + imbalance);
                    simulator.submitOrder(marketOrder);
                }

                // Если имбаланс был вниз -> открываем Long (на повышение цены)
                case DOWN -> {
                    Order marketOrder = new Order(Order.OrderType.LONG, Order.ExecutionType.MARKET);
                    marketOrder.setPrice(currentEntry.getPrice());

                    //TODO открывать позицию рядом с окончанием имбаланса. Уменьшить время подтверждения имбаланса (?)
                    double tp = currentEntry.getPrice()
                            + TAKE_PROFIT_THRESHOLD * (imbalance.getMax().getHighPrice() - imbalance.getMin().getLowPrice()) / 100;
                    double sl = imbalance.getMin().getLowPrice()
                            - STOP_LOSS_THRESHOLD * (imbalance.getMax().getHighPrice() - imbalance.getMin().getLowPrice()) / 100;

                    // Если текущая цена рыночного ордера меньше чем расчетный стоп лосс то не открывать позицию
                    // (для LONG позиции стоп находится ниже чем цена открытия)
                    if (sl <= 0 || sl > currentEntry.getPrice()) {
                        return;
                    }
                    // Если текущая цена рыночного ордера больше чем расчетный тейк профит то не открывать позицию
                    // (для LONG позиции тейк находится выше чем цена открытия)
                    if (tp <= 0 || tp < currentEntry.getPrice()) {
                        return;
                    }
                    marketOrder.setTP_SL(tp, sl);
                    marketOrder.setCreateTime(currentEntry.getStartTime());
                    System.out.println("Imbalance for order: " + imbalance);
                    simulator.submitOrder(marketOrder);
                }
            }
        }
    }

    /**
     * Корректируем тейк профит и стоп лосс открытой позиции в зависимости от текущего курса валюты.
     */
    private void correctTP_SL(Position position, MarketKlineEntry entry) {
        //TODO
        var lastNEntries = marketData.stream().collect(Collectors.toList());

    }

    /**
     * Если позиция слишком долго не закрывается, закрыть принудительно
     */
    private void closeByTimeout(Position position, MarketKlineEntry entry) {
        if (entry.getStartTime() - position.getOrder().getCreateTime() > MAX_POSITION_LIVE_TIME) {
            System.out.println("Position " + position.toString() + " is closed by timeout with TP&SL: " + position.getProfitLoss());
            position.close(entry.getPrice());
        }
    }
}
