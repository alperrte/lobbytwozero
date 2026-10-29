# Auth Entegrasyonu 01 — Kullanıcı kimliği: JWT doğrulaması

> Tarih: 2026-10-27 · Servis: community-service (Nisa) · Kaynaklar: `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md` §3–§6, `docs-auth/servis-sozlesmeleri.md` §1–§2
> Temel kural: **Auth Service'in yapısı değişmedi ve değiştirilmeyecek.** Değişen tek şey community'nin Auth'a uyması.

## 1. Önceki durum ve değişiklik

| | Önce | Şimdi |
| --- | --- | --- |
| Token doğrulama | `JwtDecoder` fail-closed: hiçbir token kabul edilmiyordu (her kimlikli istek 401) | Auth'un JWKS anahtarıyla gerçek doğrulama |
| Kullanıcı kimliği | `sub` claim'i (varsayım) | `sub` claim'i (sözleşmeyle **doğrulandı**) |
| Moderatör rolü | `roles` claim'i (varsayım) | `roles` claim'i (sözleşmeyle **doğrulandı**: öneksiz `MODERATOR`, `ADMIN`) |
| Token türü ayrımı | yok | yola göre: `/api/v1/community/**` kullanıcı token'ı, `/internal/**` servis token'ı |

## 2. Auth'un sözleşmesi (bizim uyduğumuz kurallar)

Kullanıcı token'ı (`access token`) Auth'un kendi doğrulayıcısıyla **birebir aynı** kurallarla doğrulanır; fazladan kural eklenmedi:

