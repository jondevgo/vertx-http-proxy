package io.vertx.httpproxy;

import java.util.HashMap;
import java.util.Map;

public class SimpleHttpResponseCache implements HttpResponseCache {

    private final Map<String, Response> cache = new HashMap<>();

    @Override
    public Response get(String key) {
        return this.cache.get(key);
    }

    @Override
    public void remove(String key) {
        this.cache.remove(key);
    }

    @Override
    public void put(String key, Response value) {
        this.cache.put(key, value);
    }
}
