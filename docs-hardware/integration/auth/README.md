# Hardware–Auth entegrasyonu

Sorumlu: Alper. Güncelleme: 2026-10-29. Kapsam: ikinci entegrasyon aşaması, HW-06/HW-07 ve mevcut Auth/D4 sınırı.

- [05 — Talep karşılaştırması ve Hardware uygulaması](auth-integration-05.md)
- [06 — Güncel test ve kabul kanıtları](auth-integration-06.md)
- [Auth'a kalan ortak kabul takibi — talep 05](auth-service-talepleri/auth-requests-05.md)
- [Birinci aşama arşivi](entegrasyon-v1/README.md)

Auth cihaz kimliğinin doğrulanması, Performance aktif cihaz politikasını sağlamaz.
Kullanıcı kararıyla PERF-01/02 bu göreve dahil değildir; üretimde grant/apply kapalı kalır.
Auth/Gateway/Performance kaynakları, gerçek ENV dosyaları ve mevcut migration'lar değiştirilmez.

Eski `01–04` entegrasyon ve talep dosyaları, kalite ve PR notları mevcut alt yapılarıyla
`entegrasyon-v1/` altındadır. Yeni kayıtlar `05` ile devam eder.
