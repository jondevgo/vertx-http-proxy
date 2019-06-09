package io.vertx.httpproxy;

public interface HttpResponseCache {
    Response get(String key);
    void remove(String key);
    void put(String key, Response value);

    interface Response {
        long getSize();
        long getMaxAge();
        long getTimestamp();
    }
}
