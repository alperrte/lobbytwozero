package com.ltz.event.infrastructure.security;

import java.util.Set;

/**
 * Etkinlik oluşturabilen Auth rolleri. Rol adları Auth'un kanonik rol kodlarıdır
 * ({@code PLAYER, CONTENT_CREATOR, GAME_DEVELOPER, MODERATOR, ADMIN}). Karar KD-1 (servis sahibi Nisa, 2026-11-09):
 * kayıtlı oyuncular ({@code PLAYER}), {@code MODERATOR} ve {@code ADMIN} etkinlik oluşturabilir
 * (docs-event/event-service/evt-01-kararlar-ve-varsayimlar.md). Auth'ta {@code PLAYER} temel roldür ve geri alınamaz
 * ({@code BASE_ROLE_REQUIRED}); bu yüzden pratikte kimliği doğrulanmış her aktif hesap oluşturabilir.
 *
 * <p>Güncelleme/iptal (EVT-14, EVT-15) için "sahip veya yetkili kullanıcı" kuralı ayrıca karara bağlanacaktır; bu küme
 * onlar için kullanılmaz. Kural tek yerde tutulur; SecurityConfig ve {@link SecurityContextCurrentUser} buradan okur.
 */
public final class EventRoles {

	public static final String PLAYER = "PLAYER";
	public static final String MODERATOR = "MODERATOR";
	public static final String ADMIN = "ADMIN";

	/** Etkinlik oluşturabilen roller; öneksiz. */
	public static final Set<String> EVENT_CREATORS = Set.of(PLAYER, MODERATOR, ADMIN);

	private EventRoles() {
	}
}
