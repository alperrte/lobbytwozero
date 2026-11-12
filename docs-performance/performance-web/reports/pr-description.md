# PR Title

```text
feat(performance): performans web paneli, veri gizliliği ve servis kalite düzeltmeleri
```

# PR Description

## Sonuç ve kapsam

Kullanıcının kayıtlı cihazlarını, donanımını, performans geçmişini, grafiklerini ve raporlarını mevcut Next.js uygulamasında gösterir. Senkronizasyon izni ile onaylı tekli/toplu veri silme akışlarını mevcut Auth, Gateway, Hardware ve Performance sözleşmeleriyle çalıştırır. Main'deki İncelemeler ve feature dalındaki Performans navigasyonu birlikte korunur.

**Kısmi özellik teslimi: 8/10 PWEB.** PWEB-08/09 için Recommendation iş API/sözleşmesi yoktur. Gerçek olmayan ölçüm, FPS tahmini, indirme dosyası, uyumluluk sonucu veya öneri üretilmez.

Kalite düzeltmeleri kullanıcının açık sınırıyla yalnız Hardware/Performance ve Hardware import yardımcılarını kapsar. Başka servislerin kaynaklarına, ortak kalite kurallarına, ENV veya Git kimliğine değişiklik yoktur.

## Özellikler

- **PWEB-01–03:** TR/EN tanıtım sayfası, pasif masaüstü indirme alanı ve mevcut Auth cihazlarını gösteren owner görünümü. Kullanıcı commit'i `df308dfa`.
- **PWEB-04–07:** çoklu donanım profili, nullable teknik özellikler, kaynak ve gizlilik; sayfalı/filtreli oturum geçmişi; mevcut metrikler için erişilebilir grafik/tablo; manuel kaynak ayrımını koruyan oyun raporları. Kullanıcı commit'i `5cfe014f`.
- **PWEB-10:** mevcut GET/PUT/DELETE consent ve owner single/bulk DELETE sözleşmeleri, açık onay, çift istek kilidi, iptal ve belirsiz mutasyonda güncel veri kontrolü. Kullanıcı commit'i `22ef905d`; PWEB-08/09 BLOCKED kalır.
- Özel panel rotaları noindex/sitemap dışıdır. Mevcut session/refresh, cookie/CSRF, no-store, timeout/abort ve hesap değişiminde eski veriyi gizleme davranışları korunur.

## Hardware ve Performance kalite düzeltmeleri

- Hardware katalog kaynağı sorguları sabit SQL ve named parametrelerle çalışır; JWT audience/ID null kontrolleri açıklaştırılır.
- Katalog arama parametreleri seçim/teknik koşul record'larında toplanır; transaction proxy sınırları ve HTTP sözleşmeleri korunur.
- Icecat ayrıştırıcı, XML sayfalama ve dataset import kodu küçük metotlara ayrılır; gerçek veri limitleri, birimler, karantina ve rollback davranışları değişmez. Tekrar eden sabitler, kullanılmayan importlar ve test lambda uyarıları temizlenir.
- Hardware import CLI lazy logger argümanları kullanır; yalnız bu CLI'nin izinli logger öneki kaldırılarak mevcut makine özeti biçimi korunur. Ham hata/ENV çıktısı geçirilmez.
- Performance JWT kontrolü bağlam, zaman ve servis kimliği koşullarına ayrılır. 60 sn ömür, 30 sn tolerans, servis tek audience/jti ve kullanıcı UUID kuralları korunurken kopyalanmış uzun blok azaltılır.
- Icecat kaynak/birim/limit ve Performance kimlik/zaman/claim regresyonları eklenir. Endpoint, migration, bağımlılık, ENV, kalite eşiği veya exclusion değişikliği yoktur.

## Servis entegrasyonları ve sınırlar

- **Auth:** mevcut session/HttpOnly cookie/refresh/Gateway CSRF akışı tüketilir. Performance cihaz yetkilendirme ve hesap silme bağlantılarının izole kabul kanıtları mevcuttur; yeni Auth kodu veya talebi yoktur.
- **Hardware:** frontend mevcut profil/detail/privacy sözleşmelerini tüketir. Hardware'ın Performance aktif cihaz sorgusu uygulanmıştır; Performance'ın Hardware profil doğrulamasıyla oturum açma E2E kabulü ayrıca gerekir. Performance özeti için üretim Hardware tüketicisi ayrı iştir.
- **Game:** Performance batch istemcisi vardır; gerçek Game ile oturum/rapor akışı E2E kabulü ayrıca gerekir.
- **Recommendation:** henüz uygulama iskeletidir. Performance reference sözleşmesi ve Auth izni hazır olsa da üretim tüketicisi/öneri motoru yoktur; PWEB-08/09 bununla bloke olur. Bu kalite işinde yeni entegrasyon uygulanmadı.

