package org.bybittradeapp.analysis.service;

import org.bybittradeapp.analysis.domain.Imbalance;
import org.bybittradeapp.marketData.domain.MarketKlineEntry;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.bybittradeapp.Main.*;

public class ImbalanceService {

    private final List<MarketKlineEntry> marketData;
    private final ArrayList<Imbalance> imbalances = new ArrayList<>();
    private final double priceThreshold;

    public ImbalanceService(List<MarketKlineEntry> marketData, VolatilityService volatilityService) {
        this.marketData = marketData;
        this.priceThreshold = volatilityService.getVolatility() * IMBALANCE_PRICE_MODIFIER;
    }

    /**
     * Для проверки есть ли сейчас имбаланс (за последние N тиков от entry)
     */
    public Optional<Imbalance> getImbalance(MarketKlineEntry entry) {
        checkImbalance(marketData.stream()
                .filter(entry_ -> entry_.getStartTime() <= entry.getStartTime() &&
                        entry_.getStartTime() > entry.getStartTime() - IMBALANCE_SEARCH_TIME_WINDOW * 60 * 1000)
                .toList());
        if (!imbalances.isEmpty()) {
            return Optional.of(imbalances.get(imbalances.size() - 1));
        } else return Optional.empty();
    }


    /**
     * Для отображения всех имбалансов в UI
     */
    public void getImbalance(int fromElement) {
        if (marketData.size() < fromElement + IMBALANCE_SEARCH_TIME_WINDOW) {
            return;
        }
        List<MarketKlineEntry> marketData_ = marketData
                .subList(fromElement, fromElement + IMBALANCE_SEARCH_TIME_WINDOW);
        checkImbalance(marketData_);

    }

    private void checkImbalance(@NotNull List<MarketKlineEntry> marketData) {
        MarketKlineEntry maxPrice = marketData.stream()
                .max(Comparator.comparing(MarketKlineEntry::getHighPrice))
                .orElse(null);

        MarketKlineEntry minPrice = marketData.stream()
                .min(Comparator.comparing(MarketKlineEntry::getLowPrice))
                .orElse(null);
        // Если нет минимума или максимума выходим (невозможно)
        if (maxPrice == null || minPrice == null) {
            return;
        }

        // Если имбаланс сейчас в процессе
        if (!imbalances.isEmpty() && imbalances.get(imbalances.size() - 1).getStatus() == Imbalance.Status.PROGRESS) {

            // Если имбаланс на рост (вверх)
            if (imbalances.get(imbalances.size() - 1).getType() == Imbalance.Type.UP) {
                // Если максимум последнего тика больше чем у имбаланса ->  обновляем максимум имбаланса
                if (maxPrice.getHighPrice() > imbalances.get(imbalances.size() - 1).getMax().getHighPrice()) {
                    imbalances.get(imbalances.size() - 1).setMax(maxPrice);
                }
                // Если максимум не обновлялся IMBALANCE_COMPLETE_TIME -> считаем его завершенным
                if (marketData.get(marketData.size() - 1).getStartTime() - imbalances.get(imbalances.size() - 1).getMax().getStartTime() > IMBALANCE_COMPLETE_TIME) {
                    imbalances.get(imbalances.size() - 1).setStatus(Imbalance.Status.COMPLETE);
                }
            } else {
                // Если имбаланс вниз

                // Если минимум последнего тика меньше чем у имбаланса ->  обновляем минимум имбаланса
                if (minPrice.getLowPrice() < imbalances.get(imbalances.size() - 1).getMin().getLowPrice()) {
                    imbalances.get(imbalances.size() - 1).setMin(minPrice);
                }
                // Если минимум не обновлялся IMBALANCE_COMPLETE_TIME -> считаем его завершенным
                if (marketData.get(marketData.size() - 1).getStartTime() - imbalances.get(imbalances.size() - 1).getMin().getStartTime() > IMBALANCE_COMPLETE_TIME) {
                    imbalances.get(imbalances.size() - 1).setStatus(Imbalance.Status.COMPLETE);
                }
            }

        } else {
            // Если имбаланс присутствует и завершен и с момента завершения прошло меньше чем IMBALANCE_SEARCH_TIME_WINDOW времени -> ждём
            if (!imbalances.isEmpty() && imbalances.get(imbalances.size() - 1).getStatus() == Imbalance.Status.COMPLETE &&
                    ((imbalances.get(imbalances.size() - 1).getType() == Imbalance.Type.UP &&
                            marketData.get(0).getStartTime() < imbalances.get(imbalances.size() - 1).getMax().getStartTime())
                            ||
                            (imbalances.get(imbalances.size() - 1).getType() == Imbalance.Type.DOWN &&
                                    marketData.get(0).getStartTime() < imbalances.get(imbalances.size() - 1).getMin().getStartTime()))
            ) {
                return;
            }

            // Если имбаланса нет или прошло уже достаточно времени для проверки нового имбаланса и если разница цен больше чем 'priceThreshold'
            // -> добавляем имбаланс в лист и ставим статус в процессе.
            if (maxPrice.getHighPrice() - minPrice.getLowPrice() > priceThreshold) {
                Imbalance imbalance = new Imbalance(minPrice, maxPrice);
                imbalance.setStatus(Imbalance.Status.PROGRESS);
                if (minPrice.getStartTime() > maxPrice.getStartTime()) {
                    imbalance.setType(Imbalance.Type.DOWN);
                } else {
                    imbalance.setType(Imbalance.Type.UP);
                }
                imbalances.add(imbalance);
            }
        }
    }

    public List<Imbalance> getImbalances() {
        return imbalances;
    }
}
