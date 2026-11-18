package com.ltz.recommendation.application.port;

import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import java.util.Map;

/** REC-02/05: kullanıcının favori ve tamamladığı oyunların tür dağılımı (tür bazlı yakınlık 0..10000). */
public interface LibraryTasteSource {
    /** Kaynak hatasında istisna (fail-closed): eksik Library verisi sessizce sinyalsiz öneriye çevrilmez. */
    Affinity affinity(Actor actor);

    /** Anahtarlar küçük harfli tür kodudur; değer, kümedeki görünür oyunların o türü taşıma payıdır (0..10000). */
    record Affinity(Map<String, Integer> favoriteGenres, Map<String, Integer> completedGenres) {
        public static final Affinity NONE = new Affinity(Map.of(), Map.of());

        public Affinity {
            favoriteGenres = Map.copyOf(favoriteGenres);
            completedGenres = Map.copyOf(completedGenres);
        }
    }
}
