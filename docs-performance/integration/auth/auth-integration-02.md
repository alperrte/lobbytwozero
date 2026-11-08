# Auth entegrasyonu 02 — Oturum ve rapor uçları

Güncelleme: 2026-11-06 · Kapsam: PERF-03/04/13/20 · Güncel Auth durumu: [auth-integration-01.md](auth-integration-01.md).

## 1. Durum özeti

| Konu | Durum |
| --- | --- |
| Faz 2 uçları (10 adet) kullanıcı token'ıyla çalışır | Kod + test hazır |
| Kullanıcı token'ı doğrulaması gerçek Auth **kaynak kodundan** üretilen JWKS ile test edildi | **Kaynak sözleşme testi**; çalışan Auth ile E2E değildir |
| Çalışan (DEV) Auth ile uçtan uca oturum/rapor akışı | **NOT RUN** — bu fazda yapılmadı |
| Cihaz kaynaklı örnekler | Faz 3'te uygulandı; REQ-01-A Auth'ta hazır, kaynak sözleşme testleri gerçek Auth üreticisini kullanır |
| Yeni Auth talebi | **Yok** ([auth-requests-02.md](../auth-service-talepleri/auth-requests-02.md)) |

Auth Service kaynak koduna dokunulmadı. Sahte JWT, `permitAll` veya varsayılan izin yoktur; `anyRequest().denyAll()` geçerlidir.

## 2. Sahiplik karşılaştırması

| Uç | Token türü | Sahiplik kuralı | Başkasının kaynağı |
| --- | --- | --- | --- |
| POST /sessions | Kullanıcı | `user_id = token.sub` yazılır; `hardwareProfileId` Hardware'de çağıranın Bearer'ıyla, `deviceLinkId` kendi `device_links` kaydıyla doğrulanır | `HARDWARE_PROFILE_NOT_FOUND` / `DEVICE_LINK_NOT_FOUND` (422) |
| GET /sessions, /sessions/active | Kullanıcı | `WHERE user_id = :sub` | Hiç görünmez |
| GET /sessions/{id} | Kullanıcı | `WHERE id = :id AND user_id = :sub` | 404 `PERFORMANCE_NOT_FOUND` |
| POST /sessions/{id}/end | Kullanıcı | aynı | 404, veri değişmez |
| DELETE /sessions/{id} | Kullanıcı | aynı | 404, veri değişmez |
| DELETE /sessions?confirm=true | Kullanıcı | `WHERE user_id = :sub` | Başka hesaplara etkisi 0 |
| PUT/GET/DELETE /sessions/{id}/report | Kullanıcı | oturum sahipliği + `performance_reports.user_id` | 404, veri değişmez |

Olmayan ve başkasına ait kaynak **aynı** 404'ü döner (varlık sızmaz). `userId` istekten hiçbir zaman alınmaz; yalnız token'ın `sub` iddiasından gelir (`CurrentActor.requireUserId()`).

## 3. Cihaz kaynaklı ve manuel oturum farkları

| Özellik | Manuel oturum | Cihaz kaynaklı örnek (Faz 3, uygulandı) |
| --- | --- | --- |
| Kimlik | Kullanıcı token'ı (`aud=ltz-api`) | Cihaz token'ı (`aud=performance-service`, Auth `target=performance`) |
| Başlatan | Kullanıcı, web/uygulama üzerinden | Kullanıcının açtığı oturuma cihaz örnek gönderir |
| `source` | Oturumda `MANUAL` | Örneklerde `DEVICE`; oturum `MANUAL` kalır |
| Zaman | Sunucu saati | Sunucu kabul zamanı + istemci örnek zamanı (Faz 3 kuralı) |
| Ölçüm | Rapora kullanıcı elle yazar | Örnekler cihazdan gelir (PERF-05..12) |
| İzin | Ek izin gerekmez | Açık `sync_consents` + aktif `device_link` |
| `deviceLinkId` | İsteğe bağlı, kullanıcı kendi aktif bağlantısını bağlayabilir | Zorunlu (cihaz kimliğinden çözülür) |

Kullanıcı token'ıyla bağlanan `deviceLinkId` yalnız ilişkilendirmedir; cihaz yetkisi vermez ve cihaz token'ı yerine geçmez.

## 4. Negatif Auth testleri (`PerformanceSessionIntegrationTest`)

Aşağıdakiler PASS (PostgreSQL Testcontainers + gerçek güvenlik filtre zinciri; Auth üretim kaynağından derlenen imzalama/JWKS; çalışan Auth yok):

- Anonim istek: 10 rotanın tamamı 401.
- Cihaz token'ı (`at+jwt` dışı/yanlış audience): oturum uçlarında 401.
- Tanımsız alt yol: 403 (`denyAll`).
- IDOR: başkasının oturumuna get/end/delete/report put-get-delete → 404, veri bozulmadı; toplu silme başka hesabı etkilemedi.
- Hesap silme (Auth D4) sonrası yalnız hedef hesap temizlenir.
- İstemci tarafından gönderilen `userId`/`status`/`startedAt`/`source` → 400.

## 5. Dürüstlük notları

- `PerformancePeerDoubles` Game GAME-15 ve Hardware HW-17 için **yerel mock**tur; gerçek servis entegrasyonu değildir. Hardware'in tüketici tarafı `DEFERRED_CONTRACT / NOT_VERIFIED`.
- Oturum/rapor testleri çalışan Game ve Hardware servisleriyle uçtan uca kabul anlamına gelmez.
- Auth cihaz akışının ayrı gerçek HTTP kabul kanıtı [auth-integration-05.md](auth-integration-05.md) içinde tutulur; bu, Game/Hardware oturum entegrasyonunu doğrulamaz.
