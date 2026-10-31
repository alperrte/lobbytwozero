# Faz 04 kararları — LTZ-ALP-PERF-04

Tarih: 2026-10-31 · Kapsam: PERF-14 (geçmiş), PERF-15 (istatistik), PERF-16 (grafik API'si), PERF-19 (filtreleme), PERF-21 (Hardware/Recommendation sözleşmeleri) · Migration: **yok** (V24 kullanılmadı; mevcut `uq_performance_samples_point (session_id, metric, captured_at)` ve oturum indeksleri yeterli).

Faz 1 kararları (A + C) geçerlidir. Bu fazın tüm uçları salt okunurdur; başka servisin veritabanına sorgu yoktur.

## KARAR-P4-01 — Mevcut `GET /sessions` genişletildi; ayrı "geçmiş" ucu yok

- PERF-14 geçmişi zaten oturum listesidir. Filtreler (`gameId`, `hardwareProfileId`, `deviceLinkId`, `status`, `startedFrom`, `startedTo`) eklemeli (additive) ve isteğe bağlıdır; mevcut istemciler bozulmaz. Yanıta `totalElements` eklendi.
- Filtreler `Specification` ile birleşir (AND); `user_id` koşulu her zaman ilk koşuldur (IDOR).
- Sıra deterministiktir: `startedAt DESC, id DESC` (eşit başlangıçta sayfalar kayıp/mükerrer vermez; testle kanıtlı).

## KARAR-P4-02 — Zaman sınırları yarı açık, adlandırma açık

- Oturum filtresi `startedFrom`/`startedTo` (oturum **başlangıcı**); örnek uçları `from`/`to` (**`captured_at`**). Aralık `[from, to)`: alt sınır dahil, üst sınır hariç. `from < to` zorunlu, sınırlar 2000-01-01..2200-01-01 (aşırı değerler 400).
- Geçersiz tarih, bilinmeyen metrik/durum, sayfa/boyut sınırı ihlali → 400 `INVALID_REQUEST`.

## KARAR-P4-03 — Ham örnek okuma: sabit sıra, sınırlı sayfa

- `GET /sessions/{id}/samples`: sıra `captured_at, metric, id`; `size` 1..500 (varsayılan 100). `hasNext`, `size + 1` satır okunarak bulunur (toplam sayım sorgusu yok).
- Yanıt iç kimlik (örnek id, cihaz bağı, parti) taşımaz; yalnız `metric, unit, capturedAt, value`.

## KARAR-P4-04 — İstatistik yalnız gerçek örneklerden; sıfır doldurma yok

- `avg/min/max/count` SQL'de gerçek satırlardan hesaplanır. Örneği olmayan metrik listede yer almaz; örneksiz oturum `metrics=[]`.
- Hesap istatistiği **örnek ağırlıklıdır**, oturum ortalamalarının ortalaması değildir. `sessionCount` örneksiz oturumları da sayar; metrik düzeyinde `sessionCount` o metrikten örneği olan oturumlardır. Birim sunucuda metrikten atanır, istemci birim seçmez.

## KARAR-P4-05 — Grafik serisi: ham ya da açıkça belirtilmiş kova ortalaması

- `maxPoints` 10..1000 (varsayılan 300). Pencerede `count <= maxPoints` ise `RAW` (her nokta gerçek örnek). Aksi halde pencerenin **gerçek** ilk/son zamanı arasında eşit genişlikli kovalar: `bucketMillis = max(1, ceil((span + 1) / maxPoints))`; nokta sayısı `maxPoints`'i asla aşmaz.
- Her kova `avg/min/max/count` taşır; `at` kova başlangıcıdır; yanıt `downsampled=true`, `aggregation=AVG_MIN_MAX`, `bucketMillis` ile indirgemeyi açıkça bildirir. Kovaların `count` toplamı `totalSamples`'a eşittir (veri kaybı yok). Boş kovalar yazılmaz (aralık uydurulmaz).
- Üst sınır sayım sorgusundaki `last` değerine sabitlenir; sayım ile okuma arasında gelen örnek yanıtı `maxPoints`'in üstüne taşıramaz.
- **Neden eşit genişlikli kova (LTTB vb. değil):** deterministik, tek SQL, indeksten yürür, ortalama/min/max ile uç değerler korunur; ek kütüphane gerekmez.
- Sorgular `uq_performance_samples_point` indeksini kullanır; testte `EXPLAIN` ile `Seq Scan` olmadığı doğrulanır (küçük tabloda planlayıcı seq scan seçebileceğinden `enable_seqscan=off` altında).

## KARAR-P4-06 — Servis sözleşmeleri: hesap kapsamlı, tamamlanmış oturum, en az ayrıcalık

- İki ayrı uç, iki ayrı servis kimliği, iki ayrı kapsam (ortak "tümünü okur" kapsamı yok):
  - `GET /internal/v1/accounts/{accountId}/performance-summary?hardwareProfileId=` → `sub=svc:hardware-service`, `scope=summary:read`.
  - `GET /internal/v1/accounts/{accountId}/games/{gameId}/performance-reference[?hardwareProfileId=]` → `sub=svc:recommendation-service`, `scope=reference:read`.
- Mevcut D4 deseni korunur: tek `aud=performance-service`, `jti`, ≤ 60 sn ömür, `account_id` iddiası yoldaki `accountId` ile eşleşmek zorunda. Gateway bu yolları dışarı açmaz; kullanıcı/cihaz token'ı 401.
- Yalnız `COMPLETED` oturumlar sayılır (devam eden/terk edilen ölçüm referans olmaz).
- Hardware yalnız özet alır: oturum/oyun/cihaz kimliği, oyun adı, ham örnek **yok**. Recommendation yalnız FPS referansı alır.
- **Hesaplar arası toplama yok.** Recommendation'ın "bu oyun tüm oyuncularda kaç FPS" ihtiyacı varsa bu, başka kullanıcıların verisini ve gizlilik kararını gerektirir; ayrı karar (ekip/DPO) olmadan yapılmadı (I-25).

## KARAR-P4-07 — Güvenilirlik eşiği: ≥ 60 FPS örneği (VARSAYIM)

- Recommendation, dayanaksız tek örneği "referans" saymasın diye `fpsSampleCount < 60` ise `reliable=false` ve `avgFps/minFps/maxFps = null` döner; sayım ve `lastMeasuredAt` yine verilir.
- 60, sistemin dayandığı bir sözleşme değildir (varsayım); Recommendation ihtiyacı netleşince ayarlanmalı (I-24).

## KARAR-P4-08 — Servis kapsam adları Auth biçimine uyar

- Auth `ServiceTokenIssuer` kapsamı `^[a-z]+:[a-z]+$` ile doğrular. İlk taslak `performance:summary:read` bu desenle **üretilemez** olurdu; kapsamlar `summary:read` ve `reference:read` olarak seçildi (testte gerçek Auth `ServiceTokenIssuer` ile kapsam biçimi uyumu doğrulandı).
- Auth bugün her servis token'ında `sub=svc:auth-service` üretir (REQ-01-C); bu yüzden gerçek Auth token'ı iki uçta 403 alır ve uçtan uca çalışma **BLOCKED**'dır (REQ-04-A).

## KARAR-P4-09 — Okuma uçlarında hız sınırı yok (şimdilik)

- Yazma uçları `PerformanceWriteLimits` ile sınırlıdır; okuma uçları sınırsızdır. Her sorgu `maxPoints` / `size` / hesap kotasıyla sınırlı iş yapar, ancak ağır hesap istatistiği tekrarlı çağrılabilir. Takas olarak kabul edildi, I-26/I-27'de izleniyor.
