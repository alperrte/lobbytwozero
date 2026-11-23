# User Talebi 01 — Arkadaşlık isteği ve engel hedefinin varlık doğrulaması

> Tarih: 2026-11-20 · Gönderen: Umut (social-service) → `user-service` (Kerem)
> Durum: **TALEP / ÖNERİ — User'da uygulanmadı.** Sözleşme Messaging talebiyle **ortaktır**; ayrıntı ve açık sorular orada: [docs-messaging/user-service/talep/req-01.md](../../../docs-messaging/user-service/talep/req-01.md) §3.
> Gereksinimler: **SOC-01** (istek gönderme), **SOC-09/10** (engel), **SOC-15** (servis sınırları).

## 1. İhtiyaç

Social, `POST /api/v1/social/friend-requests {receiverId}`, `by-code` ve `POST /blocks {blockedId}` çağrılarında hedef kimliğin gerçek bir hesap olup olmadığını **doğrulamıyor** (`docs-social/TASK-04.md` §4 madde 5, `TASK-06.md` §7 madde 3). Silinmiş ya da olmayan kimliğe istek/engel kaydı oluşabilir; Auth silmesi sonradan temizler ama yeni kayıt engellenmez (`docs/api.md` SOCIAL hesap silme notu: "kullanıcı varlık doğrulaması hâlâ PENDING").

## 2. İstenen

Messaging talebindeki toplu uç: `POST /internal/v1/users/status` → `{results:[{userId, status: ACTIVE|UNAVAILABLE}]}`, `scope=users:status:read`, `sub=svc:social-service`.

Social'ın ek notları:

- Çağrılar tek kimlik içindir; toplu uç tek elemanlı kullanılır.
- Social çağıran davranışı: `UNAVAILABLE` → istekte `404 USER_NOT_FOUND` (engel varlığı sızdırmaz kuralını bozmayacak genel hata), engelde aynı. User'a ulaşılamazsa **fail-closed** `503`; mevcut arkadaşlık/engel okumaları etkilenmez.
- Auth tarafı: `social-service → user-service` zaten `privacy:read` + `events:ingest` ile tanımlı; yeni kapsam (`users:status:read`) eklenmeli (Kerem, aynı kişi). Yeni ENV yok.
- Kabul etmeyi `UNAVAILABLE` sayılan askıdaki hesap için de karar istiyoruz: askıdaki kullanıcıya istek gönderilebilsin mi? Önerimiz **hayır** (görünürlük kuralıyla tutarlı).

## 3. Social tarafında hazırlık

`UserPrivacyClient` kalıbıyla (`ServiceTokenPort`, fail-closed) yeni bir istemci eklenir; uç ve Auth kapsamı gelince yapılır. Bu uç gelene kadar Social'da değişiklik yok.

## 4. Kabul kriterleri

1. Var olan aktif hedefe istek/engel çalışır; silinmiş/olmayan hedefe `404`.
2. User kapalıyken yeni istek/engel `503`, mevcut arkadaş listesi ve engel sorgusu çalışmaya devam eder.
3. Hata yanıtları engelin/hesabın varlığını ayrıştırmaz.
