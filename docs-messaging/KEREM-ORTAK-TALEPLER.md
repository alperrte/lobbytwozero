# Kerem'e Ortak Talepler — Umut'un Servisleri (social, messaging, lfp)

> Tarih: 2026-11-20 · Gönderen: Umut · Alıcı: Kerem (auth-service + user-service)
> Bu belge, ayrı ayrı yazılmış talepleri tek yerde toplar. Ayrıntı ve kabul kriterleri bağlantılı dosyalardadır. Hepsi **talep/öneri**; Auth ve User'da uygulanmış bir şey yok.

## 1. Özet

| # | Hedef | Ne | Hangi servisimiz | Öncelik | Ayrıntı |
| --- | --- | --- | --- | --- | --- |
| 1 | Auth | `messaging-service → notification-service: events:ingest` çifti | messaging (MSG-16) | Orta | [talep-01](../docs-auth/verilen-cevaplar/messaging-service/talep-01-notification-events-ingest.md) |
| 2 | Auth | `social-service → notification-service: events:ingest` çifti | social (SOC-13) | Orta | [talep-02](../docs-auth/verilen-cevaplar/social-service/talep-02-notification-events-ingest.md) |
| 3 | User | Kullanıcı varlığı/durumu için toplu iç uç `POST /internal/v1/users/status` | messaging, social (lfp ihtiyaç duymuyor) | Orta | [messaging req-01](user-service/talep/req-01.md) · [social req-01](../docs-social/user-service/talep/req-01.md) |
| 4 | Auth | Yeni kapsam `users:status:read` için `messaging-service` ve `social-service` → `user-service` çiftleri (#3'ün parçası) | messaging, social | Orta (#3'e bağlı) | #3 |
| 5 | Bilgi | Dört küçük duyuru/güncelleme (§3) | hepsi | Düşük | §3 |

Sıralama önerisi: #3 ve #4 birlikte karar verilirse tek seferde yapılır; #1 ve #2 notification alıcı ucu gelmeden de Auth'ta tanımlanabilir (tanım zararsız, Messaging ve Social adaptörleri teslimi kapalı tutuyor).

## 2. Kısa açıklamalar

**#1 ve #2 — notification çiftleri.** Mevcut `messaging-service` ve `social-service` istemcileri ve gizli anahtarları yeterli, yeni ENV yok. İstenen iki satır:

```properties
ltz.auth.service-clients.clients.messaging-service.targets.notification-service=events:ingest
ltz.auth.service-clients.clients.social-service.targets.notification-service=events:ingest
```

Notification alıcı ucu ayrıca eksik (ortak servis, sahibi belirlenmedi). Bu iki çift o uç için gereken Auth tarafıdır. Notification'a olan talepler: [Messaging](notification-service/talep/req-01.md), [Social](../docs-social/notification-service/talep/req-01.md).

**#3 ve #4 — kullanıcı varlığı.** Bugün hiçbir serviste hedef kimliğin gerçek hesap olup olmadığı doğrulanmıyor: silinmiş/olmayan `userId` ile birebir sohbet, arkadaşlık isteği ve engel kaydı oluşabiliyor. Önerimiz Social'ın `blocks:check` kalıbında toplu bir uç (yalnız `ACTIVE | UNAVAILABLE`, profil verisi yok):

```text
POST /internal/v1/users/status   { "userIds": [≤100] }
→ { "results": [ { "userId", "status": "ACTIVE | UNAVAILABLE" } ] }
aud=user-service, scope=users:status:read (kapsam adını sen belirleyebilirsin)
```

Senden karar istediğimiz sorular:
1. Profili henüz oluşmamış (yeni kayıt) hesap `ACTIVE` sayılacak mı? Aksi halde yeni kullanıcılar kısa süre sohbet/istek alamaz.
2. Askıdaki/yasaklı hesap `UNAVAILABLE` mi? Önerimiz evet.
3. Varlık bilgisinin kaynağı User mı, Auth mu? Biz User'ı önerdik (`docs-messaging/TASK-08.md` §6); Auth hesap durumunun asıl sahibi olduğu için başka bir yön seçersen uyum sağlarız.

LFP bu uca ihtiyaç duymuyor: ilan sahibi token `sub`'ından geliyor, iletişim hedefi Messaging'de doğrulanacak.

## 3. Bilgi / küçük güncellemeler (talep değil)

1. **Messaging silme bilgilendirmesi kapanabilir.** Auth → Messaging hesap silme, gerçek Auth token'ıyla canlı doğrulandı (2026-11-13, `messaging` görevi `CONFIRMED`). `docs-auth/verilen-cevaplar/messaging-service/bilgilendirme-2026-11-05.md` kapatılabilir.
2. **`docs-auth/entegrasyon/README.md`** tablosunda `messaging-service` ve `lfp-service` satırları hâlâ "İskelet". Önerilen: messaging "Aktif, uçtan uca doğrulandı 2026-11-13 (`docs-messaging/TASK-08.md`)", lfp "Aktif, uçtan uca doğrulandı 2026-11-17 (`docs-lfp/TASK-04.md`)". `docs-auth/verilen-cevaplar/lfp-service/README.md` de hâlâ "İskelet (henüz iş yok)" diyor.
3. **user-service `ltz.user.social.projection-enabled=false`** (2026-11-20 itibarıyla). Social artık `FriendshipChanged/UserBlocked/UserUnblocked` olaylarını User'a teslim ediyor ve canlı doğrulandı (`docs-social/TASK-06.md` §5–6). Yansımanın gizlilik kararlarında kullanılması için bayrağı açmak User sahibinin (senin) kararı. `docs/api.md` USER gizlilik kararı ucundaki "Gerçek tüketicilerle uçtan uca: NOT VERIFIED" notu Social için güncellenebilir.
4. **Yerel `auth_db` checksum sıfırlaması.** `origin/main`'deki birleştirilmiş Auth migration'ları, eski yerel veritabanlarında Flyway checksum hatası veriyor (V61–V67). `docs-auth/migration-yazim-kurallari.md`'deki sıfırlama adımının ekibe tekrar duyurulması iyi olur (Messaging ve LFP canlı testlerinde ikimiz de karşılaştık).

## 4. Kimden ne bekleniyor (Umut tarafında)

Bu talepler Auth/User'dan cevap geldiğinde yapılacak işler:

| Gelince | Bizde yapılacak |
| --- | --- |
| #1 + notification alıcı ucu | `LTZ_MESSAGING_OUTBOX_ADAPTER=notification`, canlı doğrulama |
| #2 + notification alıcı ucu | Social `FanOutEventPublisher`'a notification hedefi, `SOCIAL_OUTBOX_RELAY_ENABLED=true`, canlı doğrulama |
| #3 + #4 | Messaging ve Social'a User istemcisi (fail-closed), `docs/api.md` kayıtları, testler |

## 5. Kerem'e gönderilecek mesaj (kopyala-yapıştır)

```text
Kerem, Social/Messaging/LFP tarafında senden beklediklerimi tek dosyada topladım:
docs-messaging/KEREM-ORTAK-TALEPLER.md

Özet:
1) Auth çifti: messaging-service ve social-service -> notification-service, scope events:ingest
   (yeni ENV yok; notification alıcı ucu ayrıca bekliyor, tanım şimdi yapılabilir).
2) User: kullanıcı varlığı için toplu iç uç (POST /internal/v1/users/status -> ACTIVE|UNAVAILABLE),
   messaging ve social için ayrı Auth çifti + yeni kapsam. Üç açık soru dosyanın §2'sinde (yeni hesap
   profil gecikmesi, askıdaki hesap, kaynak User mı Auth mı).
3) Küçük bilgi: messaging silme bilgilendirmesi kapanabilir; docs-auth/entegrasyon/README.md'de
   messaging ve lfp "İskelet" yazıyor; user'daki social projection-enabled bayrağı kararı sende;
   birleşik migration yüzünden yerel auth_db sıfırlama adımını ekibe tekrar duyurmak iyi olur.
Detay ve kabul kriterleri ilgili talep dosyalarında.
```
