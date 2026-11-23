# LTZ-NIS-CONT-AUTH-01 — Hesap silme entegrasyonu (ayrı görev, takip)

> Durum: **PLANLANDI, kodlanmadı.** LTZ-NIS-CONT-01 kapsamı dışındadır; bu belge politikayı ve kabul ölçütlerini kaydeder.
> Kaynaklar: `docs-auth/servis-sozlesmeleri.md` §3.1, `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`, `docs-auth/verilen-cevaplar/content-service/bilgilendirme-2026-11-05.md`.

## Neden gerekli

Auth, hesap silerken 16 hedefe `DELETE /internal/v1/accounts/{accountId}` çağırır ve hepsi `204` dönmeden hesabı `DELETED` yapmaz. Content Service bu ucu yazmadığı sürece Auth'ta `content` görevi `PENDING` kalır ve hesap silme **hiçbir zaman tamamlanmaz**. Servis artık kullanıcı verisi (taslak, görsel) tuttuğu için bu iş bekletilmemelidir.

## Politika (LTZ-NIS-CONT-01 kararı)

Hesap silindiğinde, o kullanıcının **yazarı olduğu tüm içerik kayıtları kalıcı olarak silinir**:

- taslaklar,
- **yayımlanmış içerikler** (kullanıcının yayımladığı haberler de kaldırılır),
- önceden silinmiş içeriklere ait kalan kayıtlar (silme işaretli `contents` satırları).

Bu, anonimleştirme değil kalıcı silmedir. `contents` satırları silinince `content_translations` ve `content_images` `ON DELETE CASCADE` ile gider. Başkasının verisine dokunulmaz (Content Service'te başkasına ait kayıt zaten yoktur).

## Kabul ölçütleri

- `DELETE /internal/v1/accounts/{accountId}` yalnız Auth'un imzaladığı servis token'ıyla çalışır: `typ=svc+jwt`, `aud=content-service`, `sub=svc:auth-service`, `scope=account:erase`, `account_id` = yoldaki `{accountId}`, ömür ≤ 60 sn, `jti` zorunlu. Kullanıcı token'ı ve başka çağıran/kapsam reddedilir (401/403).
- Tek transaction; başarıda ve hesap zaten yoksa **`204`** (idempotent). 5 saniye sınırı gözetilir.
- Gateway bu yolu yönlendirmez (`/internal/**` yalnız servisler arası); `/api/v1/content/**` dışında açık yol eklenmez.
- Loglarda yalnız `accountId` yer alır (içerik, token, `Authorization` yok).
- Testler: gerçek RSA imzalı token + yerel JWKS ile güvenlik tablosu (community-service `InternalEndpointSecurityTest` örneği), gerçek PostgreSQL ile veri etkisi (taslak/yayımlanmış/silinmiş satırlar ve cascade, başkasının verisine dokunmama, idempotentlik).
- `docs/api.md` CONTENT bölümüne iç uç kaydı; `SecurityConfig`'e yalnız bu uç için `SCOPE_account:erase` kuralı (diğer `/internal/**` yolları `denyAll` kalır), servis token'ı için ayrı `JwtDecoder` (community-service `JwtConfig` örneği).
- Dev doğrulaması (Kerem ile): `AUTH_DELETION_GRACE_PERIOD=PT0S` ile test hesabı silinir ve `account_deletion_tasks` içinde `content` görevi `CONFIRMED` olur.

## Bağımlılık ve sıra

- LTZ-NIS-CONT-01 tamamlandıktan sonra, yayınlama fazından (CONT-09) **önce** yapılması önerilir: yayımlanmış içerik oluşmadan da taslak verisi için gereklidir.
- Yeni ENV gerektirmez (`AUTH_SERVICE_URL`, `JWT_ISSUER` mevcut; servis token'ı doğrulaması yalnız JWKS ister).
