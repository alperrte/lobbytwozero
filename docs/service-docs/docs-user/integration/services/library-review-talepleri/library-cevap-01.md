# Library → User entegrasyon talebine cevap (LIB-10, LIB-11, LIB-14, LIB-18)

Kimden: Kerem (user-service) · Kime: Hamza (library-service) · Tarih: 2026-11-09
Kaynak: `LTZ_Library_User_Entegrasyon_Talebi.md`
Durum: User tarafı kodlandı (yerel doğrulama). Library ile uçtan uca **NOT VERIFIED**.

---

## Özet

- LIB-11: karar ucu hazır, adaptörünü yazabilirsin. **Bayrak kapalıyken başkasının koleksiyonu 403 döner; bu bilinçli (§A).**
- LIB-10: önerdiğin model (olayla özet + Library'nin kendi public ucu) uygun, onaylıyorum.
- LIB-14: öneri rızası **User'da tutuluyor**, karar ucu `field=RECOMMENDATION` ile hazır (§H–J).
- LIB-18: dev akışı var (§K).

## A. `projection-enabled`
Şimdilik **kapalı** (sabit `false`, ENV yok). Açılış zamanı belli değil; Social ile birlikte dev'de denendikten sonra karar verilecek.
Kapalıyken sahibi olmayan izleyiciye `allowed=false` döner, yani başkası PUBLIC koleksiyonu da göremez (403). Bu bozukluk değil,
fail-closed davranış. Sahibin kendi verisi etkilenmez. `RECOMMENDATION` alanı bayraktan **bağımsızdır** (§H).

## B. Profili olmayan sahip
Teyit ve **düzeltme yapıldı**: profili olmayan sahip için `setting` varsayılan döner (Social bu alanı kendisi kullanıyor),
ama sahibi olmayan izleyiciye `allowed` artık **her zaman `false`**. Var olmayan kullanıcı gizli kullanıcıdan ayırt edilemez (ZG-10).
Sahibin kendisi için kısayol değişmedi.

## C. Etkili olma süresi (ZG-11)
Karar her istekte DB'den okunur (`readOnly` sorgu, önbellek yok). Görünürlük ve rıza değişikliği **bir sonraki karar isteğinde** etkilidir.

## D. Limit
Karar ucunda oran sınırı tanımlı değil; en çok 100 `ownerId`. Senin 3 sn bağlantı / 5 sn okuma zaman aşımın uygun. 429 politikası yok.

## E. LIB-10 modeli
Onaylandı. `LibrarySummaryChanged` alıcısı hazır: `{userId, collectionCount, completedCount, favoriteCount}`, `userId = aggregateId`,
sayılar **mutlak**, `occurredAt` ile sıra korumalı (eski olay yeniyi ezmez). Yetki: `sub=svc:library-service`, `scope=events:ingest`.
User, Library'yi çağırmaz. Profilde başkasının koleksiyon listesi gerekmez.

## F. Hesap silme
Teyit: `profile_counters` hesap silmede temizleniyor (`AccountErasureService`, `AccountErasureTest` kapsıyor). Yeni `user_consents` tablosu profil FK'si ile `ON DELETE CASCADE`.

## G. Başlangıç aktarımı
User tarafında oran sınırı yok; olaylar upsert olduğu için tekrar ve sıra bozulması zararsız. Yavaş ve sıralı göndermen yeterli.

## H–J. Öneri rızası (LIB-14)
Rızayı **User tutar**. Varsayılan: izin yok, kullanıcı geri alabilir.

Çağrı (mevcut uç, yeni alan):
```
POST {USER_SERVICE_URL}/internal/v1/privacy/decisions
{ "viewerId": "<sahip UUID>", "field": "RECOMMENDATION", "ownerIds": ["<sahip UUID>"] }
```
`allowed=true` için hepsi gerekir:
1. `viewerId` = sahip (başka izleyici ve misafir her zaman `false`).
2. Sahibin profili var ve hesabı görünür (ACTIVE/SUSPENDED; BANNED/DISABLED/silinmekte → `false`).
3. Sahip öneri rızasını vermiş.

**Sahiplik kısayolu bu alanda uygulanmaz:** rızası yoksa sahip için de `allowed=false`. Yanıt şeması aynı; `setting` izinliyse `PUBLIC`, değilse `PRIVATE`; `friends` hep `false`.
Adaptöründe `RECOMMENDATION` eşlemesi tek satır. `projection-enabled` bu alanı etkilemez, yani **bayrak kapalıyken de çalışır**.

Kullanıcı rızayı `GET/PUT /api/v1/user/me/consents` ile yönetir (Hesap ayarları sayfasında, sayfa henüz yazılmadı).
Sözleşme: `docs/api.md` → USER → "Rızalarım" ve gizlilik kararı bölümü.

Migration: `V76__create_user_consents.sql`.

## K. LIB-18 dev ortamı
`ops/dev.mjs` içinde `launchUser(trusted)` var ve `library-service → user-service` için `events:ingest privacy:read` çifti dev'de tanımlı.
Sahip ayarlarını DB'ye elle yazma, `PUT /api/v1/user/me/privacy` ve (rıza için) `PUT /api/v1/user/me/consents` ile hazırla.
Rıza testi için: önce rızasız `allowed=false`, sonra `PUT` ile rıza → `true`, geri çek → `false`.

## Doğrulanmayanlar
- Library ile uçtan uca denenmedi (adaptör yok). Rıza ucu canlı yığında gerçek Auth token'ıyla denendi ve geçti.
- `RECOMMENDATION` kararı servis token'ıyla canlıda denenmedi; entegrasyon testleri (`ConsentsTest` 13) ve `pre-push user-service` (242 test, kapsam %95,53) geçti.
