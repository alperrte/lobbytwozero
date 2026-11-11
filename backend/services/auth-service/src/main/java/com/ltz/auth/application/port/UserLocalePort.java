package com.ltz.auth.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Auth → User: hesabın dil tercihi (MAIL-AUTH-REQ-02). <b>En iyi çaba</b>: dil bilinmiyorsa, User yanıt vermezse ya da
 * değer geçersizse {@link Optional#empty()} döner ve asla istisna fırlatmaz; mail iki dilli gider.
 */
public interface UserLocalePort {

    /** @return yalnız {@code tr} ya da {@code en}; bilinmiyorsa boş */
    Optional<String> localeOf(UUID accountId);
}