| Alan | Beklenen | Reddedilen örnekler |
| --- | --- | --- |
| İmza | yalnız **RS256**, Auth'un JWKS anahtarı (`kid` ile) | HS256, `alg=none`, bilinmeyen `kid` |
| `typ` (başlık) | `at+jwt` | `svc+jwt` (servis token'ı) |
| `iss` | `ltz-auth` | başka yayıncı |
| `aud` | `ltz-api` içermeli | `community-service` (servis token'ının hedefi) |
| `exp`, `iat`, `sub` | zorunlu | eksik olan |
| Süre | `exp`/`nbf` doğrulanır, saat sapması ≤ 30 sn | süresi dolmuş, henüz geçerli olmayan |

Kullanılan claim'ler: `sub` → kullanıcı kimliği (UUID); `roles` → rol listesi (`PLAYER`, `CONTENT_CREATOR`, `GAME_DEVELOPER`, `MODERATOR`, `ADMIN`). **Aranmayan** claim'ler: e-posta, kullanıcı adı, hesap durumu, `scope` (rehber §3.3: bunlar token'da yok).

## 3. Nasıl çalışıyor (kod haritası)

```
İstek ──► Spring Security filtre zinciri (SecurityConfig)
            │  yol /internal/** mi?
            ├─ hayır ─► userTokenDecoder  (typ=at+jwt, aud=ltz-api)   ─► ROLE_<roles>
            └─ evet  ─► serviceTokenDecoder (typ=svc+jwt, aud=community-service) ─► SCOPE_<scope>
                         (bkz. auth-integration-02.md)
            ▼
         Controller  ─► CurrentUserPort (SecurityContextCurrentUser): sub → userId, roles → moderatör mü?
```

| Dosya | Görev |
| --- | --- |
| `infrastructure/config/JwtConfig` | İki `JwtDecoder` (kullanıcı/servis) ve doğrulayıcı kuralları. JWKS'i Auth'tan çeker, önbelleğe alır, bilinmeyen `kid`'de yeniler. İmzadan hemen sonra ham JWT'de `iat`/`exp`/`sub` zorunluluğunu denetler |
| `infrastructure/security/SecurityConfig` | Yola göre token türü seçimi, "varsayılan reddet", hangi uç hangi yetkiyi ister |
| `infrastructure/security/TokenClaims` | Auth sözleşmesinden gelen sabitler (`at+jwt`, `svc+jwt`, `account:erase`, ...). Değerlerin sahibi Auth; biz değiştirmeyiz |
| `infrastructure/security/SecurityContextCurrentUser` | Doğrulanmış token'dan kullanıcıyı verir. İstemcinin gönderdiği `userId`, `X-User-Id`, `X-Role` gibi başlıklar **hiçbir zaman** kullanılmaz |
| `infrastructure/security/ProblemDetailSecurityHandler` | 401/403 hatalarını `ProblemDetail` (RFC 9457) olarak döner; token/claim değeri yanıta yazılmaz |

## 4. Yapılandırma (yeni ENV yok)

`application.properties` (`ltz.community.auth.*`), mevcut Compose değişkenlerinden beslenir:

| Özellik | Kaynak | Varsayılan |
| --- | --- | --- |
| `jwks-uri` | `AUTH_SERVICE_URL` + `/api/v1/auth/.well-known/jwks.json` | `http://auth-service:8080/...` |
| `issuer` | `JWT_ISSUER` | `ltz-auth` |
| `audience` | `JWT_AUDIENCE` | `ltz-api` |
| `service-audience` | sabit (servisin kendi adı) | `community-service` |

`AUTH_SERVICE_URL`, `JWT_ISSUER`, `JWT_AUDIENCE` Compose'ta tüm backend servislerine zaten veriliyor; `.env.example` değişmedi (docs/SECURITY.md §1: yeni ENV onay gerektirir, gerekmedi).

## 5. Neden böyle? (öğretici notlar)

- **Gateway'e güvenmiyoruz.** Gateway çerezi `Authorization: Bearer`'a çevirir ama servis token'ı yine **kendisi** doğrular (SECURITY.md §4). Bir iç ağ saldırganı Gateway'i atlayıp servise doğrudan vurabilir.
- **İmza tek başına yetmez.** `iss`, `aud`, `typ` doğrulanmazsa Auth'un *başka amaçla* imzaladığı bir token (örn. servis token'ı) kullanıcı gibi kabul edilebilir. `typ` ve `aud` bu yüzden zorunlu.
- **`iat` neden ham JWT'de denetleniyor?** Spring, `iat` eksikse kendiliğinden `exp - 1 sn` atar; yalnız Spring'in `Jwt` nesnesine bakan bir doğrulayıcı "iat var" sanır. Bu açık testle yakalandı (iat'sız token kabul edilmişti); imza doğrulamasından hemen sonra Nimbus'un `DefaultJWTClaimsVerifier`'ı ile ham haliyle denetleniyor.
- **Sahiplik yine bizim işimiz.** JWT "bu kişi kim" ve "bu rolde mi" sorusunu yanıtlar; "bu gönderi bu kişinin mi" sorusunu community kendi DB'sinde yanıtlar (`PostAccess`, `CommentService`; IDOR kontrolü).
- **Gecikme sınırı.** Rol iptali, askı/yasak gibi değişiklikler en çok access token ömrü (varsayılan 5 dk) kadar gecikmeyle ulaşır (rehber §7). Community bunu kabul eder; ek önlem talebi `auth-requests-03.md`'de.

## 6. Davranış tablosu (kullanıcı uçları)

| Durum | Sonuç |
| --- | --- |
| Misafir, herkese açık okuma ucu (`GET` akış/detay/yorum/forum) | 200 (kimlik gerekmez) |
| Geçerli kullanıcı token'ı, yazma ucu | İşlem; kimlik `sub`'dan |
| Token yok, yazma ucu | 401 `ProblemDetail` |
| **Bozuk/geçersiz Bearer, herkese açık uç** | 401 (Spring geçersiz token'ı misafir saymaz) |
| Servis token'ı (`svc+jwt`) kullanıcı ucunda | 401 |
| `aud` başka (`baska-api`), `iss` başka, süresi dolmuş, HS256, `alg=none`, bilinmeyen `kid` | 401 |
| Geçerli token, yetersiz rol (örn. `PLAYER` ile forum oluşturma) | 403 |
| Başkasının kaynağı | 403 (görünür kaynak) veya 404 (görünmeyen kaynak; varlığı sızdırılmaz) |

## 7. Testler

| Test | Ne kanıtlıyor |
| --- | --- |
| `JwtConfigTest` | Gerçek RS256 imzalı token'larla her kuralın kabul ve ret durumu; JWKS dönüşümü (yeni anahtar, bilinmeyen `kid` sonrası kabul) |
| `InternalEndpointSecurityTest` | Gerçek güvenlik zinciri + yerel JWKS: kullanıcı ucu gerçek kullanıcı token'ını kabul eder, servis token'ını reddeder; misafir okuma açık, sahte Bearer 401 |
| `SecurityContextCurrentUserTest` | `sub` ve `roles` okuma, sahte/eksik rollerin fail-closed davranışı |
| Mevcut MockMvc testleri | `jwt()` ile yetkilendirilmiş kimlik; uç davranışı (decoder'dan bağımsız) |

Testlerde gerçek anahtar/token yoktur: `AuthTestSupport` her çalıştırmada geçici RSA anahtarı üretir ve yerel bir JWKS sunucusu açar.

## 8. Durum ve açık noktalar

- Kod ve testler **tamam**.
- **NOT VERIFIED:** Çalışan Auth Service ile (gerçek JWKS, gerçek token) uçtan uca deneme yapılmadı. Dev ortamında Auth'tan alınan gerçek bir access token'la `GET/PUT` denemesi Auth sahibiyle birlikte yapılmalı.
- Rol iptal gecikmesi ve JWKS önbelleği soruları: `auth-service-talepleri/auth-requests-03.md`.
