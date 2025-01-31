package org.tradeapp.backtest.binance;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.tradeapp.backtest.domain.APIError;
import org.tradeapp.backtest.domain.HTTPResponse;
import org.tradeapp.utils.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.stream.Collectors;

import static org.tradeapp.backtest.constants.Settings.mapper;

public class HttpClient {

    private static final String BASE_URL = "https://api.binance.com";

    private final Log log = new Log();

    protected String getParamsString(Map<String, String> params) {
        return params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    public HTTPResponse<String, APIError> sendPublicRequest(String endpoint, String method, Map<String, String> params, long currentTime) {
        try {
//            long start = System.nanoTime();
//            log.debug(String.format("[REQUEST START] HTTP %s to %s", method, endpoint), currentTime);

            String query = getParamsString(params);
            URL url = new URI(BASE_URL + endpoint + "?" + query).toURL();
//            log.debug(String.format("Generated URL: %s", url), currentTime);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
//            log.debug(String.format("Request properties: %s", connection.getRequestProperties()), currentTime);

            int responseCode = connection.getResponseCode();
//            Map<String, List<String>> headers = connection.getHeaderFields();
//            log.debug(String.format("Response headers: %s", headers), currentTime);

//            long finish = System.nanoTime();
//            double elapsedMs = (finish - start) / 1_000_000.0;
//            log.debug(String.format("[REQUEST END] HTTP GET to %s completed in %.2f ms", endpoint, elapsedMs), currentTime);

            return readResponse(connection, responseCode, currentTime);
        } catch (Exception e) {
            throw log.throwError("Failed to send HTTP request", e, currentTime);
        }
    }

    private HTTPResponse<String, APIError> readResponse(HttpURLConnection connection, int responseCode, long currentTime) throws IOException {

        if (responseCode >= 200 && responseCode < 300) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String response = reader.lines().collect(Collectors.joining());
//                log.debug(String.format("Response code: %d, Response body: %s", responseCode, response), currentTime);
                return HTTPResponse.success(responseCode, response);
            }
        } else {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                String errorResponse = reader.lines().collect(Collectors.joining());
                log.warn(String.format("Response code: %d, Error body: %s", responseCode, errorResponse), currentTime);
                return HTTPResponse.error(responseCode, parseAPIError(errorResponse, currentTime));
            }
        }
    }

    public APIError parseAPIError(String error, long currentTime) {
        try {
            return mapper.readValue(error, APIError.class);
        } catch (JsonProcessingException e) {
            throw log.throwError("Failed to parse HTTP response", e, currentTime);
        }
    }
}
