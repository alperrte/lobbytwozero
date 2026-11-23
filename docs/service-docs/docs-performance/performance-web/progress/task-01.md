# TASK 1 — LTZ-ALP-PWEB-01

Durum: PARTIAL — 3/3 uygulama mevcut; final build/E2E ve canlı entegrasyon henüz doğrulanmadı.

PWEB-01 tanıtım `/tr/performans` ve `/en/performance`; PWEB-02 erişilebilir pasif indirme; PWEB-03 gerçek owner cihaz API consumer'ı. Masaüstü ölçümü/eşleştirmesi veya indirme dosyası varmış gibi gösterilmez.

Dosyalar: `frontend/src/app/[locale]/(public)/performance/page.tsx`, `features/performance/{components,hooks,services,types}`, ortak pages/navigation/routing ve TR/EN mesajları. Hesap kimliği yalnız cache izolasyonu için kullanılır; API'ye userId gönderilmez. Cihaz içeriği metadata'ya/SSR'ye taşınmaz; sayfa noindex. Auth mevcut SessionProvider/apiRequest/Gateway cookie-Bearer/CSRF akışıdır; yeni Auth talebi gerekmedi. Endpoint/DTO/backend/migration değişmedi.

Testler: [task-01-tests.md](../testings/task-01-tests.md), 34 PASS; ESLint ve whitespace PASS; standart typecheck eski `.next` nedeniyle FAIL.

```text
feat(performance-web): add PWEB-01–03 landing and linked device views

- PWEB-01: LTZ kabuğunda yerelleştirilmiş Performance tanıtım rotasını ve navigasyonu ekle
- PWEB-02: yayımlanmamış masaüstü indirmesini açıklamalı pasif kontrolle sun
- PWEB-03: owner cihaz API'sini mevcut Auth oturumu ve Gateway istemcisi üzerinden tüket
- Güvenlik: misafir isteklerini engelle, hesap değişiminde eski veriyi gizle, kullanıcı içeriğini metin olarak sun
- UI: TR/EN, tema tokenları, boş/yükleniyor/hata/yeniden dene ve noindex davranışlarını ekle
- Testler: cihaz durumları ve API consumer testlerini ekle; hedefli koşuda 34 testi doğrula
- Dokümantasyon: sözleşme matrisi, bağımlılık kaydı ve TASK 1 test kanıtlarını kaydet
```

Commit/push yapılmadı; sıradaki taska otomatik devam ediyorum.
