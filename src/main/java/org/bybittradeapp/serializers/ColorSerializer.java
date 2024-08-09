package org.bybittradeapp.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.awt.Color;
import java.io.IOException;

import static org.bybittradeapp.utils.JsonUtils.getColorHex;

public class ColorSerializer extends JsonSerializer<Color> {

    @Override
    public void serialize(Color color, JsonGenerator jsonGenerator, SerializerProvider serializers) throws IOException {
        // You can serialize the color in a simple way (e.g., by name or hex code)
        String colorName = getColorHex(color);
        jsonGenerator.writeString(colorName);
    }
}
