package org.tradeapp.ui.utils;

import org.tradeapp.logging.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.Map;

import static org.tradeapp.Main.SYMBOL;

public class Serializer<T> {

    private final String path;

    public Serializer(String path) {
        this.path = path;
    }

    public void serialize(T data) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(System.getProperty("user.dir") + path + SYMBOL);
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)
        ) {
            Log.debug("serializing...");
            objectOutputStream.writeObject(data);
            if (data instanceof Collection<?>) {
                Log.debug("list size=[" + ((Collection<?>) data).size() + "] serialized");
            } else if (data instanceof Map<?,?>){
                Log.debug("map size=[" + ((Map<?, ?>) data).size() + "] serialized");
            } else {
                Log.debug("object=" + data.toString() + " serialized");
            }
        } catch (IOException e) {
            Log.debug(e, false);
        }
    }

    @SuppressWarnings("unchecked")
    public T deserialize() {
        try (FileInputStream fileInputStream = new FileInputStream(System.getProperty("user.dir") + path + SYMBOL);
             ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)
        ) {
            Log.debug("deserializing...");
            T data = (T) objectInputStream.readObject();
            if (data instanceof Collection<?>) {
                Log.debug("list size=[" + ((Collection<?>) data).size() + "] deserialized");
            } else if (data instanceof Map<?,?>){
                Log.debug("map size=[" + ((Map<?, ?>) data).size() + "] deserialized");
            } else {
                Log.debug("object=" + data.toString() + " deserialized");
            }
            return data;
        } catch (ClassNotFoundException | IOException e) {
            Log.debug(e, false);
            return null;
        }
    }
}
