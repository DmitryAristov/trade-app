package org.bybittradeapp.service;

import com.bybit.api.client.domain.market.MarketInterval;
import org.bybittradeapp.domain.MarketKlineEntry;

import java.util.ArrayList;
import java.util.List;

public class VolatilityService {
    private static final UIService uiService1h = new UIService(MarketInterval.HOURLY);
    private static final UIService uiService4h = new UIService(MarketInterval.FOUR_HOURLY);
    private static final UIService uiServiceD = new UIService(MarketInterval.DAILY);

    /**
     * Method returns volatility of provided marked data in percents
     */
    public static double calculatePriceChange(ArrayList<MarketKlineEntry> marketData) {
        if (marketData == null || marketData.size() < 2) {
            return 0.;
        }

        List<Double> changes = new ArrayList<>();
        for (MarketKlineEntry marketDatum : marketData) {
            double priceDiff = marketDatum.getHighPrice() - marketDatum.getLowPrice();
            double priceAverage = (marketDatum.getHighPrice() + marketDatum.getLowPrice()) / 2.;
            changes.add(priceDiff / priceAverage);
        }

        return changes.stream().reduce(0., Double::sum) / changes.size() * 100.;
    }

    public static void main(String[] args) {
//        uiService1h.updateMarketData();
        uiService4h.updateMarketData();
//        uiServiceD.updateMarketData();

//        var volatility1h = calculatePriceChange(uiService1h.getMarketData());
//        var volatility4h = calculatePriceChange(uiService4h.getMarketData());
//        var volatilityD = calculatePriceChange(uiServiceD.getMarketData());
//
//        System.out.println(volatility1h);
//        System.out.println(volatility4h);
//        System.out.println(volatilityD);
//      BTC:
        double btc1h = 0.5719908663860126;
        double btc4h = 1.7133028249035402;
        double btcD = 4.3469057993100675;
//      TON
        double ton1h = 1.2888702367757248;
        double ton4h = 2.3457200279503754;
        double tonD = 5.939060780547791;
//      XRP
        double xrp1h = 0.788138097016813;
        double xrp4h = 2.6003298463923303;
        double xrpD = 6.549153215960341;
//      NOT
        double not1h = 2.5063465001917864;
        double not4h = 4.726035777560774;
        double notD = 12.264146365326816;

        System.out.println("btc / ton");
        System.out.println(btc1h / ton1h);
        System.out.println(btc4h / ton4h);
        System.out.println(btcD / tonD);

        System.out.println("btc / xrp");
        System.out.println(btc1h / xrp1h);
        System.out.println(btc4h / xrp4h);
        System.out.println(btcD / xrpD);

        System.out.println("btc / not");
        System.out.println(btc1h / not1h);
        System.out.println(btc4h / not4h);
        System.out.println(btcD / notD);

//      BTC:
//        0.5719908663860126
//        1.7133028249035402
//        4.3469057993100675
//      TON
//        1.2888702367757248
//        2.3457200279503754
//        5.939060780547791
//      XRP
//        0.788138097016813
//        2.6003298463923303
//        6.549153215960341
//      NOT
//        2.5063465001917864
//        4.726035777560774
//        12.264146365326816
    }
}
