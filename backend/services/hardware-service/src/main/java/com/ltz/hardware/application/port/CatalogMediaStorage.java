package com.ltz.hardware.application.port;

public interface CatalogMediaStorage {
    record Stored(String key, String hash, int width, int height) { }
    boolean available();
    Stored store(byte[] input);
    byte[] read(String key);
}
