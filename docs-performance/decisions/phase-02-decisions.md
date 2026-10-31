# Faz 02 kararları — LTZ-ALP-PERF-02

Tarih: 2026-10-31 · Kapsam: PERF-03, PERF-04, PERF-13, PERF-20 · Migration: `V22__create_performance_sessions_reports.sql`

Faz 1 kararları (A + C: cihaz kimliği, token ve iptal Auth'tadır) geçerlidir ve yeniden açılmadı.

## KARAR-P2-01 — Oturum yalnız MANUAL ve zamanı yalnız sunucu belirler

- İstemci `startedAt`, `endedAt`, `status`, `userId`, `source` gönderemez (`fail-on-unknown-properties=true` → 400).
- `source` CHECK ile `MANUAL`'a kilitlidir. Cihaz kaynaklı (`DEVICE`) oturum Faz 3'ün işidir; gerçek cihaz token'ı (REQ-01-A) gelmeden eklenmedi.
- Neden: istemci saatine güvenmek süre/istatistik sahteciliğine açıktır; cihaz kaynağı gerçek Auth olmadan doğrulanamaz.

## KARAR-P2-02 — Kullanıcı başına tek ACTIVE oturum

- Kural iki katmanlıdır: uygulama kontrolü (anlaşılır 409 `ACTIVE_SESSION_EXISTS`) ve veritabanı kısmi benzersiz indeks `uq_performance_sessions_active_user (user_id) WHERE status='ACTIVE'`.
- Eşzamanlı başlatmada (8 iş parçacığı testi) yalnız biri 201, kalanı 409 alır; DB indeksi son savunma hattıdır.

## KARAR-P2-03 — Terk edilmiş oturum

- 24 saatten uzun süredir `ACTIVE` kalan oturum bir sonraki başlatma/okumada `ABANDONED` yapılır; `endedAt = startedAt + 24 sa`.
- Neden: kapatılmayı unutulan oturum kullanıcıyı sonsuza dek kilitlemesin; süre uydurulmaz, üst sınıra kırpılır.
- Arka plan zamanlayıcısı eklenmedi (ek bileşen/ENV gerekmez); durum geçişi tembeldir (lazy).

## KARAR-P2-04 — Bitirme idempotent, çelişkili bitirme 409

- Aynı bitiş durumuyla tekrar `end` → 200, kayıt ve `version` değişmez.
- Farklı sonuçla bitirme (ör. COMPLETED → ABANDONED) → 409 `SESSION_ALREADY_ENDED`; mevcut sonuç ezilmez.
- Bitirme gövdesi opsiyoneldir; varsayılan `COMPLETED`; `ACTIVE` geçersizdir.

## KARAR-P2-05 — Eşzamanlılık: hesap advisory lock + iyimser kilit

- Bitir/sil/başlat işlemleri `PerformanceAccountAccessGuard.requireActive(userId)` ile hesap düzeyinde sıralanır (Faz 1 mekanizması). Bitirme–silme yarışında sonuç ya "bitti sonra silindi" ya "silindi, bitirme 404"tür; yarım durum oluşmaz.
- `@Version` iyimser kilit ek güvencedir; iki ayrı kopya üzerinden çakışma `ObjectOptimisticLockingFailureException` ile kanıtlandı.

## KARAR-P2-06 — Rapor: yalnız COMPLETED oturum, oturum başına tek rapor, idempotent PUT

- `PUT /sessions/{id}/report`: ilk yazım 201, aynı değerle tekrar 200 (kayıt değişmez), farklı değerle 200 (tek satır güncellenir). `UNIQUE(session_id)`.
- `ACTIVE`/`ABANDONED` oturumda rapor → 409 `SESSION_NOT_COMPLETED`. Neden: yarım/terk oturumun özeti güvenilmezdir.
- Çözünürlük ve grafik ön ayarı rapordadır (oturumda değil): PERF-13 "oyun, donanım, çözünürlük ve grafik ayarlarıyla ilişkili rapor" ifadesi.

## KARAR-P2-07 — Ölçülmemiş veri uydurulmaz

- Ölçüm alanları `NULL` olabilir; sistem sensör/FPS değeri doldurmaz. En az bir ölçüm zorunludur (`REPORT_EMPTY`, DB CHECK `ck_performance_reports_metric_present`).
- Yalnız not + çözünürlük + ön ayar içeren rapor reddedilir (422). `p1LowFps > avgFps` → 422 `REPORT_INCONSISTENT`.
- Neden: Master Prompt §6 "otomatik doldurma yok"; ölçüm olmadan rapor anlamsızdır.

## KARAR-P2-08 — Silme ve gizlilik modeli

- Oturum silme → rapor CASCADE ile silinir (yetim rapor kalmaz). Rapor silme → oturum korunur.
- `DELETE /sessions?confirm=true` yalnız çağıranın kayıtlarını siler; `confirm` zorunludur (yanlışlıkla toplu silmeyi önler); ikinci çağrı `0` döner.
- `device_links → performance_sessions` FK `RESTRICT`: oturumu olan bağlantı fiziksel silinmez, `DISABLED` kalır. Hesap silme (`erase_performance_account`, V22'de güncellendi) önce oturumları, sonra bağlantıları siler.
- Başkasının veya olmayan kaynak her zaman aynı 404 (`PERFORMANCE_NOT_FOUND`): kaynak varlığı sızmaz (IDOR).

## KARAR-P2-09 — Oyun ve donanım doğrulaması yalnız servis sözleşmeleriyle

- Oyun: Game GAME-15 `POST /api/v1/game/games/batch`; oyun adı gösterim kopyası olarak saklanır. Oyun yoksa 422 `GAME_NOT_FOUND`.
- Donanım profili: Hardware HW-17 `POST /api/v1/hardware/profiles/batch`; **çağıranın kendi kullanıcı Bearer'ı iletilir** (sahiplik Hardware'de çözülür). Profil yok/başkasının → 422 `HARDWARE_PROFILE_NOT_FOUND`.
- Başka servisin DB'sine erişilmez; `game_id`/`hardware_profile_id` REF'tir (FK yok).
- Dış çağrı **transaction dışında** yapılır (Hikari havuzu 3; havuz tükenmesi ve kilit tutma riski), yeniden deneme yoktur.
- **Kapalı başarısızlık:** zaman aşımı, 4xx/5xx, bozuk veya sözleşme dışı gövde → kayıt açılmaz, 503 + `Retry-After: 30`. "Servis yok = oyun yok" yorumu yapılmaz; ne `items` ne `unavailable` içinde olmayan oyun yanıtı da 503'tür.
- Hardware tüketici tarafı gerçek servisle doğrulanmadı (HW-17 E2E `DEFERRED_CONTRACT / NOT_VERIFIED`); testlerde yerel sapma (mock) kullanıldı ve belgelerde böyle etiketlendi.
- Yeni ENV anahtarı eklenmedi; mevcut `GAME_SERVICE_URL` ve `HARDWARE_SERVICE_URL` kullanılır.

## KARAR-P2-10 — Sınırlar

- Hesap başına en çok 1000 saklanan oturum (409 `SESSION_LIMIT_REACHED`). Neden: silme fonksiyonunun tek geçişte bitmesi ve sınırsız büyümenin önlenmesi.
- Yazma hız sınırı: `session-user:<uuid>` 60/dk + ortak adres kovası 120/dk (Faz 1 `performance_write_limits` altyapısı).
- Liste sayfası en çok 50; gövde 16 KiB; `notes` ≤ 500 kod noktası ve kontrol karakterlerinden arındırılır.

## Reddedilen alternatifler

- İstemci zamanı kabul etmek (sahtecilik riski).
- Raporu oturum tablosuna gömmek (tek sorumluluk ve ayrı silme ihtiyacı).
- Game/Hardware yanıtı gelmezse kaydı yine de açıp sonra doğrulamak (doğrulanmamış veri; kapalı başarısızlık seçildi).
- Hardware doğrulaması için servis token'ı kullanmak (REQ-01-C bloklu; sahiplik kullanıcı Bearer'ıyla Hardware'de çözülür).
