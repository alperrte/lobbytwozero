package com.ltz.user.api.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Hesap kullanılabilirliği, istek sırasıyla (yinelenen kimlikler bir kez). {@code ACTIVE}: profili olan ve hesabı etkin
 * kullanıcı. {@code UNAVAILABLE}: bunun dışındaki her durum (profil yok, askıda, yasaklı, devre dışı, doğrulanmamış, silme
 * bekliyor); var olmayan ve kullanılamaz hesap birbirinden ayırt edilemez. Profil verisi dönmez.
 */
public record UserStatusResponse(List<Item> results) {

    /** Tek hesabın kullanılabilirliği. */
    public record Item(UUID userId, String status) {
    }
}
