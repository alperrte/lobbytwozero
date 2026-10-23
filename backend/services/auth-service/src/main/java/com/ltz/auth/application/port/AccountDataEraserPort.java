package com.ltz.auth.application.port;

import java.util.UUID;

/**
 * Auth → diğer servisler: hesaba ait verinin silinmesi/anonimleştirilmesi (AUTH-16/17).
 * <p>
 * Sözleşme: giden, idempotent çağrı ({@code DELETE <SERVICE>_SERVICE_URL/internal/v1/accounts/{accountId}}, servis
 * JWT'siyle: aud=&lt;servis&gt;-service, scope=account:erase, account_id=hesap). Yalnız hedef servis silmeyi
 * TAMAMLADIĞINDA ({@code 200}/{@code 204}; zaten silinmişse veya veri yoksa da) normal döner, aksi halde
 * {@link AccountErasureException} fırlatır. Auth'ta onay alan bir iç uç yoktur; kimliksiz iç uç açılmaz. Diğer
 * servisler Auth veritabanına erişmez.
 */
public interface AccountDataEraserPort {

    /** @throws AccountErasureException silme onaylanmadıysa; çağrı daha sonra aynen tekrarlanır */
    void erase(String service, UUID requestId, UUID accountId);
}
