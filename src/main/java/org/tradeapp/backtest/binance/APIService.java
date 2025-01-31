package org.tradeapp.backtest.binance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.tradeapp.backtest.domain.APIError;
import org.tradeapp.backtest.domain.HTTPResponse;
import org.tradeapp.backtest.domain.MarketEntry;
import org.tradeapp.utils.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.tradeapp.backtest.constants.Settings.mapper;

public class APIService {

    private final Log log = new Log();
    private final HttpClient httpClient;

    public APIService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public HTTPResponse<TreeMap<Long, MarketEntry>, APIError> getMarketDataPublicAPI(String symbol, String interval, long start, int limit, long currentTime) {
        Map<String, String> params = new HashMap<>();
        params.put("symbol", symbol);
        params.put("limit", String.valueOf(limit));
        params.put("interval", String.valueOf(interval));
        params.put("startTime", String.valueOf(start));

        var response = httpClient.sendPublicRequest("/api/v3/klines", "GET", params, currentTime);
        if (response.isSuccess()) {
            return HTTPResponse.success(response.getStatusCode(), parseResponseMarketData(response.getValue(), currentTime));
        } else {
            return HTTPResponse.error(response.getStatusCode(), response.getError());
        }
    }

    public TreeMap<Long, MarketEntry> parseResponseMarketData(String response, long currentTime) {
        try {
            ArrayNode klines = mapper.readValue(response, ArrayNode.class);
            TreeMap<Long, MarketEntry> marketData = new TreeMap<>();
            for (int i = 0; i < klines.size(); i++) {

                ArrayNode candle = mapper.convertValue(klines.get(i), ArrayNode.class);
                long timestamp = candle.get(0).asLong();
                double high = candle.get(2).asDouble();
                double low = candle.get(3).asDouble();
                double volume = candle.get(5).asDouble();
                MarketEntry entry = new MarketEntry(high, low, volume);
                marketData.put(timestamp, entry);
            }
            return marketData;
        } catch (JsonProcessingException e) {
            throw log.throwError("Failed to parse market data", e, currentTime);
        }
    }
}
