# PR Title

```text
feat(performance-web): add performance dashboard and privacy controls (PWEB-01–07,10)
```

# PR Description

## Summary

Mevcut Next.js uygulamasına LTZ Performance tanıtımını, bağlı cihaz görünümünü, Hardware profillerine dayalı donanım panelini, kayıtlı oturum/grafik/rapor görünümünü ve onaylı gizlilik/veri silme akışlarını ekler. Gerçek olmayan ölçüm, indirme dosyası, uyumluluk sonucu veya öneri üretmez.

**Kısmi teslim: 8/10 PWEB.** PWEB-08/09 için Recommendation iş API/sözleşmesi bulunmuyor; bu iki özellik tamamlanmış olarak sunulmuyor.

## Scope / PDA Tasks

- [x] LTZ-ALP-PWEB-01 — Performance Tanıtım ve Cihaz Yönetimi Arayüzü (PWEB-01–03), kullanıcı commit'i `df308dfa`
- [x] LTZ-ALP-PWEB-02 — Donanım ve Performans Paneli (PWEB-04–07), kullanıcı commit'i `5cfe014f`
- [ ] LTZ-ALP-PWEB-03 — Uyumluluk, Öneriler ve Veri Gizliliği Ekranları (PWEB-08–10): PWEB-10 uygulandı; PWEB-08/09 BLOCKED

## Implemented Changes

### Task 1

- `/tr/performans` ve `/en/performance`: ürünün gerçek mevcut yetenekleri, MVP sonrası masaüstü istemcisi açıklaması ve pasif indirme alanı.
- `DeviceList`: mevcut `GET /api/v1/performance/devices`, misafir/boş/yükleniyor/hata/yeniden dene; hesap değişiminde eski veriyi gizleme.
- Mevcut sidebar/footer/page kataloğu ve TR/EN yönlendirme altyapısıyla bütünleşme.

### Task 2

- `/tr/performans/panel`, `/en/performance/dashboard`; mevcut AppShell/Auth katmanı, noindex ve sitemap dışı özel ekran.
- `HardwareSnapshot`: çoklu bilgisayar/bileşen sayfalaması, kaynak, nullable özellikler, opsiyonel marka ve gerçek gizlilik durumu.
- `SessionHistory`: owner geçmişi, 20 satırlık sayfa, durum filtresi; yalnız seçilen oturum için rapor/grafik isteği.
- `SessionMeasurements`/`MeasurementChart`: 10 metrik, tam/ilk 5/ilk 30 dakika, maxPoints=100, RAW/BUCKET açıklaması, SVG + erişilebilir tablo; eksik değer sıfır olmaz.
- `SessionReport`/`HardwareProfileReference`: manuel rapor kaynağı, çözünürlük/ayar/ölçümler, gerçek ilgili bilgisayar; tahmin ve ölçüm ayrımı.

### Task 3

- `PerformancePrivacy`: mevcut GET/PUT/DELETE senkronizasyon izni; açık onay ve geri alma; profil görünürlüğü için mevcut ayarlara erişim.
- `DeletePerformanceData`: tekli veya toplu oturum silme; bağlı örnek/raporların mevcut backend CASCADE sözleşmesi; cihaz/donanım/izinlerin korunduğunu açıklama.
- `usePerformanceMutation`: duplicate ref kilidi, unmount iptali; kesinleşen yanıttan sonra başarı; belirsiz sonuçta tekrar mutasyondan önce güncel veri kontrolü.
- Kullanılamayan uyumluluk/öneri kabiliyetleri için dürüst açıklama; hayali endpoint veya frontend öneri algoritması yok.
- Mevcut Hardware E2E CSRF fixture ve misafir/shell navigasyon test beklentileri düzeltildi. Üretilen `coverage/**` çıktıları mevcut ESLint build ignore listesine eklendi; kaynak kuralları korunuyor.

## Service Integrations

- **Auth Service:** mevcut SessionProvider/useRequireSession, HttpOnly cookie, ortak refresh, Gateway cookie→Bearer ve double-submit CSRF kullanılır. Yeni Auth talebi gerekmedi; Auth kodu/ENV/güven modeli değiştirilmedi.
- **Hardware Service:** mevcut profiles/detail/privacy API'leri public frontend feature export'larıyla tüketilir; backend/DTO/DB/migration değişmedi.
- **Performance Service:** devices, sessions, series, report, sync-consent ve owner single/bulk DELETE uçları tüketilir. `docs/api.md` yalnız PERFORMANCE bölümünde dokuz mevcut endpoint'in PWEB consumer durumu güncellendi; HTTP sözleşmesi değişmedi.
- **Recommendation / Game Service:** Recommendation yalnız uygulama iskeleti; gerçek uyumluluk/öneri sözleşmesi yok. Game veya başka servisin koduna müdahale edilmedi. PWEB-08/09 ayrı Recommendation geliştirmesi gerektiriyor.

## Security, Privacy & Accessibility

