# CİHAZ KİMLİĞİ (B YOLU) — TASARIM TASLAĞI

**Durum:** UYGULANDI (2026-10-29, V67); bu belge tasarım kaydıdır, gerçek sözleşme `docs/api.md` "Masaüstü cihaz kimliği" bölümündedir. Sapmalar: hesap kilidi yok, `DEVICE_TOKEN_REJECTED` olayı yok, etiket biçimi kısıtlı, günde 10 bağlama sınırı. · **Sahip:** Kerem · **İlgili:** Alper (Hardware, Performance) · **Kaynak karar:** `docs-auth/verilen-cevaplar/hardware-service/talep-02-masaustu-cihaz-kimligi.md` (B yolu seçildi)
**Tarih:** 2026-10-29 · **Migration aralığı:** Auth V67–V70 boş, bu iş V67 kullanır

---

## NE ÇÖZÜYORUZ

Masaüstü uygulaması donanım bilgisini Hardware'a gönderecek: `POST /api/v1/hardware/profiles/{profileId}/sync/snapshots`. Bu uç kullanıcı token'ıyla açılmıyor, çünkü kullanıcı token'ıyla cihaz üretilmez ve yetki geniş kalır. Hardware şu an üretimde cihaz doğrulamasını reddediyor (policy deny).

**Hardware'in koddaki beklentisi (doğrulandı):**

- Principal: `AuthenticatedHardwareDevice(UUID userId, UUID deviceId)`. Yalnız "gelecekteki doğrulanmış bir cihaz doğrulayıcısı" üretebilir
- Yetki dizesi: `hardware:sync:write` (`HardwareSyncDeviceAccess.PERMISSION`)
- Sonra Hardware hesabın aktif olduğunu ve Performance'ın cihaz sahipliği politikasını kendisi denetler

Yani Auth'un işi: **kim (hesap) ve hangi cihaz** bilgisini taşıyan, dar yetkili, kısa ömürlü, iptal edilebilir bir token üretmek.

---

## ÖNERİLEN TASARIM

### 1. Cihaz kimlik bilgisi

- Cihaza uzun ömürlü bir gizli anahtar verilir (256 bit rastgele, `base64url`). Auth yalnız özetini saklar, düz değeri **bir kez** gösterir
- Cihaz `deviceId` (UUID) ve `label` (en fazla 64 karakter, kullanıcının verdiği ad) taşır
- Kullanıcı başına en fazla 5 aktif cihaz (öneri)
- Süre: 365 gün, yenilemek için yeniden bağlama (öneri, tartışmaya açık)

### 2. Kullanıcı uçları (Gateway'den, kullanıcı oturumuyla, CSRF zorunlu)

| Uç | Ne yapar |
|---|---|
| `POST /api/v1/auth/devices` | Cihaz bağla. Gövde: `label`, `password` (yeniden doğrulama), TOTP açıksa kod. Yanıt `201`: `deviceId`, `deviceSecret` (tek seferlik) |
| `GET /api/v1/auth/devices` | Kullanıcının cihazları: `deviceId`, `label`, `createdAt`, `lastUsedAt`, `status`. Gizli anahtar asla dönmez |
| `DELETE /api/v1/auth/devices/{deviceId}` | Cihazı iptal et. İdempotent |

Bağlama yeniden doğrulama ister (parola, varsa TOTP), aynı hesap silme ve e-posta değiştirme gibi. Yönetici cihaz yerine geçemez.

### 3. Cihaz token ucu (Gateway'den, kimliksiz, hız sınırlı)

`POST /api/v1/auth/devices/token` gövde: `{ "deviceId": "...", "deviceSecret": "..." }`

- Yanıt: `accessToken`, `expiresIn`, `Cache-Control: no-store`
- Yanlış, bilinmeyen, iptal edilmiş cihaz tek yanıt verir (`401 INVALID_CLIENT`), hesap kilidi yok ama IP ve cihaz başına hız sınırı var
- Hesap `ACTIVE` değilse (askı, yasak, silme isteği) token verilmez

### 4. Cihaz token'ı

