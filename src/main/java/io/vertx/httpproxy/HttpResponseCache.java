package io.vertx.httpproxy;

public interface HttpResponseCache {
    Object get(String key);
    void remove(String key);
    void put(String key, Object value);
}
