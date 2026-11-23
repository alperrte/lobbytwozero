# Auth Entegrasyonu 02 — Servis kimliği (D4) ve hesap silme iç ucu

> Tarih: 2026-10-27 · Servis: community-service (Nisa) · Kaynaklar: `docs-auth/servis-sozlesmeleri.md` §3, `docs-auth/d4-ekip-notlari/01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md`, `04_NISA_community_content_event.md`
> Auth'un yapısı değişmedi: Auth bu sözleşmenin sahibi, community yalnız uyuyor.

## 1. Ne isteniyordu

Bir kullanıcı hesabını sildirmek istediğinde Auth **16 servise** şu çağrıyı yapıyor ve hepsi `204` dönmeden hesabı `DELETED` yapmıyor:

```http
DELETE {COMMUNITY_SERVICE_URL}/internal/v1/accounts/{accountId}
Authorization: Bearer <Auth'un imzaladığı servis token'ı>
```

Community bu ucu hiç açmamıştı; bu, hesap silmeyi bloke ediyordu. Şimdi hazır.

## 2. Servis token'ı (D4): ne ve neden

Servisler arası çağrıda kimlik "hangi **servis** çağırıyor" sorusudur; kullanıcı token'ından farklı bir token türüdür. Auth her çağrı için **yeni**, 30 sn ömürlü bir JWT imzalar. Community hepsini doğrular (rehber §2):

| Alan | Değer | Neden denetlenir |
| --- | --- | --- |
| `typ` | `svc+jwt` | Kullanıcı token'ı (`at+jwt`) iç uçta geçmesin |
| `alg` / `kid` | RS256 / Auth JWKS | HS256 veya imzasız token reddedilsin |
| `iss` | `ltz-auth` | Başka yayıncı reddedilsin |
| `aud` | `community-service` (tek değer) | Başka servise yazılmış token (örn. `social-service`) bizde geçmesin; `ltz-api` iç uçta kabul edilmez |
| `sub` | `svc:auth-service` (**tam eşleşme**) | Yalnız Auth silme yetkisine sahip; `svc:admin-service` olsa bile reddedilir |
| `scope` | `account:erase` | Uç bu kapsamı ister |
| `account_id` | silinecek hesap | Yoldaki `{accountId}` ile aynı olmalı: **çalınan token başka hesabı silemez** |
| `iat` / `exp` | `exp - iat ≤ 60 sn` | Uzun ömürlü token reddedilsin |
| `jti` | var olmalı | Her çağrının kimliği |
| `roles`, `sid`, `ver`, `amr` | **yok** | Servis token'ından kullanıcı yetkisi türetilmez |

**Doğrulamanın üç katmanı** (hangisinin nerede olduğunu bilmek hata ayıklamayı kolaylaştırır):
1. `JwtConfig.serviceTokenDecoder`: imza, `typ`, `iss`, `aud`, süre, `jti`, ömür, `sub` öneki (`svc:`). Hata → **401**.
2. `SecurityConfig`: `DELETE /internal/v1/accounts/*` için `SCOPE_account:erase` yetkisi. Hata → **403**.
3. `AccountErasureController`: `sub == svc:auth-service` ve `account_id == yoldaki id`. Hata → **403** (`ProblemDetail`).

Neden son iki kural decoder'da değil controller'da? Decoder ileride başka servis çağrıcılarına da hizmet edebilir; ama **silme** yalnız Auth'a ve yalnız token'ın bağlandığı hesaba aittir (rehber §4.4).

## 3. Güvenlik zinciri: token türü yola göre

```
/api/v1/community/**  ─► kullanıcı token'ı (at+jwt)  → servis token'ı gelirse 401
/internal/**          ─► servis token'ı (svc+jwt)    → kullanıcı token'ı gelirse 401
  DELETE /internal/v1/accounts/{id}  → SCOPE_account:erase
  POST   /internal/events            → SCOPE_events:ingest   (bkz. 03 ve auth-requests-01)
  başka her /internal/** yol/yöntem  → denyAll (403)
```

Gateway `/internal/**` için route eklemez (Gateway yalnız `/api/v1/<servis>/**` yönlendirir). Bu, Gateway sahibiyle teyit edilecek bir varsayımdır.

## 4. Hesap silme: veri kararı (tablo tablo)

