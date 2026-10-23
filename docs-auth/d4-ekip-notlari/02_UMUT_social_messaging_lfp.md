# Umut — Social, Messaging, LFP: Hesap Silme İç Ucu

> Tarih: 2026-10-24 · Kimden: Kerem (Auth) · Önce [01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md](01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md)'yi
> oku; bu dosya yalnız sana özel kısım.

## Özet

| Servis | Durum | Yapılacak |
| --- | --- | --- |
| **social** | Uç var (main, PR #8), Auth token'ıyla **uyumlu** | **2 eksik denetim** + 4 test (aşağıda) + api.md notu |
| **messaging** | Uç yok | Ucu aç (rehber §4), mesajlar/katılımcılık için silme kararı |
| **lfp** | Uç yok | Ucu aç (rehber §4) |

## 1. Social — uyumluluk sonucu (2026-10-23, senin gerçek kodunla)

Auth'un gerçek `ServiceTokenIssuer`'ının ürettiği token, Social'ın `JwtConfig.serviceTokenValidator("ltz-auth", "social-service", …)`
doğrulayıcısından geçti; yetki `SCOPE_account:erase` oluştu. Aynı token `aud=game-service` beklentisinde, kullanıcı token'ı
doğrulayıcısında ve süresi dolunca **reddedildi**. Yani temel doğru; iki kural eksik.

## 2. Social — eksik iki kural

**a) `sub` tam eşleşme.** Şu an `JwtConfig` yalnız `svc:` önekine bakıyor; böylece ileride `svc:admin-service` gibi başka bir
servis token'ı (doğru `aud` ve `scope` ile) Social'da hesap silebilir. Silme yetkisi yalnız Auth'tadır.

**b) `account_id` = yoldaki `{accountId}`.** Şu an controller claim'e hiç bakmıyor; çalınan/yeniden oynatılan bir token
(30 sn içinde) **başka bir hesabı** silebilir. Auth her token'ı tek hesaba bağlıyor; hedef bunu denetlemezse bağlama boşa gider.

`TokenClaims`'e ekle:

```java
public static final String AUTH_SERVICE_SUBJECT = "svc:auth-service";
public static final String ACCOUNT_ID = "account_id";
```

`AccountErasureController`:

```java
@DeleteMapping(InternalApiPaths.ACCOUNT_ERASURE)
@ResponseStatus(HttpStatus.NO_CONTENT)
public void erase(@PathVariable UUID accountId, @AuthenticationPrincipal Jwt token) {
    if (!TokenClaims.AUTH_SERVICE_SUBJECT.equals(token.getSubject())
            || !accountId.toString().equals(token.getClaimAsString(TokenClaims.ACCOUNT_ID))) {
        throw new AccessDeniedException("Service token is not valid for this erasure");
    }
    erasure.erase(accountId);
}
```

`JwtConfig`'teki `svc:` önek kuralı kalabilir (genel servis token'ı kuralı); tam eşleşme silme ucuna aittir.
`GlobalExceptionHandler`'ın `AccessDeniedException` → `403` eşlemesi zaten var.

`AccountErasureTest`'e eklenecek testler:

1. `sub=svc:admin-service` (diğer her şey geçerli) → `403`, veri silinmemiş
2. `account_id` başka bir UUID → `403`, veri silinmemiş
3. `account_id` claim'i yok → `403`, veri silinmemiş
4. Yolda büyük harfli UUID, claim'de aynı UUID küçük harfle → `204` (yol `UUID` olarak çözülüp `toString()` küçük harf verir;
   Auth claim'i her zaman küçük harf yollar). Karşılaştırmayı yanlışlıkla ham yol metnine çeviren bir değişikliği yakalar.

**Dikkat:** `IntegrationTestSupport.validServiceClaims()` şu an `account_id` üretmiyor. Controller denetimi eklenince mevcut
14 testin `204` bekleyenleri `403`'e döner. `validServiceClaims(UUID accountId)` biçimine çevirip `.claim("account_id",
accountId.toString())` ekle ve `serviceTokenValue()` çağrılarına silinen hesabı geçir. Ömrü de Auth gibi 30 sn yapabilirsin
(şu an 60; sınırda, geçerli ama Auth'un gerçek değeri 30).

## 3. Social — belge

`docs/api.md` SOCIAL "Hesap verisi silme" kaydı ve PR #8 notunda "Auth tarafında adaptör ve svc+jwt üreticisi yok; uç Auth yazılana
kadar çağrılmaz" yazıyor. Artık **geçersiz**: Auth üretici + adaptörü yazdı (`feature/auth-service`, main'e PR gelecek). Kaydın
Yetkilendirme alanına `sub=svc:auth-service` (tam) ve `account_id = {accountId}` kurallarını ekle; Hata Durumları'na `403`.

## 4. Messaging

Rehber §4'ü uygula (`ltz.messaging.auth.service-audience=messaging-service`). Karar senin ama önerim:

- Silinen hesabın **kendi** mesaj içerikleri silinir veya içerik boşaltılıp gönderen REF'i anonimleşir (karşı tarafın konuşma
  geçmişi bozulmasın diye). Hangisi seçilirse seçilsin mesaj metni ve ekleri kalmaz.
- Katılımcılık, okundu bilgisi, bildirim tercihleri, outbox olayları silinir.
- Seçimini servis belgene ve api.md kaydına yaz; anonim saklama varsa bana haber ver (rehber §3, istisna kuralı).

## 5. LFP

Rehber §4'ü uygula (`ltz.lfp.auth.service-audience=lfp-service`). Hesabın ilanları, başvuruları ve ilan/grup üyelikleri silinir;
başkasının ilanındaki üyelik satırı yalnız ilişki olarak gider. Henüz tablo yoksa da uç açılır ve `204` döner (iskelet servisler
dahil).
