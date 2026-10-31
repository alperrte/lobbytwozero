# Faz 03 kararları — LTZ-ALP-PERF-03

Tarih: 2026-10-31 · Kapsam: PERF-05..PERF-12 (CPU/GPU sıcaklık, CPU/GPU kullanım, RAM/VRAM, disk, fan RPM, FPS) · Migration: `V23__create_performance_samples.sql`

Faz 1 kararları (A + C: cihaz kimliği, token ve iptal Auth'tadır) geçerlidir ve yeniden açılmadı. Gerçek bir masaüstü sensör sözleşmesi yoktur; aralıklar ve kotalar **geçici varsayımdır** (bkz. [issues.md](../reports/issues.md) Faz 3 tablosu).

## KARAR-P3-01 — Örnekler mevcut sync ucuna eklenir; ikinci eşzamanlama sistemi yok

- Uç değişmedi: `POST /api/v1/performance/sync/batches` (aynı cihaz token'ı, aynı `Idempotency-Key`, aynı makbuz tablosu).
- `schemaVersion = 2` örnek taşır (`sessionId`, `samples`); `schemaVersion = 1` Faz 1 zarfıdır ve **olduğu gibi** çalışır (geriye uyumlu). Şemalar karışık alan taşıyamaz: v1'de `sessionId`/`samples`, v2'de `sampleCount` → 400.
- Neden: Master Prompt §7.1 "paralel ikinci sync sistemi kurma" der; makbuz, hız sınırı ve cihaz doğrulaması tek yerde kalır.

## KARAR-P3-02 — Örnek her zaman mevcut, çağıranın sahip olduğu bir oturuma bağlanır

- `sessionId` zorunludur; başkasının veya olmayan oturum aynı `404 PERFORMANCE_NOT_FOUND` döner (IDOR).
- Cihaz kaynaklı oturum açılmadı (`performance_sessions.source` hâlâ yalnız `MANUAL`): oturumu kullanıcı açar, cihaz yalnız ölçüm yazar. Kaynak ayrımı örnek düzeyinde `source='DEVICE'` ile tutulur ve sunucu atar. Faz 2'deki I-11 bu kararla kapanır (cihaz kimliği olmadan cihaz oturumu açmak Auth'a ihtiyaç duyar; ihtiyaç yok).
- Oturum durumu: `ACTIVE` (24 saatten eski değil) örnek kabul eder; `COMPLETED` oturum bitişten sonra 10 dk (END_GRACE) kabul eder; `ABANDONED` ve eski `ACTIVE` → 409 `SESSION_NOT_ACCEPTING_SAMPLES`.

## KARAR-P3-03 — Uzun biçim (long format) tablo: `performance_samples`

- Satır = (oturum, metrik, an, değer, birim). Yeni metrik eklemek şema değişikliği gerektirmez; ölçülmeyen metrik için satır hiç yazılmaz (**hiçbir zaman 0 ile doldurulmaz**).
- `UNIQUE(session_id, metric, captured_at)`; `INSERT ... ON CONFLICT DO NOTHING`. Aynı noktaya ikinci yazım **ilk değeri korur** ve `duplicateSamples`'a sayılır; farklı idempotency anahtarıyla yeniden gönderim çoğaltma yaratmaz.
- Salt eklenen (append-only) tabloda `version` yoktur; PK uuid, zaman `timestamptz` (UTC), metrik/birim/kaynak `varchar` + `CHECK`.
- Oturum silinince `ON DELETE CASCADE`; `erase_performance_account` örnekleri de siler. Başka servise FK yoktur.

## KARAR-P3-04 — Hep-ya-hiç (all-or-nothing) ve makbuz

- Herhangi bir örnek geçersizse hiçbir örnek yazılmaz ve makbuz oluşmaz; aynı anahtar düzeltilmiş içerikle yeniden kullanılabilir.
- Aynı `Idempotency-Key` + aynı içerik → ilk sonuçla `replayed=true` (yeni yazım yok). Aynı anahtar farklı içerik → 409 `IDEMPOTENCY_KEY_REUSED`.
- Makbuz `session_id`, `stored_count`, `duplicate_count` taşır (denetlenebilir). **Takas:** oturum sonradan silinse bile tekrar (replay) ilk sonucu döndürür (örnek yazmadan); sızıntı yok, çünkü yanıt yalnız sayaçlardır.
- Cihaz başına canlı makbuz sınırı 500 → **1500** yapıldı: 500 örnekli gönderim tavanıyla 1 saatlik oturum için makbuz sınırı yeterli kalsın diye. Ayar geçicidir.

## KARAR-P3-05 — Metrikler: kanonik büyük harf, tek birim, istemci birim seçmez

`CPU_TEMP_C`, `GPU_TEMP_C`, `CPU_USAGE_PERCENT`, `GPU_USAGE_PERCENT`, `RAM_USED_MB`, `VRAM_USED_MB`, `DISK_READ_MBPS`, `DISK_WRITE_MBPS`, `FAN_RPM`, `FPS`.

- Küçük harf/takma ad dönüşümü yok (`cpu_temp_c` → 422 `SAMPLE_METRIC_UNSUPPORTED`); desteklenmeyen metrik sessizce düşürülmez, reddedilir.
- **FAN_RPM belirsizliği:** Hangi fanın (CPU/GPU/kasa) ölçüldüğü sözleşmede yok. Tek `FAN_RPM` kullanıldı; birden çok fan ayrımı gerekirse yeni metrik veya `sensor` alanı için ayrı migration + ekip kararı gerekir.
- **Disk:** PERF-10 "kullanım ve performans" der; yalnız okuma/yazma verimi (MB/s) alındı. Disk doluluk/gecikme için sözleşme yoktur.

## KARAR-P3-06 — Geçici geçerlilik aralıkları

| Metrik | Aralık | Not |
|---|---|---|
| `CPU_TEMP_C`, `GPU_TEMP_C` | 0 < v ≤ 150 | 0 = ölçülememiş → reddedilir |
| `CPU_USAGE_PERCENT`, `GPU_USAGE_PERCENT` | 0 ≤ v ≤ 100 | 0 geçerli |
| `RAM_USED_MB` | 0 ≤ v ≤ 1.048.576 | 1 TiB tavanı |
| `VRAM_USED_MB` | 0 ≤ v ≤ 262.144 | 256 GiB tavanı |
| `DISK_READ_MBPS`, `DISK_WRITE_MBPS` | 0 ≤ v ≤ 100.000 | |
| `FAN_RPM` | 0 ≤ v ≤ 30.000 | 0 = durmuş fan, geçerli |
| `FPS` | 0 < v ≤ 1000 | 0 = ölçülememiş → reddedilir |

NaN/sonsuz ve negatif her zaman reddedilir. Aynı sınırlar DB `CHECK` ile de uygulanır (uygulamayı atlayan yazım için son savunma).

## KARAR-P3-07 — Sayısal değer katı ayrıştırılır

- `value` yalnız JSON **sayısı** olabilir. İlk uygulamada Jackson `"60"` (metin) değerini sessizce `60.0` yapıyordu ve saklanıyordu (testle yakalandı). `StrictNumberDeserializer` ile metin/bool/null/dizi/nesne → 400 `INVALID_REQUEST`.
- `1e999` gibi taşan değer → aralık dışı 422 `SAMPLE_VALUE_INVALID`.

## KARAR-P3-08 — Zaman penceresi ve sıralama

- `capturedAt` milisaniyeye kesilir. Pencere: `oturum başlangıcı − 5 dk` .. `min(şimdi + 5 dk, oturum bitişi + 5 dk)`. Gelecek/pencere dışı → 422 `SAMPLE_TIME_INVALID` (`sampleIndex` ile).
- Sırasız gönderim kabul edilir (sıra anlamsızdır, saklama `captured_at` ile indekslenir).
- `clientSentAt` −24 sa .. +5 dk olmalıdır (Faz 1 kuralı korunur).

## KARAR-P3-09 — Kotalar (ayarlanabilir sabitler)

| Sınır | Değer | Aşılırsa |
|---|---|---|
| Gönderim başına örnek | 500 | 400 |
| Gövde üst sınırı (yalnız sync yolu) | 64 KiB (diğer uçlar 16 KiB) | 413 |
| Oturum başına örnek | 100.000 | 409 `SESSION_SAMPLE_LIMIT_REACHED` |
| Hesap başına örnek | 500.000 | 409 `USER_SAMPLE_QUOTA_REACHED` |

- Kota kontrolü hesap advisory kilidi altında yapılır (eşzamanlı gönderimlerle aşılamaz; testle doğrulandı).
- Bu sayılar ölçüm yoğunluğuna (örn. 10 metrik × 1 Hz × 3 saat ≈ 108k) göre seçilmiş geçici değerlerdir; gerçek istemci sözleşmesiyle yeniden değerlendirilmelidir.

## KARAR-P3-10 — Silme

- `DELETE /api/v1/performance/sessions/{id}/samples` → 204: oturumun örneklerini siler, `sampleCount` 0'a döner; oturum ve rapor korunur; idempotent; yalnız sahibi (kullanıcı token'ı; cihaz token'ı 401).
- Zorunlu saklama süresi/otomatik temizlik **eklenmedi** (açık risk, bkz. issues.md): veri kullanıcı silene veya hesap silinene kadar kalır. Bölümleme (partitioning) eklenmedi.

## Reddedilen alternatifler

- Geniş (wide) satır (oturum başına bir satırda 10 sütun): seyrek metriklerde NULL/0 doldurma baskısı yaratır, yeni metrik şema değişikliği ister.
- Ayrı yeni uç (`/sync/samples`): ikinci sync sistemi; makbuz/limit/doğrulama çoğalır.
- İstemcinin birim göndermesi: birim karışıklığı ve sahtecilik yüzeyi.
