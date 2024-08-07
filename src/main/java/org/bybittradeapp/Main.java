package org.bybittradeapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.bybittradeapp.serializers.ColorSerializer;
import org.bybittradeapp.service.MarketDataService;
import org.bybittradeapp.service.UIService;

import java.awt.*;

public class Main {

    public static final boolean TEST_OPTION = true;
    public static final ObjectMapper mapper = new ObjectMapper();

    private static final MarketDataService marketDataService = new MarketDataService();
    private static final UIService uiService = new UIService();

    public static void main(String[] args) {
        registerModules();
        uiService.start();
        marketDataService.start();
    }

    private static void registerModules() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Color.class, new ColorSerializer());
        mapper.registerModule(module);
    }
}