- Özel consumer'lar yalnız doğrulanmış frontend session durumunda mount edilir; hesap değişince alt ağaç yenilenir. Bu UX önlemleri backend yetki kontrolünün yerine geçmez.
- Sunucu kimliği cookie/JWT/Gateway'de kalır; istemciden userId/X-User-Id güven kaynağı olarak gönderilmez, token saklanmaz.
- no-store, timeout/abort, encode edilmiş kaynak segmentleri, onaylı mutasyon ve CSRF; belirsiz yanıtta kör retry yok.
- Silme başarısızsa yerel kayıtlar başarı gibi kaldırılmaz. Açık CSRF reddi bir kez yenilenir; oturum yenileme mevcut ortak client davranışıdır.
- TR/EN, light/dark, reduced-motion, klavye, gerçek boş/hata durumları, grafik tablo alternatifi ve 375/768/1440 px kontrolleri. Tam WCAG sertifikasyonu iddia edilmez.
- Fixture E2E canlı Auth/Gateway/servis kabulü değildir. Backend owner/IDOR gerçek test sonucu final kalite raporunda ayrıca belirtilir.

## Validation

| Check | Result | Evidence |
| --- | --- | --- |
| Lint / TypeScript / Build | PASS | Standart npm scriptleri, exit 0; ENV dosya okumaları build/E2E test preload'unda kapalı |
| Unit / Component | PASS | 161 dosya, 1563 PASS, mevcut 1 SKIP |
| Feature coverage | PASS | 55 hedefli test; %97,46 line / %83,62 branch, yalnız belirtilen özellik kapsamı |
| Performance E2E | PASS | 10/10, Chromium; silme/izin + önceki panel regresyonları |
| Tüm E2E | FAIL / FLAKY | Son tam koşu 305 PASS/1 FAIL/56 mevcut SKIP; Navbar Ctrl+K odak senaryosu izole tekrarda 1/1 PASS; temiz full PASS iddia edilmez |
| Pre-push | FAIL | Gerçek `pre-push.cmd full dev`, exit 1/NOT SAFE TO COMMIT. İlk eksik Gitleaks giderildi, ayrı secret kontrolleri PASS; mevcut Hardware kalite gate bulguları kaldı |
| Backend tests / owner / IDOR | PASS | 18 modül 4270 test, 0 failure/error/skip; Performance 138 test/%97,10 line, owner oturum paketi 32/32; build/coverage/18 HTTP health PASS |
| SonarQube scan / Quality Gate | FAIL | Gerçek analiz yüklendi/işlendi; `ltz-backend`, CE `d72330be-eb5d-4370-875e-e60c9e4bc2e6`, gate FAILED, runner exit 1; frontend scan BLOCKED |
| Sonar Security / Reliability / Maintainability | C / C / A | Sonar coverage %90,4, duplication %3,3; 13 açık BUG/VULNERABILITY kaydı; mevcut backend kaynakları owner triage gerektiriyor |

Tam ve son sonuç otoritesi: `docs-performance/performance-web/testings/pre-merge-quality.md`.

## Risks / Known Limitations

- PWEB-08/09 Recommendation iş API/sözleşmesi olmadığı için BLOCKED.
- Canlı tarayıcı Auth/Gateway/Hardware/Performance kabulü NOT VERIFIED; fixture testleri yalnız consumer davranışını doğrular.
- Masaüstü ölçüm/eşleştirme/indirme MVP sonrasıdır. Senkronizasyon izni kaydetmek masaüstü oturumu başlatmaz.
- Mevcut Sonar runner frontend taraması sağlamıyor; bu sınır giderilmeden frontend Quality Gate/A doğrulaması iddia edilemez.
- Son tam tarayıcı koşusunda Navbar Ctrl+K focus hatası kaldı; izole tekrarında geçti. Önceki shell/guest/CSRF fixture hataları gerçek sözleşmelere göre düzeltildi. Test skip veya üretim güvenliği gevşetme yapılmadı.
- Pre-push ratchet'i Hardware'ın beş mevcut katalog/Icecat/depolama dosyasında altı kural satırı/sekiz baseline-üstü ihlal buldu. Başlangıç HEAD'inden beri backend kaynak diff'i yoktur (`git diff --quiet 085af157 -- backend`: 0); kapsam dışı refactor veya baseline büyütme yapılmadı.
- Güncel backend Sonar gate FAILED, C/C/A. Başka servis sahiplerinin koduna veya cookie/SQL güven modeline dokunulmadı; yeni issue/coverage koşulları ve exclusion'lar gevşetilmedi. Code smell/hotspot toplamları UNKNOWN; anonim ek ölçüm erişimi 401, mevcut runner bu toplamları yazdırmıyor.

## Review / Merge Checklist

- [ ] 10 PWEB gereksinimi kod + test + belge olarak doğrulandı — 8/10; PWEB-08/09 BLOCKED
- [ ] Kullanıcı sahipliği/Auth için canlı uçtan uca kabul tamamlandı
- [ ] Pre-push başarıyla geçti
- [ ] SonarQube frontend dahil Quality Gate PASS ve A hedefleri doğrulandı
- [x] Üç taska ait ayrı commit taslakları `reports/commit-messages.md` içinde; ilk iki commit kullanıcıya ait
- [x] Uygulanmamış backend/desktop/ölçüm özellikleri yapılmış gibi gösterilmedi
- [x] Bu çalışma sırasında otomatik git add/commit/push/PR/merge ve Git kimliği değişikliği yapılmadı

**Merge readiness: NOT READY FOR MERGE** — PWEB-08/09 bağımlılıkları, başarısız pre-push kalite gate'i, kararsız tam E2E, eksik frontend Sonar kapsamı ve canlı tarayıcı kabulünün doğrulanmamış olması.
