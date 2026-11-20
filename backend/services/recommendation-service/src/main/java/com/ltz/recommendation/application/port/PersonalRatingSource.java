package com.ltz.recommendation.application.port;

import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import java.util.Map;

/** REC-03: kullanıcının kendi puanlarından tür bazlı puan yakınlığı (0..10000); puan yoksa boş harita. */
public interface PersonalRatingSource {
    /** Anahtar küçük harfli tür kodudur; yalnız ortalaması 5 üzerindeki türler döner. Kaynak hatasında istisna (fail-closed). */
    Map<String, Integer> genreAffinity(Actor actor);
}