"Kişisel veri" = hesabı tanımlayan her şey. Karar kuralı: **hesabın kendi verisi silinir/anonimleşir, başkalarının verisi silinmez** (Kerem'in notu). Tek `@Transactional`, set tabanlı SQL; yarım silme olmaz, ikinci çağrı no-op.

| Tablo / veri | Karar | Not |
| --- | --- | --- |
| `posts` (yazarı hesap) | **Anonimleştir:** `author_id` → `00000000-…`, `body` → `[removed]` (repost notu `null`), `status=DELETED`, `deleted_at`; görseller (`post_images`) silinir | Satır silinmez: başkalarının yorum/beğeni/repostu FK ile bağlı. Soft delete'te saklanan eski metin de temizlenir |
| `comments` (yazarı hesap) | **Anonimleştir:** aynı şekilde; görünür yorumlar gönderinin `comment_count`'undan düşer | Yanıt ağacı korunur (başkasının yanıtı silinmez) |
| `post_likes` (hesabın beğenisi) | **Sil**, `like_count` düşer | |
| `community_reports` (hesabın yaptığı) | **Sil** | Raporlayan kimliği kişisel veri |
| `community_reports` (hesabın içeriği hakkında) | **Sakla**, yalnız `target_author_id` anonim | Moderasyon izi kalır; kişi tanımlanmaz. **Onay bekliyor:** `auth-requests-02.md` |
| `user_blocks` | **Sil** (iki yön) | social yansıması |
| `forums.created_by` | **Anonimleştir** | Forum içeriği kişisel değil |
| `outbox_events` | Hesabın kimliğini taşıyan **hepsini sil** | Bekleyen/başarısız/teslim edilmiş |
| `inbox_events` | Dokunulmaz | Yalnız olay kimliği; kişisel veri yok |
| Başkasının hesabın gönderisine yorumu/beğenisi | **Dokunulmaz** | Başkasının verisi |

**Şema değişmedi:** community'nin migration aralığı (V91–V100) dolu. Yer tutucu metin mevcut CHECK kısıtlarını sağlar (`ck_posts_body_length`: repost dışında 1–5000 karakter; repostta `null` serbest), anonim `author_id` ise FK'siz bir referanstır. `ux_posts_one_repost_per_user` kısmi UNIQUE indeksi `DELETED` satırları dışladığı için anonimleşen repostlar indeksi bozmaz.

## 5. Sınırlar ve dikkat

- **5 saniye:** Auth bu sürede yanıt bekler. İşlem set tabanlı ve indeksli (`author_id`, `user_id`) olduğundan tipik hesapta saniyenin altındadır; `202` yasak olduğu için arka plana atılmaz. Çok büyük hesaplar için `503` + kısmi ilerleme gerekirse sonradan eklenir (soru: `auth-requests-02.md` §6.3; Kerem bu soruyu ayrıca yanıtlamadı, tek işlem şimdilik yeterli).
- **Loglama:** yalnız `accountId`. Token, `Authorization` başlığı, içerik asla yazılmaz.
- **Eşzamanlılık:** silme sırasında hesap yeni içerik üretemez (Auth hesabı `DELETION_REQUESTED`'e alır, token vermeyi durdurur); yine de 5 dk'lık token gecikmesi içinde gelen bir yazma, ikinci silme çağrısıyla (Auth yeniden dener) temizlenir.

## 6. Testler

| Test | Kanıt |
| --- | --- |
| `InternalEndpointSecurityTest` | Rehber §5 tablosunun community karşılığı: geçerli token → 204 (ve ikinci çağrı 204); token yok, kullanıcı token'ı, yanlış `aud`/`iss`, süresi dolmuş, ömür > 60 sn, `jti`/`iat` yok, HS256/`alg=none`/bilinmeyen `kid` → 401; yanlış kapsam, `sub=svc:admin-service`, `account_id` yok/farklı → 403; tanımsız `/internal/**` yol/yöntem → 403; geçersiz UUID → 400 |
| `AccountErasureControllerTest` | Controller kuralları (sub tam eşleşme, `account_id`, kapsam yoksa controller'a ulaşılmaz) |
| `AccountDataEraserIntegrationTest` (gerçek PostgreSQL) | Hesabın hiçbir izi kalmaz; başkalarının verisi/yanıt ilişkisi bozulmaz; sayaçlar doğru düşer; raporlar, engeller, forum, outbox kuralları; idempotentlik; var olmayan hesap → no-op |

Rehberdeki 17 senaryonun 16'sı karşılandı; **bir istisna var:** #17 "Auth'un gerçek `ServiceTokenIssuer`'ının ürettiği token'la uyumluluk" bu repoda Auth'un kodu/anahtarı olmadan yapılamaz; test token'ları Auth'un belgelenmiş biçimiyle üretilir. Gerçek uyumluluk Auth sahibiyle birlikte denenecek (**NOT VERIFIED**).

## 7. "Bitti sayılması" kontrol listesi (rehber §6)

| # | Madde | Durum |
| --- | --- | --- |
| 1 | Uç + iki ek denetim + testler yeşil; servis kapsamı ≥ %80 | ✔ (kapsam ~%99) |
| 2 | `docs/api.md` kaydı (Durum, Sorumlu, Tüketici=`auth-service`, Yetkilendirme, `204`, hatalar, Bağlantı, Test) | ✔ ("Hesap Verisi Silme (İç Uç)") |
| 3 | `.\pre-push\pre-push.cmd community-service` yeşil | ✔ `BACKEND TEST CHECK PASSED` |
| 4 | Saklama istisnası belgede gerekçesiyle ve Kerem'le kararlaştırılmış | ◐ Kerem: anonimleştirme (sabit kimlik, `[removed]`) Auth sözleşmesiyle çelişmiyor (2026-10-28). **Rapor saklama süresi/hukuki gerekçe Alper'in kararı**, bekliyor (`auth-requests-04.md` §3) |
| 5 | Kerem'e haber: dev'de test hesabı silinip `community` görevi `CONFIRMED` | ✘ Bekliyor. Şartlar (Kerem, 2026-10-28): yeni Auth dev'e çıkmış olmalı, dev'de silme bekleme süresi `PT0S` (ekip ekleyecek), community container'ı güncel imajla çalışmalı. Kontrol listesi: `auth-requests-04.md` §4 |
