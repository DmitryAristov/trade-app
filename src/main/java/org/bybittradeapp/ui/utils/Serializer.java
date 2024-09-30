package org.bybittradeapp.ui.utils;

import org.bybittradeapp.logging.Log;

import java.io.*;
import java.util.Collection;
import java.util.Map;

import static org.bybittradeapp.Main.SYMBOL;

public class Serializer<T> {

    private final String path;

    public Serializer(String path) {
        this.path = path;
    }

    public void serialize(T data) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(path + SYMBOL);
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)
        ) {
            Log.log("serializing...");
            objectOutputStream.writeObject(data);
            if (data instanceof Collection<?>) {
                Log.log("list size=[" + ((Collection<?>) data).size() + "] serialized");
            } else if (data instanceof Map<?,?>){
                Log.log("map size=[" + ((Map<?, ?>) data).size() + "] serialized");
            } else {
                Log.log("object=" + data.toString() + " serialized");
            }
        } catch (IOException e) {
            Log.log("error serialize object: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public T deserialize() {
        try (FileInputStream fileInputStream = new FileInputStream(path + SYMBOL);
             ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)
        ) {
            Log.log("deserializing...");
            T data = (T) objectInputStream.readObject();
            if (data instanceof Collection<?>) {
                Log.log("list size=[" + ((Collection<?>) data).size() + "] deserialized");
            } else if (data instanceof Map<?,?>){
                Log.log("map size=[" + ((Map<?, ?>) data).size() + "] deserialized");
            } else {
                Log.log("object=" + data.toString() + " deserialized");
            }
            return data;
        } catch (ClassNotFoundException | IOException e) {
            Log.log("error deserialize object: " + e.getMessage());
            return null;
        }
    }
}
