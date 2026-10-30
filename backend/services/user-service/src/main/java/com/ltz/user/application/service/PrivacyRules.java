package com.ltz.user.application.service;

import com.ltz.user.domain.enums.GuestAccess;
import com.ltz.user.domain.enums.Visibility;
import com.ltz.user.domain.enums.ViewerRelation;

/**
 * Tek gizlilik kuralı (USER-05/06, K4/K5). Açık profil, toplu kart, arama ve servislerin karar ucu aynı kuralı kullanır;
 * kural başka yerde tekrar yazılmaz. Engel bu kuraldan önce denetlenir: aralarında engel olan izleyici profili hiç görmez.
 *
 * <table>
 *   <caption>Bir bölümün görünürlüğü</caption>
 *   <tr><th>İzleyici</th><th>PUBLIC</th><th>FRIENDS</th><th>PRIVATE</th></tr>
 *   <tr><td>Sahip</td><td>evet</td><td>evet</td><td>evet</td></tr>
 *   <tr><td>Arkadaş</td><td>evet</td><td>evet</td><td>hayır</td></tr>
 *   <tr><td>Giriş yapmış üye</td><td>evet</td><td>hayır</td><td>hayır</td></tr>
 *   <tr><td>Misafir</td><td>yalnız DETAILED</td><td>hayır</td><td>hayır</td></tr>
 * </table>
 */
public final class PrivacyRules {

    private PrivacyRules() {
    }

    /** Görünürlüğü {@code visibility} olan bölümü izleyici görebilir mi. */
    public static boolean allows(ViewerRelation relation, Visibility visibility, GuestAccess guestAccess) {
        return switch (relation) {
            case OWNER -> true;
            case FRIEND -> visibility != Visibility.PRIVATE;
            case MEMBER -> visibility == Visibility.PUBLIC;
            case GUEST -> visibility == Visibility.PUBLIC && guestAccess == GuestAccess.DETAILED;
        };
    }

    /**
     * Biyografi, oyuncu türü, favori tür/platform ve ülke gibi profil ayrıntıları. Bunlar alan gizliliğine tabi değildir
     * (profil bütünüyle gizlenemez); yalnız misafir için sahibin {@code DETAILED} izni gerekir.
     */
    public static boolean showsDetails(ViewerRelation relation, GuestAccess guestAccess) {
        return relation != ViewerRelation.GUEST || guestAccess == GuestAccess.DETAILED;
    }
}
