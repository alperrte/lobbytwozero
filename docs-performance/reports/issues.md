# Performance Service — Sorunlar ve riskler

## Faz 1

| # | Konu | Durum | Sahip | Etki / önlem |
| --- | --- | --- | --- | --- |
| I-01 | Auth cihaz token'ı yalnız `aud=hardware-service` üretiyor (REQ-01-A) | BLOCKED | Kerem | Gerçek cihaz senkronizasyonu E2E yapılamaz; Hardware token'ı Performance'ta reddedilir, gevşetme yapılmadı |
| I-02 | Auth cihaz iptalini servislere bildirmiyor (REQ-01-B) | REQUESTED / kabul edilen risk | Kerem | İptal sonrası ≤ 60 sn pencere; Performance bağlantısı kullanıcı kaldırana dek ACTIVE görünebilir |
| I-03 | Auth servis token'ı `sub` değerini hep `svc:auth-service` yapıyor, `devices:verify` yok (REQ-01-C) | BLOCKED | Kerem | Hardware→Performance aktif cihaz ucu gerçek token ile çağrılamaz; Hardware kodu değiştirilmedi (Hardware istemcisi önce Hardware sahibine bildirilmeli) |
| I-04 | Auth silme işçisi → Performance D4 ucu ortak ortamda doğrulanmadı | NOT VERIFIED | Alper + Kerem | Uç ve testler hazır |
| I-05 | Standart `pre-push` çalıştırılmadı (ENV okuma yasağı) | NOT RUN | Alper | Yerine ENV'siz CI runner PASS |
| I-06 | SonarQube çalıştırılmadı (`SONAR_TOKEN` ENV'de) | NOT RUN | Alper | Sunucu `localhost:5199` erişilebilir; komut `phase-01-tests.md` §5 |
| I-07 | `sonarqube/sonarqube.md` pre-push belgesinde anılıyor ama dizinde yok | Bilgi | Ekip | Belge eksik; kod değişikliği gerekmedi |
| I-08 | Bearer'sız yazma isteğinde CSRF reddi 401'e çevrildi | Karar (KARAR-04) | Alper | Oturum/cookie desteklenmediği için doğru semantik; gözden geçirilebilir |
| I-09 | Test kapsamına iki bağımlılık eklendi | Bilgi | Alper | Hardware'de zaten kullanılıyor; yeni üretim bağımlılığı yok |
