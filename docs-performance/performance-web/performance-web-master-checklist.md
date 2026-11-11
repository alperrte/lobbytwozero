# Performance Web — master checklist

Kaynak: `../LTZ_Performance_Web_Master_Development_Prompt.md`, Alper görev dosyası.
11 Kasım 2026: kullanıcı task sonunda manuel commit için durulmasını istedi.
Bu oturumda TASK 3'e kullanıcı devam demeden geçilmez.

`[x]` yerel çalışan kod + sözleşme tüketimi + ilgili test + belge doğrulamasıdır.
Fixture E2E canlı servis kabulü değildir; canlı Auth/Gateway/servis E2E henüz NOT VERIFIED.
Final pre-push/SonarQube TASK 3 sonrasında ayrıca çalıştırılacaktır; merge hazırlığı şimdiden iddia edilmez.

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

Durum: PARTIAL 1/3 yerel akış uygulandı ve doğrulandı. PWEB-08/09 Recommendation sözleşmesi eksikliği sürüyor; ayrıntı `reports/issues.md`. Nihai backend/quality gate kanıtı `testings/pre-merge-quality.md` içinde kayıtlı.

- [ ] **PWEB-08 — Donanım uyumluluğu:** Kullanıcı bilgisayarı ile oyun sistem gereksinimlerinin karşılaştırılması.
- [ ] **PWEB-09 — Kişiselleştirilmiş öneriler:** Oyun ve donanım önerilerinin görüntülenmesi.
- [x] **PWEB-10 — Gizlilik ve veri yönetimi:** Kullanıcının saklanan veya ileride senkronize edilecek performans verilerini görüntüleme ve silme işlemleri. Yerel kod/test: mevcut consent ve owner delete API'leri; canlı tarayıcı kabulü NOT VERIFIED.

Genel yerel özellik durumu: **8/10**. Final quality gate denemeleri tamamlandı: pre-push FAIL, backend Sonar gate FAIL (C/C/A), tam E2E FAIL/FLAKY; frontend Sonar BLOCKED. **NOT READY FOR MERGE**. Tam kanıt `testings/pre-merge-quality.md`, PR taslağı `reports/pr-description.md`.
