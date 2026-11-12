package com.ltz.game.application.port;

/**
 * İsteği yapanın yetişkin içerik (+18) listelemeye yetkisi. Yetki yalnız Auth'un imzaladığı access token'daki {@code adult_verified=true} iddiasından gelir
 * (merkezi güvenlik katmanı üretir); anonim istek, iddiası olmayan, {@code false} veya yanlış türde olan token yetkisizdir (kapalı varsayılan).
 */
public record AdultAccess(boolean verified) {
    private static final AdultAccess DENIED = new AdultAccess(false);

    public static AdultAccess denied() { return DENIED; }

    /** İstenen {@code includeAdult} değeri yalnız yetki varsa uygulanır; yetkisizken sessizce yok sayılır. */
    public boolean allows(boolean requested) { return requested && verified; }
}
