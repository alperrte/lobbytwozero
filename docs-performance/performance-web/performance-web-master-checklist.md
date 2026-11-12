# Performance Web — master checklist

Kaynak: `../LTZ_Performance_Web_Master_Development_Prompt.md`, Alper görev dosyası.
11 Kasım 2026: kullanıcı task sonunda manuel commit için durulmasını istedi.
Bu oturumda TASK 3'e kullanıcı devam demeden geçilmez.

`[x]` yerel çalışan kod + sözleşme tüketimi + ilgili test + belge doğrulamasıdır.
Fixture E2E canlı servis kabulü değildir; canlı Auth/Gateway/servis E2E henüz NOT VERIFIED.
Final pre-push/SonarQube denemeleri TASK 3 sonrasında çalıştırıldı; tarihli kanıtlar aşağıdadır. Servis bazlı başarı global merge hazırlığı anlamına gelmez.

## TASK 1 — LTZ-ALP-PWEB-01 — Performance Tanıtım ve Cihaz Yönetimi Arayüzü

Durum: yerel doğrulama DONE 3/3; kullanıcı commit'i `df308dfa`.
İlk checkpoint'teki typecheck/build kısıtı TASK 2'de giderildi; 7 Performance E2E senaryosuna misafir tanıtım ve erişim kontrolü de dahildir.

- [x] **PWEB-01 — Performance ana sayfası:** LTZ Performance özelliklerinin anlatıldığı web sayfası.
- [x] **PWEB-02 — Masaüstü uygulaması indirme alanı:** Masaüstü sürümü yayımlandığında kurulum dosyası bağlantısının sunulacağı alan. **MVP'de gerçek indirme bağlantısı aktif olmayacak**; yayımlanmamış dosyaya yönlendirme yapılmayacak.
- [x] **PWEB-03 — Cihaz yönetimi:** Kullanıcının hesabına bağlı cihazları görüntüleyebileceği alan; masaüstü bağlantısı yokken uygun boş durum gösterilmesi.

Kanıt: `progress/task-01.md`, `testings/task-01-tests.md` ve TASK 2 regresyon kaydı.

## TASK 2 — LTZ-ALP-PWEB-02 — Donanım ve Performans Paneli

Durum: yerel doğrulama DONE 4/4; kullanıcı commit'i `5cfe014f` doğrulandı.

- [x] **PWEB-04 — Donanım paneli:** CPU, GPU, RAM ve diğer kayıtlı donanım özelliklerinin görüntülenmesi.
- [x] **PWEB-05 — Performans geçmişi:** Kayıtlı performans oturumlarının listelenmesi.
- [x] **PWEB-06 — Performans grafikleri:** Mevcut FPS, sıcaklık ve kullanım ölçümlerinin grafiklerle gösterilmesi; veri olmadığında boş durumun doğru sunulması.
- [x] **PWEB-07 — Oyun performans raporları:** Kullanıcının oyun bazlı manuel/kayıtlı performans sonuçlarını görüntülemesi.

Kanıt: `progress/task-02.md`, `testings/task-02-tests.md`; Hardware profilleri/ayrıntı/gizlilik, Performance sessions/series/report. Yeni backend/migration/endpoint yok.

## TASK 3 — LTZ-ALP-PWEB-03 — Uyumluluk, Öneriler ve Veri Gizliliği Ekranları

Durum: PARTIAL 1/3 yerel akış uygulandı ve doğrulandı; kullanıcı commit'i `22ef905d`. PWEB-08/09 Recommendation sözleşmesi eksikliği sürüyor; ayrıntı `reports/issues.md`. İlk backend/quality gate kanıtı `testings/pre-merge-quality.md` içinde kayıtlı.

- [ ] **PWEB-08 — Donanım uyumluluğu:** Kullanıcı bilgisayarı ile oyun sistem gereksinimlerinin karşılaştırılması.
- [ ] **PWEB-09 — Kişiselleştirilmiş öneriler:** Oyun ve donanım önerilerinin görüntülenmesi.
- [x] **PWEB-10 — Gizlilik ve veri yönetimi:** Kullanıcının saklanan veya ileride senkronize edilecek performans verilerini görüntüleme ve silme işlemleri. Yerel kod/test: mevcut consent ve owner delete API'leri; canlı tarayıcı kabulü NOT VERIFIED.

Genel yerel özellik durumu: **8/10**. 11 Kasım tarihli kalite denemeleri: pre-push FAIL, backend Sonar gate FAIL (C/C/A), tam E2E FAIL/FLAKY; frontend Sonar BLOCKED. Tarihçe `testings/pre-merge-quality.md`, PR taslağı `reports/pr-description.md`.

## 12 Kasım — main birleştirmesi ve servis kalite düzeltmeleri

- [x] Main `d2f31fe9` çakışmaları çözüldü; merge commit'i kullanıcıya bırakıldı. Frontend 1796 test, 21 shell/Performance E2E, lint/typecheck/build PASS. [Birleştirme kanıtı](testings/main-merge-2026-11-12.md).
- [x] Kullanıcı kapsamı yalnız Hardware / Performance olarak sınırladı. Diğer servislerde ve ortak kalite kurallarında bu çalışmadan değişiklik yok.
- [x] Son Hardware servis pre-push: 581 test, %93,59 LINE coverage, yeni kalite ihlali 0, `BACKEND TEST CHECK PASSED`.
- [x] Son Performance servis pre-push: 148 test, %97,18 LINE coverage, yeni kalite ihlali 0, `BACKEND TEST CHECK PASSED`.
- [x] Kapsamdaki 30 değişen/yeni kaynak/betik dosyasında Gitleaks PASS; üç yeni Java testi dahil.
- [x] Nihai Sonar tekrarı: iki serviste A/A/A, bug/vulnerability/code smell 0/0/0. Hardware Sonar coverage %90,4 / duplication %1,4; Performance %93,6 / %2,7. CE `2a57074b-ae96-4f06-9da9-95b3eeca5168`.
- [ ] Global gate: Auth fixture secret bulguları ve başka servislerin 237 açık Sonar issue'su kullanıcı talimatıyla kapsam dışındadır; 233 yeni issue global gate'i kırar. Global B/C/A, coverage %91,8, duplication %3,2. **NOT READY FOR MERGE**; tüm kapılar yeşil iddiası yoktur.

Güncel servis düzeltmeleri ve gerçek test sonuçları: [12 Kasım servis kalite raporu](testings/owned-quality-fixes-2026-11-12.md). PWEB-08/09 engeli devam eder; yeni servis entegrasyonu uygulanmadı.
