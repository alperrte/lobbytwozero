# Auth Entegrasyonu 03 — Olay girişi (`/internal/events`), giden olaylar ve açık noktalar

> Tarih: 2026-10-27 · Servis: community-service (Nisa)
> Önceki belgeler: `auth-integration-01.md` (kullanıcı token'ı), `auth-integration-02.md` (servis token'ı, hesap silme). Muhatap taleplerin tamamı: `auth-service-talepleri/auth-requests-01..03.md`.

> **Güncelleme (2026-10-29):** Bu belge birinci aşamanın (`entegrasyon-v1`) kaydıdır ve olduğu gibi saklanır. §3–§4'te "bekliyor / relay kapalı" denen giden token ve `AccountStatusChanged` alıcısı ikinci aşamada tamamlandı: bkz. `../auth-integration-05.md`.

## 1. Bağlam

Faz 4'te community, diğer servislerle **olay** üzerinden konuşacak şekilde yazıldı (`docs/architecture.md` §6.2, outbox/inbox). Auth'un servis kimliği sözleşmesi (D4) o sırada henüz bilinmiyordu; bu yüzden olay girişi bir **varsayım** (`SCOPE_internal.events`) üzerine kuruluydu. Auth belgeleri okunduktan sonra bu uç, Auth'un gerçek modeline (servis token'ı) **hizalandı**.

## 2. Önceki ve şimdiki yetkilendirme