| Alan | Değer |
|---|---|
| Başlık `typ` | `dev+jwt` (kullanıcı `at+jwt` ve servis `svc+jwt` değil: biri diğerinin yerine geçemez) |
| `iss` | `ltz-auth` |
| `aud` | `hardware-service` (yalnız Hardware'de geçer, Auth'ta geçmez) |
| `sub` | `deviceId` (UUID) |
| `account_id` | cihazı bağlayan hesap (UUID) |
| `scope` | `hardware:sync:write` (başka kapsam yok) |
| Ömür | 60 sn (öneri, servis token'larıyla aynı). Masaüstü her senkronda yeni token alır |
| `jti` | her token için yeni |

Auth'un kendi uçları cihaz token'ını reddeder (`aud` ve `typ` uyuşmaz).

### 5. İptal ve yaşam döngüsü

- Kullanıcı iptal ederse kayıt kapanır, yeni token verilmez. Mevcut token en fazla 60 sn yaşar
- Aşağıdakilerde cihazların hepsi aynı işlemde iptal olur: parola değişti, parola sıfırlandı, hesap askıya alındı veya yasaklandı, hesap silme onaylandı, e-posta değişti (öneri)
- Hesap silme tamamlanınca cihaz kayıtları hesapla birlikte gider (FK `CASCADE`)
- Güvenlik olayları: `DEVICE_BOUND` ve `DEVICE_REVOKED` yazılır. Her token verilişinde olay yazılmaz (tabloyu şişirir). Reddedilen token için `DEVICE_TOKEN_REJECTED` yalnız ilk hatada ve eşikte yazılır

### 6. Veri modeli (V67)

`device_credentials`: `id` (= deviceId), `account_id` (FK, `ON DELETE CASCADE`), `label`, `secret_hash` (SHA-256 hex), `status` (`ACTIVE`/`REVOKED`), `created_at`, `last_used_at`, `revoked_at`, `expires_at`, `version`. `CHECK` kısıtları ve `(account_id)` indeksi. Aktif cihaz sayısı sınırı serviste ve veritabanında.

---

## ALPER TARAFINDA DEĞİŞECEKLER

- **Hardware:** `dev+jwt` için üçüncü bir doğrulayıcı: `typ=dev+jwt`, `aud=hardware-service`, `scope` içinde `hardware:sync:write`, `sub` ve `account_id` kanonik UUID. Doğrulanınca `AuthenticatedHardwareDevice(userId=account_id, deviceId=sub)` ve `hardware:sync:write` yetkisi. Mevcut `svc:` önek kuralı cihaz token'ına uymaz, dokunma, ayrı doğrulayıcı olsun
- **Performance (PERF-01):** "Bu cihaz aktif ve bu hesaba mı ait" kararı Performance'ta kalıyor. Auth'ta cihaz kaydı var, ama aktif cihaz envanteri Performance'ın. İki kaynak arasında sahiplik çakışırsa: **Auth kaydı cihazın kimliğini, Performance cihazın çalıştığını** söyler
- **İptal bildirimi (isteğe bağlı, ikinci aşama):** Auth cihazı iptal edince `DeviceRevoked` olayını Performance'a `events:ingest` ile gönderebilir. Şimdilik gerekmiyor, 60 sn token ömrü yeterli

---

## GÜVENLİK TESTLERİ (yazılacak)

- Kullanıcı token'ı, servis token'ı ve cihaz token'ı birbirinin yerine geçemez
- Cihaz token'ı Auth'un hiçbir ucunda ve başka serviste geçmez
- İptal, askı, yasak, silme ve parola değişikliği sonrası token alınamaz
- Başka hesabın cihazı iptal edilemez (IDOR)
- Yanlış gizli anahtar ve bilinmeyen cihaz aynı yanıtı verir
- Gizli anahtar hiçbir yerde loglanmaz, veritabanında düz saklanmaz
- Hız sınırı çalışır, cihaz sayısı sınırı aşılamaz
- Yönetici cihaz bağlayamaz ve cihaz yerine geçemez

---

## AÇIK KARARLAR

1. Cihaz sınırı 5 mi, süre 365 gün mü
2. Cihaz token ömrü 60 sn mi (sık senkron için sorun değil), yoksa 5 dk mı
3. Bağlama arayüzü: kullanıcı web'de "cihaz bağla" deyip anahtarı masaüstüne yapıştırır mı, yoksa kod/QR akışı mı (Alper masaüstü tarafını biliyor)
4. Auth cihaz ucu `devices/token` Gateway'den geçiyor, masaüstü bunu çağırır. Alper'in masaüstü istemcisi bunu destekler mi

## İŞ TAHMİNİ

Auth tarafı: migration, entity, 3 kullanıcı ucu, token ucu, iptal bağları, testler ve belgeler. Yaklaşık 1 ila 2 gün. Hardware doğrulayıcısı ve Performance API'si ayrı, Alper'in işi.

## ONAY

Onaylanınca sırayla: V67 migration, uçlar, testler, `docs/api.md` kaydı, Hardware için ekip notu. Onaylanmadan Auth'ta cihaz claim, kapsam veya uç adı yazılmaz (talep-02 kararı).
