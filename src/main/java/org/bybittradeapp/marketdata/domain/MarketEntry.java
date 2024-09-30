package org.bybittradeapp.marketdata.domain;

import java.io.Serializable;


/**
 * Для хранения и анализа ежесекундных данных.
 */
public record MarketEntry(double high, double low) implements Serializable {  }
