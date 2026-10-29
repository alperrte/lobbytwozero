# Hardware–Auth entegrasyonu

Sorumlu: Alper. Güncelleme: 2026-10-29. Kapsam: ikinci entegrasyon aşaması, HW-06/HW-07 ve mevcut Auth/D4 sınırı.

- [05 — Talep karşılaştırması ve Hardware uygulaması](auth-integration-05.md)
- [06 — Güncel test ve kabul kanıtları](auth-integration-06.md)
- [Ortak DEV doğrulama takibi](auth-integration-06.md#ortak-dev-doğrulama-takibi)
- [Birinci aşama arşivi](entegrasyon-v1/README.md)

Auth cihaz kimliğinin doğrulanması, Performance aktif cihaz politikasını sağlamaz.
Kullanıcı kararıyla PERF-01/02 bu göreve dahil değildir; üretimde grant/apply kapalı kalır.
Auth/Gateway/Performance kaynakları, gerçek ENV dosyaları ve mevcut migration'lar değiştirilmez.
Bu aşamada Auth'tan beklenen yeni bir geliştirme talebi saptanmadı. Ortak DEV kabulü
entegrasyon doğrulama kaydında izlenir.

Eski `01–04` entegrasyon ve talep dosyaları, kalite ve PR notları mevcut alt yapılarıyla
`entegrasyon-v1/` altındadır. Yeni kayıtlar `05` ile devam eder.
