package com.ltz.game.application.port;

import java.util.function.Supplier;

/**
 * Sağlayıcı çağrılarının önceliği (iş parçacığı bağlamı). Varsayılan öncelik ARKA PLANDIR: günlük kotanın belirli payı dolunca arka plan taramaları
 * (diğer oyunların aralıklı yenilemesi, tür birikimi) durur. "Öncelikli" işaretli çağrılar (sayfası açılan oyunun anlık yenilemesi, sıcak oyunların
 * yenilemesi) günlük sınırın sonuna kadar çalışır; böylece kullanıcının gördüğü ve en hızlı değişen veri arka plan taramasına aç bırakılmaz.
 */
public final class ProviderPriority {
    private static final ThreadLocal<Boolean> ESSENTIAL = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private ProviderPriority() { }

    /** Bu iş parçacığında çalışan çağrı öncelikli mi. */
    public static boolean isEssential() { return ESSENTIAL.get(); }

    /** İşi öncelikli olarak çalıştırır; önceki durum her koşulda geri yüklenir. */
    public static <T> T essential(Supplier<T> work) {
        boolean previous = ESSENTIAL.get();
        ESSENTIAL.set(Boolean.TRUE);
        try {
            return work.get();
        } finally {
            if (previous) {
                ESSENTIAL.set(Boolean.TRUE);
            } else {
                ESSENTIAL.remove();
            }
        }
    }
}
