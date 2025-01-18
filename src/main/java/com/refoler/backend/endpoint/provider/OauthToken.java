package com.refoler.backend.endpoint.provider;

import java.util.Objects;

public class OauthToken {
    private final String tokenKey;
    private final long validAfter;

    public OauthToken(String tokenKey) {
        this.tokenKey = tokenKey.split("\\.")[0];
        this.validAfter = System.currentTimeMillis();
    }

    public boolean compareToken(String tokenKey) {
        String[] sectors = tokenKey.split("\\.");
        return Objects.equals(sectors[0], this.tokenKey);
    }

    public boolean isStillValid() {
        return (System.currentTimeMillis() - validAfter) <= 3600000; // 1 Hour Timeout
    }
}
