# Auth Talebi 04 — Kerem'in cevapları ve Auth'a verilecek servis token'ı istek listesi

> Tarih: 2026-10-28 · Gönderen: Kerem (Auth) → Nisa (community-service) · Durum: **CEVAPLANDI; Nisa'dan bir liste ve Alper'den bir karar bekleniyor**
> Cevapladığı talepler: `auth-requests-01.md`, `auth-requests-02.md`, `auth-requests-03.md` (6 soru).
> Uyarı (Kerem): Aşağıdaki yeni Auth özellikleri (14 gün silme bekleme süresi, JWKS'te ek anahtar yayını, servis token ucunun genişletilmesi) `feature/auth-service` dalında hazır, **main'e ve dev'e henüz girmedi**. PR açılıp birleşince devreye girer; tarih yok, Alper onayı gerekiyor.

## 1. Cevapların özeti ve community'ye etkisi

| # | Soru | Kerem'in cevabı | Community'de ne değişir / ne yapılır |
| --- | --- | --- | --- |
| 1 | Servis token'ı başka kapsamlara/çağıranlara açılacak mı? `events:ingest` uygun mu? | Genişletmeye açık ama **varsayılan imtiyaz yok**: her çağıran için ayrı ayrı tanım (kim → hangi servis → hangi kapsam); listede olmayan çalışmaz. `events:ingest` adı `kaynak:eylem` kuralına **uygun**. | Kapsam adı `events:ingest` olarak kalır (`TokenClaims.EVENT_INGEST_SCOPE`). **Yapılacak:** §2'deki isteğe dayalı listeyi Kerem'e gönder |
| 2 | Community admin/notification'a giderken token'ı nasıl alır? | Auth'un token ucundan. Community için **ayrı istemci kimliği ve gizli anahtar** tanımlanır; gizli anahtar ortam değişkeninden gelir, koda/repoya yazılmaz. Bugün böyle bir istemci yalnız admin için var ve token yalnız Auth'a gidebiliyor; community→admin/notification için madde 1'deki gibi tanım eklenecek. | Relay kapalı kalır. Açmak için: (a) Auth'ta tanım, (b) community için **yeni ENV değişkenleri** (istemci kimliği + gizli anahtar) — `docs/SECURITY.md` §1 uyarınca **açık onay** ister, (c) `EventDeliveryClient`'a token edinme/ekleme. Hiçbiri henüz yapılmadı |
| 3 | Anonimleştirme yeterli mi, sabit kimlik, `[removed]`, rapor saklama süresi | Auth kendi tarafında hesabı anonimleştiriyor; sabit kimlik ve `[removed]` Auth sözleşmesiyle **çelişmiyor**. Rapor saklama süresi ve hukuki gerekçe **Auth'un kararı değil, Alper'in**; ona sorulacak. Netleşene kadar raporlar anonim saklanır, **süre ayarlanabilir tutulur**. | Mevcut davranış korunur (raporlar anonim, süresiz saklanıyor). Saklama süresi ayarı Alper'in kararından sonra eklenir (§3) |
| 4 | Dev'de birlikte silip `CONFIRMED` görebilir miyiz? | Evet, iki şartla: (i) silme artık **14 gün bekleme süresiyle** başlıyor; dev'de bu süre `PT0S` yapılacak (yalnız dev; canlıda 14 gün). Süre ayarını **ekip** dev ortam değişkenine ekler, Kerem eklemiyor. (ii) Yeni Auth dev'e çıkmış ve community container'ı **güncel imajla** çalışıyor olmalı. Test hesabı kullanılacak; dev'de silinen hesap anında anonimleşir ve geri gelmez. | Hazır olunca Kerem'e haber verilecek (§4 kontrol listesi) |
| 5 | `AccountStatusChanged` ne zaman, askıdaki hesabın içeriği gizlensin mi? | Olay planlı ama **tarih yok**. O zamana kadar durum değişince eski token'lar en fazla **90 sn** (60 sn token ömrü + 30 sn saat farkı) içinde ölür. İçeriği gizlemek **community'nin ürün kararı**, Auth zorunlu kılmıyor. Olay gelmeden gizlemek için güvenilir durum bilgisi yok, **olayı bekle**. | Community şu an bir şey yapmaz (uydurma olay tanımlanmaz). Gizleme kararı olay gelince verilecek |
| 6 | JWKS önbellek süresi / eski anahtarın yayın süresi | Anahtar değişirken eski açık anahtar JWKS'te bir süre daha yayınlanabilecek (yeni özellik, dalda); süre en az 90 sn, pratikte 5 dakikadan fazla. Community kendi önbelleğini **en çok 5 dk** tutmalı ve tanımadığı `kid`'de **bir kez yeniden çekmeli** (Game servisi gibi). | Mevcut davranış uyumlu: Spring Nimbus JWKS'i önbelleğe alır (varsayılan 5 dk) ve bilinmeyen `kid`'de yeniler (`JwtConfigTest.keyPublishedAfterStartupIsPickedUpOnNextUnknownKid`). Değişiklik gerekmez |

> Not (token ömrü): Kerem "60 sn token ömrü" diyor; `docs/AUTH_SERVICE_ENTEGRASYON_REHBERI.md` §7'de kullanıcı access token'ı için varsayılan 300 sn yazıyor. Muhtemelen 60 sn servis token'ı ömrünü, 90 sn ise onun üst sınırını anlatıyor; netleşmesi için Kerem'e sorulabilir. Community'nin kodu her iki durumda da çalışır (süre doğrulaması `exp`/saat sapmasıyla yapılır).

## 2. Kerem'e gönderilecek istek listesi (cevap: "hangi servis hangi kapsamla neden istiyor, tek tek yaz")

Aşağıdaki tablo Kerem'in istediği "kim → hangi servise → hangi kapsamla → neden" listesidir. **Kapsam adı hedef servise göre belirlenir**; hepsinde `events:ingest` önerilir (alıcı, olay girişini aynı adla açar). Her satır ayrı bir tanım olarak talep edilir; listede olmayan çağrı çalışmaz.

| # | Çağıran (`sub`) | Hedef (`aud`) | Kapsam | Neden | Community'de durum |
| --- | --- | --- | --- | --- | --- |
| 1 | `svc:admin-service` | `community-service` | `events:ingest` | Moderatör kararını (`ModerationActionTaken`: gizle/geri aç/kaldır/reddet) community içeriğine uygulatmak | Community ucu (`POST /internal/events`) hazır ve test edildi; `sub=svc:<producer>` kuralı var |
| 2 | `svc:social-service` | `community-service` | `events:ingest` | Engelleme yansımasını (`UserBlocked`, `UserUnblocked`) community'ye iletmek; iki yönlü görünürlük/etkileşim kuralı bu veriye dayanıyor | Aynı uç, hazır |
| 3 | `svc:community-service` | `admin-service` | `events:ingest` | Rapor olayını (`ContentReported`) admin moderasyon kuyruğuna iletmek | Olay outbox'ta `PENDING`; relay kapalı; token edinme yolu yok |
| 4 | `svc:community-service` | `notification-service` | `events:ingest` | Etkileşim bildirimlerini (`CommentCreated`, `PostLiked`, `PostReposted`) iletmek | Aynı |
| 5 (ileride) | `svc:auth-service` | `community-service` | `events:ingest` | `AccountStatusChanged` (D3) geldiğinde askı/yasak etkisi | Olay tanımı yok; bekleniyor |

Her olay yalnızca kimlik taşır (içerik metni, kullanıcı adı, e-posta yok). Olay türü ↔ çağıran eşleşmesi alıcıda ayrıca doğrulanır (örn. `ModerationActionTaken` yalnız `svc:admin-service` token'ıyla kabul edilir).

**Not:** Tablonun 3 ve 4. satırları için community'nin token'ı Auth'un token ucundan alması gerektiğinden, Kerem'in "community için ayrı istemci kimliği + gizli anahtar" tanımı da gerekir (§1, madde 2).

## 3. Rapor saklama süresi (Alper'e sorulacak)

Auth sahibi bunu Alper'in kararı olarak işaretledi. Alper'e iletilecek soru:

> Hesabı silinen kullanıcıların içeriği hakkındaki raporlar (gerekçe kodu, tarih, hedef içerik kimliği; kişi anonim) moderasyon/denetim izi olarak saklanıyor. **Bunlar ne kadar saklanmalı ve hukuki gerekçe nedir** (örn. 1 yıl, 2 yıl, süresiz)? Süre bitince otomatik silinsin mi?

Karar gelene kadar: raporlar anonim ve **süresiz** saklanır. Karar gelince eklenecek: ayarlanabilir saklama süresi (örn. `ltz.community.reports.anonymized-retention-days`) ve süresi dolanları silen zamanlanmış temizlik (varsayılan kapalı). Henüz kodlanmadı; yanlış bir süreyle veri silmemek için karar bekleniyor.

## 4. Kontrol listesi: dev'de hesap silme testi

- [ ] Yeni Auth `main`'e girdi ve dev'e çıktı (Kerem/Alper)
- [ ] Dev ortamında silme bekleme süresi `PT0S` yapıldı (ekip dev ortam değişkeni; yalnız dev)
- [ ] Community container'ı **bu dalın güncel imajıyla** çalışıyor (`COMMUNITY_SERVICE_URL`'e Auth ulaşabiliyor)
- [ ] Test hesabı oluşturulur, içerik üretir (gönderi, yorum, beğeni, rapor, engel), silinir
- [ ] `account_deletion_tasks` içinde `community` görevi `CONFIRMED`
- [ ] Community DB'de hesabın izi yok (gönderi/yorum anonim, beğeni/rapor/engel/outbox silinmiş), başkalarının verisi duruyor
- [ ] Kerem'e haber verilir

## 5. Yapılacaklar (sırayla)

1. **Nisa → Kerem:** §2 tablosunu gönder (kim/hangi servis/hangi kapsam/neden).
2. **Nisa → Alper:** §3 rapor saklama sorusu; ayrıca Gateway'in `/internal/**` yolunu route etmediğini teyit (`auth-integration-03.md` §6).
3. **Nisa → ekip/kullanıcı onayı:** Relay'i açmak için community'ye iki yeni ENV (istemci kimliği, gizli anahtar) eklenmesi — açık onay gerektirir; `.env.example` önerisi onaydan sonra yazılır.
4. **Birlikte:** §4 kontrol listesi.
5. **Nisa:** Karara göre rapor saklama süresi ayarı (§3), relay'e token ekleme, olay girişi kapsamının canlı doğrulaması.
