package com.ltz.recommendation.application.port;

import com.ltz.recommendation.application.port.PersonalizationSource.Actor;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * REC-15: Event Service'in izleyiciye açık yaklaşan etkinlik listesi. Kapasite, katılımcı sayısı, izleyicinin kendi
 * katılımı ve {@code joinable} Event sözleşmesinden (EVT-08/EVT-13) okunur. Katılım cezası
 * öneriyle ilgisizdir ve burada okunmaz.
 */
public interface EventDiscoverySource {
    /** Sınırlı sayıda sayfa tarar; {@code truncated} true ise taranmayan etkinlik vardır. */
    Scan upcoming(Actor actor);

    /**
     * {@code capacity} null ise sınırsızdır. {@code joinable}, Event'in izleyici için hesapladığı uygunluktur: aktif
     * katılımı yok, etkinlik açık, dolu değil, izleyici düzenleyici değil.
     */
    record Event(UUID id, UUID organizerId, String type, UUID gameId, Set<String> platforms, String title,
            Instant startsAt, Instant endsAt, String status, Integer capacity, int participantCount,
            String viewerParticipation, boolean joinable) {
        public Event {
            if (id == null || type == null || title == null || startsAt == null || endsAt == null || status == null
                    || participantCount < 0 || (capacity != null && capacity < 1)) {
                throw new IllegalArgumentException("Invalid event");
            }
            platforms = Set.copyOf(platforms);
        }
    }

    record Scan(List<Event> events, boolean truncated) {
        public Scan { events = List.copyOf(events); }
    }
}