| | Önce (varsayım) | Şimdi (Auth sözleşmesine hizalı) |
| --- | --- | --- |
| Kimlik | `SCOPE_internal.events` yetkisi taşıyan "bir" JWT | Auth'un imzaladığı **servis token'ı** (`svc+jwt`, `aud=community-service`, ömür ≤ 60 sn, `jti`) |
| Kapsam | `internal.events` (uydurma) | `events:ingest` (**öneri**, Auth'tan talep edildi) |
| Çağıranın kimliği | Zarftaki `producer` alanı (kendi beyanı) | Token'ın `sub` değeri **tam olarak `svc:<producer>`** (kanıtlanmış kimlik) |
| Diğer `/internal/**` | `SCOPE_internal.events` ile erişilebilir | **denyAll** (yalnız tanımlı iki uç açık) |

**Neden `sub` = `svc:<producer>` kuralı?** Zarf kendi başına güvenilmez: `{"producer":"admin-service"}` yazmak bedava. Token ise Auth tarafından imzalanır. Bu kural olmasa `svc:social-service` token'ı olan biri "moderasyon kararı" gönderip herhangi bir içeriği gizleyebilirdi. Kural ile `ModerationActionTaken` yalnız `svc:admin-service`, `UserBlocked`/`UserUnblocked` yalnız `svc:social-service` token'ıyla kabul edilir (zarf-tür eşleşmesi ayrıca `InboundEventService`'te).

## 3. Hangi olaylar kimden/kime

| Olay | Yön | Kimlik | Durum |
| --- | --- | --- | --- |
| `ModerationActionTaken` | admin-service → community | `svc:admin-service` + `events:ingest` | Community hazır; Auth bu token'ı üretmiyor → **canlıda kapalı** |
| `UserBlocked` / `UserUnblocked` | social-service → community | `svc:social-service` + `events:ingest` | Aynı |
| `ContentReported` | community → admin-service | community'nin servis token'ı | Outbox'a yazılıyor; **relay kapalı** (token edinme yolu yok) |
| `CommentCreated`, `PostLiked`, `PostReposted` | community → notification-service | community'nin servis token'ı | Aynı |
| `AccountStatusChanged` (D3) | auth → community | `svc:auth-service` | Henüz tanımlı değil (`auth-requests-03.md`) |

Sonuç: community'nin kodu ve testleri hazır, ama **iki yönlü olay akışı Auth'un yeni servis token'ı türlerini vermesini bekliyor**. Hiçbir veri kaybolmuyor: giden olaylar `PENDING` bekliyor; gelen olaylar henüz ulaşamıyor.

## 4. Neden relay hâlâ kapalı?

Outbox relay'i (`ltz.outbox.relay.enabled=false`) alıcı servislere token'sız çağrı yapardı; alıcılar bunu (doğru olarak) 401 ile reddeder ve olaylar boşuna `FAILED` olabilirdi. Token edinme yolu netleşmeden (`auth-requests-01.md` §2.2) açmak zararlı. Açma adımı hazır: relay'e token ekleme noktası `EventDeliveryClient.deliver(...)`.

## 5. Test ortamı güvenliği (bu entegrasyonla birlikte yapılan ek)

`main`'in yeni pre-push düzeni backend testlerini paylaşılan geliştirme PostgreSQL sunucusuna yönlendiriyor. Community testleri tabloları temizliyor ve "boş veritabanı" varsayıyor; paylaşılan `community_db`'ye dokunmaları kabul edilemezdi. Bu yüzden `PostgreSqlIntegrationTest` artık aynı sunucuda **rastgele adlı geçici bir veritabanı** oluşturup test bitince siliyor (`ltz_community_test_*`); `community_db` hiç değişmiyor (doğrulandı: migration sürümü ve tablo sayısı aynı). Oluşturma yetkisi yoksa Testcontainers'a düşer.

## 6. Kerem'in cevapları (2026-10-28) ve güncel durum

- Servis token'ı başka çağıran/kapsamlara **açılabilir** ama imtiyaz varsayılan değil: her (çağıran → hedef → kapsam) üçlüsü ayrı tanımlanır. `events:ingest` adı uygun bulundu. Community'nin istek listesi: `auth-requests-04.md` §2.
- Giden çağrılarda community Auth'un **token ucundan** token alır; community'ye ayrı istemci kimliği + gizli anahtar tanımlanır (ortam değişkeni; **yeni ENV, onay gerekir**).
- Yeni Auth özellikleri (silme bekleme süresi, JWKS'te ek anahtar, genişletilmiş token ucu) `feature/auth-service` dalında; main/dev'e girişi bekleniyor.
- `AccountStatusChanged` planlı, tarih yok; olay gelene kadar community gizleme yapmaz.

## 7. Yapılacaklar (sırayla)

1. ~~**Kerem:** `auth-requests-01.md` ve `-02.md` yanıtlanır~~ **(yapıldı, 2026-10-28).** Sıradaki: Nisa istek listesini (`auth-requests-04.md` §2) Kerem'e gönderir; Alper rapor saklama süresini belirler.
2. **Nisa:** Auth cevabına göre kapsam adı sabiti (`TokenClaims.EVENT_INGEST_SCOPE`) ve relay token ekleme noktası güncellenir; relay açılır.
3. **Birlikte:** dev'de gerçek bir test hesabı silinir, `community` görevinin `CONFIRMED` olduğu görülür; gerçek `ServiceTokenIssuer` çıktısıyla uyumluluk denenir.
4. **Alper (repo sahibi):** Gateway'in `/internal/**` yolunu route etmediği teyit edilir.
5. **Nisa:** `docs/api.md` ve bu klasör, Auth'un cevaplarına göre güncellenir.

## 8. Doğrulama sonucu (2026-10-28)

- `.\pre-push\pre-push.cmd community-service`: 423 test, 0 hata, `BACKEND TEST CHECK PASSED`, JaCoCo %98,96.
- SonarQube (yalnız community-service, proje anahtarı `ltz-community-service`): Quality Gate OK, açık sorun 0, güvenlik/güvenilirlik/bakım A, kapsam %97,7, tekrar %0. Not: Sonar'ın standart komutu (`sonar.cmd scan dev`) tüm backend'i derler; `game`, `social` ve `hardware` servislerinin Testcontainers tabanlı testleri bu makinede düştüğü için standart komut çalışmadı. Bu servisler community'nin kapsamı dışındadır.
- `main`'in yeni pre-push/Sonar düzeni Compose'taki yeni Auth anahtarlarını (`AUTH_JWT_PRIVATE_KEY` vb.) zorunlu kıldığı için yerel `.env.dev` ekibin belgelenmiş komutuyla (`node ops/generate-auth-dev-keys.mjs`, onayınla) tamamlandı; dosyalar git dışındadır.
