# TASK 2 — LTZ-ALP-PWEB-02 — Donanım ve Performans Paneli

Durum: **DONE (yerel kod/sözleşme/test kapsamı), 4/4**.
Canlı Auth/Gateway/Hardware/Performance E2E: **NOT VERIFIED**. Final merge gate henüz çalıştırılmadı; merge-ready iddiası yok.
TASK 1 kullanıcı commit'i `df308dfa` doğrulandı; TASK 2 öncesi çalışma ağacı temizdi.

## Teslim ve dosyalar

- PWEB-04: Hardware `HardwareSnapshot`; çoklu bilgisayar ve bileşen sayfalaması, opsiyonel marka, manuel/senkronize kaynak, nullable teknik alanlar, gerçek profil gizliliği. `HardwareProfileReference` raporun gerçek bilgisayarını çözer; dashboard seçimini onun yerine kullanmaz. Mevcut Hardware API yeterli, backend değişmedi.
- PWEB-05: `SessionHistory`; owner geçmişi, sabit yeni→eski sunucu sırası, 20 satırlık sayfa, durum filtresi, oturum seçimi. Sayfa/filtre değişince eski detay gizlenir; seçilene kadar rapor/ölçüm istekleri atılmaz.
- PWEB-06: `SessionMeasurements`, `MeasurementChart`; FPS/CPU-GPU sıcaklık/kullanım/bellek/disk/fan metrikleri, tam/ilk 5/ilk 30 dakika, API maxPoints=100, RAW/BUCKET ve gerçek count/AVG/MIN/MAX. SVG ve semantik tablo; eksik veri sıfırlanmaz, sensör eksikliği boş durumla anlatılır; grafik hatası sağlam raporu gizlemez.
- PWEB-07: `SessionReport`; manuel veri kaynağı, çözünürlük, grafik ayarı, FPS/sıcaklık/kullanım, nullable değerler, güvenli notlar, güncelleme zamanı ve ilgili donanım profili. FPS tahmini veya otomatik ölçüm gibi gösterilmez.
- Rotalar: `/tr/performans/panel`, `/en/performance/dashboard`; dashboard route/layout, mevcut AppShell/Footer, Auth `useRequireSession`, accountId ile yeniden kurulan alt ağaç, private metadata/noindex/sitemap dışı. Tanıtım sayfasından gerçek panel bağlantısı, pages/routing/TR/EN kayıtları.
- Testler: Performance API/Chart/History/Dashboard; HardwareSnapshot/ProfileReference; `e2e/performance.spec.ts`; tam regresyonda ortaya çıkan AppShell/footer eski pasif Performans beklentileri düzeltildi.
- Belgeler: master checklist, TASK 2 test/checkpoint/commit taslağı, açık sorunlar/sözleşme matrisi, klasör kaydı.

## Entegrasyon ve güvenlik

Mevcut sözleşmeler: Hardware `GET /profiles`, `/profiles/{profileId}`, `/profiles/{profileId}/privacy`; Performance `GET /sessions`, `/sessions/{sessionId}/series`, `/sessions/{sessionId}/report`.
Endpoint, DTO, DB ve migration değişmedi; `docs/api.md` sözleşmesi değiştirilmedi.
Auth `SessionProvider`/`useRequireSession`, ortak `apiRequest` (credentials/refresh), Gateway cookie→Bearer ve mevcut backend owner kuralları kullanılır. Yeni Auth talebi gerekmedi; boş request dosyası açılmadı. Token okunmaz/saklanmaz, `userId` güven kaynağı olarak gönderilmez. Session/profile ID'leri URL segmentinde encode edilir. Okumalar no-store/timeout/abort ile sınırlıdır.

## Doğrulama

Tam kanıt: [task-02-tests.md](../testings/task-02-tests.md).
`npm.cmd run test`: 159 dosya, **1544 PASS**, mevcut **1 SKIP**, exit 0.
Özellik coverage: 36 test, **%98,46 line**, %78,08 branch; tüm frontend oranı değildir.
`npm.cmd run lint`: exit 0; generated coverage raporunda 1 uyarı.
`npm.cmd run typecheck`, `npm.cmd run build`, 7/7 Performance Playwright E2E ve `git diff --check`: PASS.
Backend değişmedi; yeni Maven/Flyway koşusu N/A. Pre-push/Sonar, masterdaki gibi TASK 3 sonrası final gate'te.

## Commit taslağı

```text
feat(performance-web): implement PWEB-04–07 hardware and performance dashboard

- PWEB-04: çoklu bilgisayar, bileşen sayfalaması, kaynak ve gizlilik bilgilerini mevcut Hardware API üzerinden göster
- PWEB-05: owner oturum geçmişine sunucu sayfalaması, durum filtresi ve kullanıcı seçimiyle detay yükleme ekle
- PWEB-06: on kayıtlı metrik için zaman aralığı seçimi, sınırlı RAW/BUCKET grafik ve erişilebilir ölçüm tablosu sun
- PWEB-07: manuel oyun raporlarını çözünürlük, grafik ayarı, nullable ölçümler ve doğru donanım profiliyle görüntüle
- Entegrasyon: mevcut Auth oturumunu ve Gateway API istemcisini kullan; backend endpoint, DTO ve migration değiştirme
- Güvenlik: accountId ile özel ekranları yeniden kur, no-store/timeout/abort ve noindex uygula; eksik veriyi veya servis hatasını başarıya dönüştürme
- UI: TR/EN panel rotaları, tema uyumu, mobil grafik kaydırması ve bağımsız boş/hata/yükleniyor durumları ekle
- Testler: 1544 geçen frontend testi, 7 geçen Performance E2E ve özellik kapsamındaki yüzde 98,46 satır coverage sonucunu doğrula
- Regresyon: AppShell ve footer testlerini etkin Performans rotasıyla uyumlu hale getir; standart lint, typecheck ve production build kontrollerini geçir
- Dokümantasyon: TASK 2 checkpoint, test kanıtları, master checklist, sözleşme ve commit taslaklarını güncelle
```

**Kullanıcının güncel talimatı:** Commit/push yapılmadı. TASK 2 checkpoint'inde duruldu; kullanıcı commit atıp devam demeden TASK 3 başlatılmayacak.