## Doğrulama

| Kontrol | Gerçek sonuç |
| --- | --- |
| Main birleşimi sonrası frontend lint/typecheck/build | PASS |
| Frontend unit/component | 175 dosya, 1796 PASS, mevcut 1 SKIP |
| Shell + Performance Chromium E2E | 21/21 PASS; fixture kabulü, canlı servis E2E'si değildir |
| Son Hardware servis pre-push | 581 test, %93,59 LINE; build/coverage/Checkstyle/PMD PASS; BACKEND TEST CHECK PASSED |
| Son Performance servis pre-push | 148 test, %97,18 LINE; build/coverage/Checkstyle/PMD PASS; BACKEND TEST CHECK PASSED |
| Hardware import Node regresyonları | 10/10 PASS |
| İki modül izole runtime Trivy | HIGH/CRITICAL 0, PASS; yeni bağımlılık yok |
| Kapsamdaki secret taraması | 30 değişen/yeni kaynak/betik, Gitleaks PASS; üç untracked Java testi dahil |
| Nihai Sonar'ın taze tam backend test turu | 4369 test, 0 failure/error/skip; build ve merkezi coverage PASS, toplam LINE %95,52 |
| Hardware Sonar metrikleri | A/A/A; BUG/VULNERABILITY/CODE_SMELL 0/0/0; coverage %90,4, duplication %1,4 |
| Performance Sonar metrikleri | A/A/A; BUG/VULNERABILITY/CODE_SMELL 0/0/0; coverage %93,6, duplication %2,7 |
| Global backend Sonar Quality Gate | FAIL; 233 yeni issue, toplam 237 açık issue iki servis dışında; global B/C/A, coverage %91,8, duplication %3,2 |
| Global pre-push secret kapısı | FAIL/FINDING; main ile gelen Auth test fixture satırları kapsam dışında; kural gevşetilmedi |
| Frontend Sonar | Mevcut runner yalnız backend analiz eder; frontend sonucu yok |

Sonar CE: `2a57074b-ae96-4f06-9da9-95b3eeca5168`, `ltz-backend`, 12 Kasım 2026. Servis metrikleri, tek backend projesinden alınan gerçek component ölçümleridir; ayrı servis Quality Gate'i varmış gibi PASS iddia edilmez. Birleşmiş kaynaklar henüz commit edilmediğinden scanner SCM revision `22ef905d` ve eksik blame uyarıları vardır.

Güncel kanıt: [servis kalite raporu](../testings/owned-quality-fixes-2026-11-12.md), [main birleştirme raporu](../testings/main-merge-2026-11-12.md). Önceki tam tarayıcı koşusundaki Navbar Ctrl+K focus hatası izole tekrarda geçmişti; yeni birleşmiş kaynakta tüm E2E paketi tekrar çalıştırılmadı, temiz full E2E iddia edilmez.

## Merge öncesi açık işler

- [x] Hardware/Performance servis pre-push ve Sonar metrik hedefleri doğrulandı; iki serviste açık issue yok.
- [ ] Kapsam dışındaki Auth fixture secret bulguları ve diğer servislerin global Sonar borcu giderilmeli.
- [ ] PWEB-08/09 Recommendation sözleşmesi ve tüketicileri tamamlanmalı veya kısmi teslim kapsamı kabul edilmeli.
- [ ] Canlı tarayıcı Auth/Gateway/Hardware/Performance kabulü ve birleşmiş kaynakta temiz tam E2E doğrulanmalı.
- [ ] Frontend Sonar kapsamı ve gerekiyorsa GitHub required check ayarları ayrıca doğrulanmalı.
- [ ] Kullanıcı devam eden main merge commit'ini ve kalite değişikliklerinin staging/commit işlemini kendisi tamamlamalı.

**Merge hazırlığı: NOT READY FOR MERGE.** Kullanıcının Hardware/Performance kalite düzeltme kapsamı tamamlandı; global kapılar ve kapsam dışındaki ürün/entegrasyon işleri tamamlanmış sayılmaz. Otomatik commit, push, PR oluşturma veya merge yapılmadı.
