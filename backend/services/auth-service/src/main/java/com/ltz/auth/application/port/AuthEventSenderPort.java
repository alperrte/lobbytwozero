package com.ltz.auth.application.port;

/**
 * Auth → diğer servisler: olay teslimi. Sözleşme: {@code POST <HEDEF>_SERVICE_URL/internal/events}, servis JWT'siyle
 * ({@code sub=svc:auth-service}, {@code aud=<hedef>-service}, {@code scope=events:ingest}), gövde olay zarfı. Yalnız
 * {@code 2xx} teslim sayılır; diğer her durum ve hata {@link AuthEventDeliveryException} fırlatır, çağrı aynen tekrarlanır.
 * Alıcı {@code eventId} ile tekilleştirir.
 */
public interface AuthEventSenderPort {

    /** @throws AuthEventDeliveryException teslim onaylanmadıysa */
    void send(String target, AuthEventEnvelope envelope);
}
