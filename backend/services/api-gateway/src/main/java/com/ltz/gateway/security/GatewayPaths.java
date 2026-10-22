package com.ltz.gateway.security;

import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.http.HttpServletRequest;

/** Rota eşlemesiyle aynı çözülmüş (decode edilmiş, normalize) yol üzerinden karar verir; ham URI kullanılmaz. */
final class GatewayPaths {

    static final String API_PREFIX = "/api/v1/";
    static final String AUTH_PREFIX = "/api/v1/auth";
    private static final UrlPathHelper HELPER = UrlPathHelper.defaultInstance;

    private GatewayPaths() {
    }

    static String path(HttpServletRequest request) {
        return HELPER.getPathWithinApplication(request);
    }

    static boolean isApi(String path) {
        return path.startsWith(API_PREFIX);
    }

    static boolean isAuth(String path) {
        return path.equals(AUTH_PREFIX) || path.startsWith(AUTH_PREFIX + "/");
    }
}
