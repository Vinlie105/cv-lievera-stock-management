package com.cvlievera.stockmanagement.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Returns the connection's remote address. X-Forwarded-* headers are NOT parsed here because clients can spoof
 * them. Behind a reverse proxy, set server.forward-headers-strategy=native so the container resolves them
 * from the trusted proxy only.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
