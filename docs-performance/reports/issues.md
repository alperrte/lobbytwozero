# Performance Service — Sorunlar ve riskler

## Faz 1

| # | Konu | Durum | Sahip | Etki / önlem |
| --- | --- | --- | --- | --- |
| I-01 | Auth cihaz token'ı yalnız `aud=hardware-service` üretiyor (REQ-01-A) | BLOCKED | Kerem | Gerçek cihaz senkronizasyonu E2E yapılamaz; Hardware token'ı Performance'ta reddedilir, gevşetme yapılmadı |
| I-02 | Auth cihaz iptalini servislere bildirmiyor (REQ-01-B) | REQUESTED / kabul edilen risk | Kerem | İptal sonrası ≤ 60 sn pencere; Performance bağlantısı kullanıcı kaldırana dek ACTIVE görünebilir |
| I-03 | Auth servis token'ı `sub` değerini hep `svc:auth-service` yapıyor, `devices:verify` yok (REQ-01-C) | BLOCKED | Kerem | Hardware→Performance aktif cihaz ucu gerçek token ile çağrılamaz; Hardware kodu değiştirilmedi (Hardware istemcisi önce Hardware sahibine bildirilmeli) |
| I-04 | Auth silme işçisi → Performance D4 ucu ortak ortamda doğrulanmadı | NOT VERIFIED | Alper + Kerem | Uç ve testler hazır |
| I-05 | Standart `pre-push` (`.\pre-push\pre-push.cmd performance-service`) | ÇÖZÜLDÜ — PASS | Alper | BACKEND TEST CHECK PASSED, EXIT 0 (31 test, %94,25) |
| I-06 | SonarQube (`sonar.cmd scan dev`) | Kapı FAIL — tüm repo taraması | Alper | Analiz çalıştı; kapı repo genelinde kırmızı. performance-service'in 9 bulgusu (S2259, S1075, S3358, S107, S1192×2, S5778×4) düzeltildi; yeniden tarama sonucu `phase-01-tests.md` §1'de. Diğer servislerin bulguları bu görevin kapsamı dışında, dokunulmadı |
| I-08 | Bearer'sız yazma isteğinde CSRF reddi 401'e çevrildi | Karar (KARAR-04) | Alper | Oturum/cookie desteklenmediği için doğru semantik; gözden geçirilebilir |
| I-09 | Test kapsamına iki bağımlılık eklendi | Bilgi | Alper | Hardware'de zaten kullanılıyor; yeni üretim bağımlılığı yok |
