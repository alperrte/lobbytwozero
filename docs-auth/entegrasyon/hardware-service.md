# HARDWARE-SERVICE — AUTH ENTEGRASYONU

**Sahip:** Alper · **Kod durumu:** Aktif · **Güncelleme:** 2026-10-29

## ÖZET

Kullanıcı JWT doğrulaması ve hesap silme ucu hazır ve gerçek Auth ile denenmiş. Masaüstü senkronu (`sync/snapshots`) için gereken **cihaz kimliği Auth'ta henüz yok**, tasarım taslağı hazır, onay bekliyor.

## AUTH'TAN HAZIR OLANLAR

- Kullanıcı token'ı: JWKS, `at+jwt`, `aud=ltz-api`, `roles`. Hardware uygun doğruluyor (`HardwareJwtValidators`)
- Hesap silme: `DELETE /internal/v1/accounts/{accountId}` yazılmış, Auth worker'ı gerçek token'la çağırıyor ve `204` alıyor (E2E ile denendi)
- Alper'in E2E betiği Auth, Gateway ve Hardware'i birlikte deniyor: `ops/hardware-auth-e2e.mjs`

## SENİN YAPACAKLARIN

- [ ] E2E betiğinde Auth'a `AUTH_DELETION_GRACE_PERIOD: 'PT0S'` ver (silme artık 14 gün bekleme süresiyle başlıyor, betik aksi halde silme adımında takılır). `LTZ_AUTH_DELETION_GRACEPERIOD` adı da çalışmalı, denenmedi
- [ ] Betikteki "15 ACK" ifadesini "11 ACK", "15 dk"yı "90 sn" yap (Auth artık 60 sn token, 11 servisin silme ucu hâlâ yok)
- [ ] Cihaz kimliği gelince: `dev+jwt` için ayrı doğrulayıcı yaz (aşağıda ve `cihaz-kimligi-tasarimi.md`'de). Mevcut `svc:` önek kuralına dokunma
- [ ] Re-test: Google/Steam testlerini DEBUG logda bir kez çalıştır (Auth artık hassas logları INFO'ya sabitledi)

## AUTH'TA BEKLEYEN

- **Masaüstü cihaz kimliği (B yolu):** kod yok. Önerilen biçim: `typ=dev+jwt`, `aud=hardware-service`, `sub=deviceId`, `account_id=hesap`, `scope=hardware:sync:write`, 60 sn. Hardware'deki karşılığı `AuthenticatedHardwareDevice(userId=account_id, deviceId=sub)` ve `hardware:sync:write` yetkisi. Karar Alper ve Kerem: `cihaz-kimligi-tasarimi.md` açık kararlar bölümü
- Hesap durumu olayı (`AccountStatusChanged`): yok, askı ve yasak en fazla 90 sn içinde token ömrüyle yayılır

## NASIL DENERSİN

`node ops/hardware-auth-e2e.mjs` (Docker, JDK 25 ve güncel jar gerekir)

## İLGİLİ BELGELER

`docs-auth/verilen-cevaplar/hardware-service/` (talep 01 ile 04), `docs-auth/entegrasyon/cihaz-kimligi-tasarimi.md`
