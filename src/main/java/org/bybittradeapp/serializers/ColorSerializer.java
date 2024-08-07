package org.bybittradeapp.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.awt.Color;
import java.io.IOException;

public class ColorSerializer extends JsonSerializer<Color> {

    @Override
    public void serialize(Color color, JsonGenerator jsonGenerator, SerializerProvider serializers) throws IOException {
        // You can serialize the color in a simple way (e.g., by name or hex code)
        String colorName = getColorName(color);
        jsonGenerator.writeString(colorName);
    }

    private String getColorName(Color color) {
        // Here you can implement custom logic to return color name or hex code
        if (Color.RED.equals(color)) {
            return "red";
        } else if (Color.GREEN.equals(color)) {
            return "green";
        } else if (Color.BLUE.equals(color)) {
            return "blue";
        } else {
            // Default to hex code if the color is not pre-defined
            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
        }
    }
}
