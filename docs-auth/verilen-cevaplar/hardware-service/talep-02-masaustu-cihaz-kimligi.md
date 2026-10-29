# TALEP 02 — MASAÜSTÜ CİHAZ KİMLİĞİ

**Talep kodu:** HW-AUTH-REQ-02 · **Kimden:** Alper (hardware-service) · **Kime:** Kerem (auth-service) · **Cevap tarihi:** 2026-10-26
**Durum:** AUTH TARAFI UYGULANDI (2026-10-29, PR #25) — B yolu · Hardware'de `dev+jwt` doğrulayıcısı bekliyor

---

## NEYDİ

Masaüstü uygulaması donanım bilgisini Hardware'a gönderecek. Bunun için `POST /api/v1/hardware/profiles/{profileId}/sync/snapshots` ucu var. Bu uç kullanıcı token'ıyla açılamıyor çünkü Alper ayrı ve dar yetkili bir cihaz kimliği istiyor. Hardware tarafı hazır. Eksik olan Auth'un böyle bir kimliği vermesi.

## NE SORULDU

- Cihaz için ayrı bir kimlik bilgisi tipi olacak mı
- Bu kimlik nasıl verilecek, ne kadar yaşayacak, nasıl yenilenecek, nasıl iptal edilecek
- Kimlik hangi hesaba ve hangi cihaza bağlı olacak, hangi yetkiyi taşıyacak
- Aktif cihaz listesini Performance servisi tutacak. O ve Auth nasıl anlaşacak

## BENİM CEVABIM

**B YOLUNU SEÇTİM. CİHAZ KİMLİĞİNİ AUTH VERECEK.**

Üç yol vardı.

- **A:** Masaüstü uygulaması kullanıcı girişiyle token alır, cihazı Hardware kendi kaydeder. Auth'ta iş yok ama yetki dar olmaz.
- **B:** Auth cihaza ayrı bir kimlik verir. Cihaz bununla ayrı türde kısa ömürlü token alır. İptal Auth'ta olur.
- **C:** Hardware cihaz anahtarını kendi yönetir. Auth'ta iş yok ama kimlik ve iptal Hardware'a kalır.

B'yi seçtim. Yönü şöyle.

1. Kullanıcı normal oturumuyla "bu cihazı bağla" der
2. Auth cihaza uzun ömürlü bir kimlik bilgisi verir. Auth sadece özetini saklar
3. Cihaz bununla kısa ömürlü bir cihaz token'ı alır. Türü kullanıcı token'ından farklı olur. Hedefi hardware-service olur. Yetkisi sadece `hardware:sync:write` olur. İçinde hesap ve cihaz kimliği olur
4. Cihaz iptal edilirse Auth'ta kayıt kapanır ve yeni token verilmez
5. Cihazın aktif olup olmadığı ve kime ait olduğu kararı Performance'ta kalır, o PERF-01 işi

## NEDEN B

- **Dar yetki.** Cihaz token'ı sadece senkronizasyon yazabilir. Çalınırsa başka bir şey yapamaz
- **Karışmaz.** Kullanıcı token'ıyla cihaz token'ı farklı tür. Biri diğerinin yerine geçemez
- **İptal tek yerde.** Cihaz kaybolursa ya da kullanıcı hesabı silinirse kimlik Auth'ta kapanır
- **Kimliğin sahibi Auth olmalı.** Kimlik doğrulama başka serviste dağılırsa güvenlik kuralları servis servis farklılaşır. Biz bunu zaten servis token'larında bu şekilde çözdük, Auth imzalıyor servisler doğruluyor

A yolu işi bana yüklemiyor ama yetkiyi geniş bırakıyor. C yolu da cihaz güvenliğini Hardware'a yüklüyor. İkisini seçmedik.

## GÜNCELLEME (2026-10-29): AUTH TARAFI HAZIR

Önceki sürümde "geliştirmeye şu an kapalı" yazıyordu. Mevcut işler main'e girdi ve cihaz kimliği Auth'ta uygulandı (migration V67, PR #25). Gerçek Auth ve Gateway ile denendi: bağlama, listeleme, iptal, cihaz token'ı, token'ın Auth'ta geçmemesi, e-posta değişiminde cihazların iptali.

**Uçlar** (ayrıntı `docs/api.md` "Masaüstü cihaz kimliği"):
- `POST /api/v1/auth/devices`: kullanıcı oturumuyla cihazı bağlar; parola (TOTP varsa o da) şart. Gizli anahtar yalnız bu yanıtta bir kez döner
- `GET /api/v1/auth/devices`, `DELETE /api/v1/auth/devices/{deviceId}`: liste ve iptal
- `POST /api/v1/auth/devices/token`: `deviceId` + `deviceSecret` ile 60 sn'lik token (çerezsiz, herkese açık uç)

**Cihaz token'ı:** `typ=dev+jwt`, `iss=ltz-auth`, `aud=hardware-service`, `sub=<deviceId>`, `account_id=<hesap>`, `scope=hardware:sync:write`, `jti`, ömür 60 sn. Rol taşımaz, Auth'un hiçbir ucunda geçmez.

**Kurallar:** hesap başına en fazla 5 etkin cihaz, günde 10 bağlama, anahtar 365 gün. Parola/e-posta değişimi, hesabın aktif olmaması ve silme onayı cihazları iptal eder. Token ucunda tüm kimlik hataları tek yanıt verir (`INVALID_DEVICE_CREDENTIALS`); hesap kilidi yok, IP sınırı Gateway'de.

**Tasarımdan farklar:** `DEVICE_TOKEN_REJECTED` olayı yok; cihaz adı yalnız harf, rakam, boşluk ve `._()-` içerebilir (en fazla 64 karakter).

**Performance ile bölüşüm değişmedi:** Auth cihazın kimliğini ve hangi hesaba bağlı olduğunu söyler; cihazın aktif/çalışıyor olduğu kararı Performance'ta (PERF-01). Performance'ın aktif cihaz API'si henüz yok, uç adı ve yanıt biçimi yazılı değil. Hardware bu kararı ona soracak, o uç sende/Performance'ta belirlenmeli.

## SENDEN NE BEKLİYORUM

- Hardware'de `dev+jwt` için ayrı doğrulayıcı yaz: `typ=dev+jwt`, `iss`, `aud=hardware-service`, `sub=deviceId` (UUID), `account_id` (UUID), `scope` içinde `hardware:sync:write`, ömür ≤ 60 sn. Mevcut `svc:` önek kuralına dokunma
- ADMIN cihaz yerine geçemez. Bu kuralı sen de yazmışsın, aynen kabul
- `sync/snapshots` ucu doğrulayıcı hazır olana kadar fail-closed kalsın
- Performance'ın aktif cihaz API'si için uç adı ve yanıt biçimini belirle (PERF-01)

## NEREDEN BAKARSIN

- Açık özellik kaydı: `docs-auth/acik-guvenlik-borclari.md`
- Servis token mantığı (örnek olarak): `docs-auth/servis-sozlesmeleri.md` bölüm 3
