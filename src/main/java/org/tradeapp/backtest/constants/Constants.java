package org.tradeapp.backtest.constants;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.tradeapp.logging.Log;

public class Constants {
    public static final String PEZDA = """
            
            
                       -----------------------------------
                  ---------------------------------------------
              -----------------------------------------------------
            ----------                                     ----------
            ----------                                     ----------
              -----------------------------------------------------
                  ---------------------------------------------
                       -----------------------------------
            
            
            """;
    public static final ObjectMapper mapper = new ObjectMapper();
    public static final String SYMBOL = "BTCUSDT";

    // account parameters
    public static final int BALANCE = 10000;
    public static final double RISK_LEVEL = 1.;
    public static final int CREDIT_LEVEL = 6;

    // strategy parameters
    public static final int TAKES_COUNT = 2;
    public static final double[] TAKE_PROFIT_THRESHOLDS = new double[]{0.5, 0.75};
    public static final double STOP_LOSS_MODIFICATOR = 0.01;
    public static final long POSITION_LIVE_TIME = 240 * 60_000L;

    // imbalance parameters
    /**
     * Время хранения ежесекундных данных (1000мс * 60с * 5м = 5 минут).
     * Отдельная коллекция для поиска окончания размером 120 секунд.
     */
    public static final long DATA_LIVE_TIME = 10 * 60_000L;
    public static final long LARGE_DATA_LIVE_TIME = 60 * 60_000L;
    public static final long LARGE_DATA_ENTRY_SIZE = 15_000L;
    /**
     * Время за которое если не появилось нового минимума то считаем имбаланс завершенным (1000мс * 60с = 1 минута)
     */
    public static final double COMPLETE_TIME_MODIFICATOR = 0.5;
    public static final double POTENTIAL_COMPLETE_TIME_MODIFICATOR = 0.05; //TODO для быстрых и медленных нужна разная формула
    /**
     * Константы для расчета минимальной скорости и цены.
     * Формула: минимальная цена/скорость = [средняя цена] * [волатильность] * [константа]
     */
    public static final double SPEED_MODIFICATOR = 1E-7, PRICE_MODIFICATOR = 0.02;

    public static final double MAX_VALID_IMBALANCE_PART = 0.2;
    public static final long MIN_IMBALANCE_TIME_DURATION = 10_000L;
    public static final long TIME_CHECK_CONTR_IMBALANCE = 60 * 60_000L;
    public static final long MIN_POTENTIAL_COMPLETE_TIME = 2_000L;
    public static final long MIN_COMPLETE_TIME = 60_000L;
    public static final double RETURNED_PRICE_IMBALANCE_PARTITION = 0.5;


    //volatility parameters
    /**
     * Период обновления волатильности и средней цены (1000мс * 60с * 60м * 24ч = 1 день)
     */
    public static final long UPDATE_TIME_PERIOD_MILLS = 24L * 60L * 60L * 1000L;
    public static final int VOLATILITY_CALCULATE_DAYS_COUNT = 1;
    public static final int AVERAGE_PRICE_CALCULATE_DAYS_COUNT = 1;

}
