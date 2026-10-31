package com.ltz.auth.infrastructure.security;

/**
 * Cihaz token'ının gidebileceği hedefler. Her token tek bir hedefe ({@code aud}) ve o hedefin tek kapsamına bağlıdır;
 * bir hedef için alınan token başka hedefte geçmez (confused deputy engellenir). İstemci her hedef için ayrı token alır.
 */
public enum DeviceTokenTarget {

    /** Varsayılan: hedef verilmezse bugünkü davranış (Hardware masaüstü senkronu). */
    HARDWARE("hardware", TokenClaims.HARDWARE_SERVICE_AUDIENCE, TokenClaims.HARDWARE_SYNC_WRITE_SCOPE),
    PERFORMANCE("performance", TokenClaims.PERFORMANCE_SERVICE_AUDIENCE, TokenClaims.PERFORMANCE_SYNC_WRITE_SCOPE);

    /** İstek gövdesindeki {@code target} değeri; kodda tek yerde tutulur, DTO doğrulaması bunu kullanır. */
    public static final String REQUEST_PATTERN = "^(hardware|performance)$";

    private final String requestName;
    private final String audience;
    private final String scope;

    DeviceTokenTarget(String requestName, String audience, String scope) {
        this.requestName = requestName;
        this.audience = audience;
        this.scope = scope;
    }

    public String audience() {
        return audience;
    }

    public String scope() {
        return scope;
    }

    /** {@code null} (alan verilmedi) varsayılan hedeftir; tanımsız değer çağıran tarafından {@link #REQUEST_PATTERN} ile elenir. */
    public static DeviceTokenTarget fromRequest(String requestName) {
        if (requestName == null) {
            return HARDWARE;
        }
        for (DeviceTokenTarget target : values()) {
            if (target.requestName.equals(requestName)) {
                return target;
            }
        }
        throw new IllegalArgumentException("Unknown device token target");
    }
}
