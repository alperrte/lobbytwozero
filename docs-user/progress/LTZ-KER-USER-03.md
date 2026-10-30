# LTZ-KER-USER-03 — Steam Hesap Bağlantısı

Sorumlu: Kerem. Görevler: USER-07, USER-08. Tarih: 2026-10-30.
Durum: **User tarafı kodu tamamlandı ve derlendi. TESTLER SONA ERTELENDİ.** Auth tarafı (bağlama/kaldırma ucu ve olaylar) bekliyor (T4, [auth-requests-05](../integration/auth/auth-service-talepleri/auth-requests-05.md)).

## Yapılanlar

| Görev | Kayıt |
| --- | --- |
| Migration | `V74__create_linked_accounts.sql`: `linked_accounts` (UQ kullanıcı+sağlayıcı, UQ sağlayıcı+dış kimlik, Steam kimliği CHECK, `show_on_profile` yalnız etkin bağlantıda) |
| USER-07 | `GET /api/v1/user/me/linked-accounts`: sağlayıcı başına bağlantı durumu, Steam kimliği, profil adresi, izin, sürüm |
| USER-08 | `PATCH /api/v1/user/me/linked-accounts/{provider}`: `showOnProfile` izni (iyimser kilit; yoksa `404`, sürüm uyuşmazsa `409`) |
| Olay girişi | `ExternalIdentityLinked` / `ExternalIdentityUnlinked` (yalnız `auth-service`); son-durum upsert, `occurredAt` korumalı; bilinmeyen sağlayıcı yok sayılır |
| Açık profil | `details.steam` yalnız etkin bağlantı + sahibin izni + `details` hakkı varsa |
| Hesap silme | Ek iş yok: profil FK'si `ON DELETE CASCADE` |
| Yeni ENV | **Yok.** `STEAM_WEB_API_KEY` ve Steam ad/avatar çekimi ilk sürümde yok (planın önerisi) |

## Tasarım notları

- Bağlama/kaldırma Auth'ta; User yalnız yansıtır. Kullanıcı kimlik yazamaz.
- Kaldırılan bağlantı satırı `active=false` kalır (kimlik NULL): sırası bozuk eski "bağlandı" olayı kaldırmayı geri alamaz.
- Olay yazımı native upsert (`version + 1`), kullanıcı izin güncellemesi JPA `@Version`: aradaki yarış `409` olur.
- Ayrıntı: [api.md → Bağlı hesaplar](../../docs/api.md), [NOTLAR](../../docs-security/user-service/NOTLAR.md).

## Doğrulama

| Kontrol | Sonuç |
| --- | --- |
| `mvn test-compile` | Derleniyor |
| Mevcut testler (`mvn test -pl services/user-service`) | **201 geçti**, V74 gerçek PostgreSQL'de uygulandı |
| `lint-ratchet check` | **Yeşil** (yeni ihlal yok) |
| Yeni testler | **Yazılmadı** (sona ertelendi) |

## Sonda yazılacak testler (toplu test fazı)

- **Olay girişi:** bağlandı → satır etkin, izin `false`; aynı kimlik tekrar → izin ve onay korunur; farklı kimlik → izin sıfırlanır;
  kaldırıldı → kimlik/izin silinir, satır `active=false`; eski "bağlandı" yeni "kaldırıldı"yı ezmez; bağlantı olmadan gelen
  "kaldırıldı" sonraki eski "bağlandı"yı engeller; profil yoksa profil açılır; geçersiz SteamID (`16/18 hane`, harf) `400`;
  başka hesapta etkin aynı kimlik `400`; `accountId != aggregateId` `400`; bilinmeyen sağlayıcı `200` ve etkisiz; yanlış üretici `400`;
  yinelenen `eventId` etkisiz.
- **Uçlar:** `GET` bağlı/bağlı değil; `PATCH` izin aç/kapat, `consentAt` davranışı, aynı değer idempotent (sürüm artmaz),
  bağlantı yok `404`, bilinmeyen sağlayıcı `404`, eski sürüm `409`, eksik/tanımsız alan `400`, token yok/servis token'ı `401`.
- **Açık profil:** `details.steam` yalnız izin açıkken; kapalıyken `null`; misafir (`DETAILED` olmadan) görmez; kaldırılınca kaybolur.
- **Silme:** `AccountErasureTest` tohumuna `linked_accounts` satırı eklenmeli (FK cascade).

## Açık işler

- T4: Auth'un bağlama/kaldırma ucu ve olayları; Steam ile kayıtlı mevcut hesaplar için yeniden yayın.
- Testler (yukarıdaki liste).
