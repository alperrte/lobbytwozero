package com.ltz.event.domain.enums;

/**
 * Etkinliğin oynanacağı platform (EVT-06). Platform, etkinliğin <b>kendi alanıdır</b>: bağlı oyundan türetilmez ve
 * oyuna bağlanmamış etkinlikte de verilebilir. Bir etkinlik birden çok platformda (çapraz platform turnuva) olabilir;
 * hiç platform belirtilmemiş de olabilir (eski etkinlikler dahil; rastgele platform atanmaz).
 *
 * <p>Değerler DB'de {@code event_platforms.platform} CHECK kısıtıyla birebir aynı olmalıdır ({@code EventPlatformTest}
 * bunu denetler). <b>Sözlük kararı KD-6a onaylandı (2026-11-11):</b> Event Service, Content Service'in sabit PLATFORM
 * kategori listesiyle ({@code content_categories}, V103) aynı altı değeri kullanır; LFP-03 örnekleriyle de uyumludur.
 * Game Service'in ince taneli sağlayıcı kodları ({@code win}, {@code ps5}, ...) kullanılmaz. Değişiklik yeni migration
 * (V116+; V115 değiştirilmez) + enum ister.
 */
public enum EventPlatform {
	PC,
	PLAYSTATION,
	XBOX,
	NINTENDO_SWITCH,
	MOBILE,
	VR
}
