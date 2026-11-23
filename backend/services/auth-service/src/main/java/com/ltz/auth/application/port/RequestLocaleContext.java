package com.ltz.auth.application.port;

import java.util.Optional;

/**
 * İsteği yapan tarayıcının arayüz dili (frontend {@code Accept-Language} başlığına site dilini yazar). Yalnız {@code tr}
 * ya da {@code en}; başlık yoksa, tanınmıyorsa ya da istek dışındaysa (iç çağrı, zamanlanmış iş) boştur. Güvenilmez
 * girdidir: yalnız mail şablonunun dilini seçmek için yedek olarak kullanılır, hiçbir yere kalıcı yazılmaz.
 */
public interface RequestLocaleContext {

    Optional<String> locale();
}
