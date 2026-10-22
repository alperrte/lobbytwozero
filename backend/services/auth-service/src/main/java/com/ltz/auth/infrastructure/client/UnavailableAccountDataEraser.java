package com.ltz.auth.infrastructure.client;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ltz.auth.application.port.AccountDataEraserPort;
import com.ltz.auth.application.port.AccountErasureException;

/**
 * Üretim davranışı: hedef servislerde silme ucu ve servisler arası kimlik (D4) henüz yoktur. Kimliksiz bir iç çağrı
 * yapmak yerine silme açıkça onaylanmamış sayılır; görev PENDING kalır, geri çekilmeyle yeniden denenir ve hesap
 * "silindi" olarak işaretlenmez. Servis uçları ve servis JWT'si hazır olduğunda yerini RestClient adaptörü alır.
 */
@Component
public class UnavailableAccountDataEraser implements AccountDataEraserPort {

    static final String ERROR_CODE = "SERVICE_ERASURE_UNAVAILABLE";

    @Override
    public void erase(String service, UUID requestId, UUID accountId) {
        throw new AccountErasureException(ERROR_CODE);
    }
}
