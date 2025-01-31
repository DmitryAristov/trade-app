package org.tradeapp;

import org.tradeapp.backtest.binance.APIService;
import org.tradeapp.backtest.binance.HttpClient;
import org.tradeapp.backtest.domain.Account;
import org.tradeapp.backtest.service.*;
import org.tradeapp.utils.Log;
import org.tradeapp.backtest.domain.MarketEntry;
import org.tradeapp.ui.domain.MarketKlineEntry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.tradeapp.backtest.constants.Settings.*;

/**
 * Для тестирования стратегии на предыдущих исторических данных.
 */
public class BackTester {
    public static final boolean SKIP_MARKET_DATA_UPDATE = true;

    private final Log log = new Log();

    private final ExchangeSimulator simulator;
    private final Strategy strategy;
    private final Account account;
    private final VolatilityService volatilityService;
    private final ImbalanceService imbalanceService;
    private final TreeMap<Long, MarketEntry> marketData;

    public BackTester(String symbol,
                      APIService apiService,
                      TreeMap<Long, MarketEntry> marketData,
                      TreeMap<Long, MarketKlineEntry> uiMarketData) {
        this.marketData = marketData;

        log.info(PEZDA, marketData.firstKey());
        log.info(String.format("""
                        account parameters:
                            balance :: %d$
                            risk :: %d%%
                            credit :: %d""",
                BALANCE, (long) (RISK_LEVEL * 100), CREDIT_LEVEL),
                marketData.firstKey());

        //noinspection ConstantValue
        if (TAKES_COUNT > TAKE_PROFIT_THRESHOLDS.length) {
            throw log.throwError("foreach take modifier must be defined", marketData.firstKey());
        }
        log.info(String.format("""
                        strategy parameters:
                            takes count :: %d
                            takes modifiers :: %s
                            stop modificator :: %.2f
                            position live time :: %d minutes""",
                TAKES_COUNT,
                Arrays.toString(TAKE_PROFIT_THRESHOLDS),
                STOP_LOSS_MODIFICATOR,
                POSITION_LIVE_TIME / 60_000L), marketData.firstKey());

        log.info(String.format("""
                        imbalance parameters:
                            complete time modificator :: %.3f
                            potential complete time modificator :: %.3f
                            speed modificator :: %s
                            price modificator :: %s
                            maximum valid imbalance part when open position :: %.3f
                            minimum imbalance time duration :: %d seconds
                            minimum potential complete time :: %d seconds
                            minimum complete time :: %d seconds
                            data live time :: %d minutes
                            large data live time :: %d minutes
                            large data entry size :: %d seconds
                            time in the past to check for contr-imbalance :: %d minutes
                            already returned price imbalance partition on potential endpoint check %.3f""",
                COMPLETE_TIME_MODIFICATOR,
                POTENTIAL_COMPLETE_TIME_MODIFICATOR,
                SPEED_MODIFICATOR,
                PRICE_MODIFICATOR,
                MAX_VALID_IMBALANCE_PART,
                MIN_IMBALANCE_TIME_DURATION/1000,
                MIN_POTENTIAL_COMPLETE_TIME/1000,
                MIN_COMPLETE_TIME/1000,
                DATA_LIVE_TIME/60_000L,
                LARGE_DATA_LIVE_TIME/60_000L,
                LARGE_DATA_ENTRY_SIZE/1000,
                TIME_CHECK_CONTR_IMBALANCE/60_000L,
                RETURNED_PRICE_IMBALANCE_PARTITION), marketData.firstKey());
        log.info(String.format("""
                        imbalance parameters:
                            update time period :: %d hours
                            volatility calculation past time :: %d days
                            average price calculation past time :: %d days""",
                UPDATE_TIME_PERIOD_MILLS / 3_600_000L,
                VOLATILITY_CALCULATE_PAST_TIME_DAYS,
                AVERAGE_PRICE_CALCULATE_PAST_TIME_DAYS), marketData.firstKey());

        this.account = new Account();
        this.simulator = new ExchangeSimulator(account);
        this.volatilityService = new VolatilityService(symbol, apiService);
        this.imbalanceService = new ImbalanceService();
        imbalanceService.setData(marketData);
        volatilityService.subscribe(this.imbalanceService);
        this.strategy = new Strategy(simulator, marketData, uiMarketData, imbalanceService, account);
    }

    public void runTests() {
        AtomicLong step = new AtomicLong(0L);
        long startTime = Instant.now().toEpochMilli();
        long firstKey = marketData.firstKey();//1666775517000
        long lastKey = marketData.lastKey();//1688132229000
//        long firstKey = 1666675517000L;
//        long lastKey = 1688032229000L;

        ZonedDateTime zonedDateTime = Instant.ofEpochMilli(firstKey).atZone(ZoneId.of("UTC"));
        int year = zonedDateTime.getYear();

        log.info(String.format("starting backtest with balance %.2f$", account.getBalance()), firstKey);
        try {
            marketData
                    .subMap(firstKey, lastKey)
                    .forEach((currentTime, currentEntry) -> {
                        volatilityService.onTick(currentTime, currentEntry);
                        imbalanceService.onTick(currentTime, currentEntry);
                        strategy.onTick(currentTime, currentEntry);
                        simulator.onTick(currentTime, currentEntry);

                        double progress = ((double) (currentTime - firstKey)) / ((double) (lastKey - firstKey));
                        log.logProgress(startTime, step, progress, "backtest " + year, currentTime);
                    });
        } catch (Exception e) {
            log.error("", e, lastKey);
        }
        log.info(String.format("backtest finished with balance %.2f$", account.getBalance()), lastKey);
    }

    public static void main(String[] args) {
        final HttpClient httpClient = new HttpClient();
        final APIService apiService = new APIService(httpClient);

        final FileMarketDataLoader handler = new FileMarketDataLoader(
                System.getProperty("user.dir") + "/input/market-data/" + SYMBOL,
                SYMBOL,
                apiService);

        if (!SKIP_MARKET_DATA_UPDATE)
            handler.updateOrDownloadData();

        for (int year = 2017; year <= 2025; year++) {
            TreeMap<Long, MarketEntry> marketData = handler.readAllEntries(year);
            BackTester tester = new BackTester(SYMBOL, apiService, marketData, null);
            tester.runTests();
        }
    }
}
