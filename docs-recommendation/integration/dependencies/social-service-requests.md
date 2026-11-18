# TASK 4 — Social öneri eligibility sözleşmesi talebi

2026-11-15 · REC-13/14 · Sahip: **Umut**, Auth izinleri **Kerem** · **TASARIM DEĞİŞTİ (2026-11-18): batch eligibility ucu artık gerekmiyor; Social'dan iki küçük teyit/karar bekleniyor**.
Aşağıdaki "İstenen en dar kabiliyet" bölümü özgün talebin kaydıdır; güncel durum en alttaki "Güncel durum" bölümündedir.
Bu belge mevcut API'yi genişletmez; öneri tüketicisi için eksik kabiliyeti tarif eder.

## Mevcut kanıt

- `POST /internal/v1/blocks/check`: 1–100 diğer kimlik; iki yönlü engeli tek boolean ile döner.
- `BlocksCheckController` ve `TokenClaims.BLOCKS_CHECK_SUBJECTS` yalnız
  `svc:messaging-service` / `svc:lfp-service` kabul eder. Aynı scope ile başka subject 403.
- Auth mevcut Recommendation hedefleri Library `signals:read` ve Performance `reference:read`;
  Social grant'ı yoktur. Token issuer'ın teknik olarak claim imzalayabilmesi, token grant
  endpoint'inde Recommendation'a bu hedefi kullanma yetkisi verildiği anlamına gelmez.
- `GET /api/v1/social/friends` owner arkadaş listesidir: limit 1–50, cursor, friendId/since.
  Yalnız bu liste ve owner engellenenler listesi B→A engelini kanıtlamaz. Listeyi okumak
  izinli aday keşfi veya bütün adaylar için güncel öneri eligibility kontrolü değildir.

## İstenen en dar kabiliyet

Umut, Recommendation için izinli ve sınırlı batch eligibility sözleşmesini belirlemeli;
Kerem gerekiyorsa dar Auth audience/scope/account binding yetkisini ayrıca onaylamalı.
Burada endpoint/scope adının uygulanmış olduğu iddia edilmez.

- Viewer doğrulanmış kimliğe bağlı; başka hesabın ilişkilerini sorgulama yetkisi verilmemeli.
- Self, mevcut arkadaşlık, A→B veya B→A engel ve erişime kapalı aday dışlanmalı.
- Yanıt tercihen yalnız önerilebilir aday kimliklerini taşımalı; engelin yönü, sahibi, zamanı
  veya özel arkadaş listesi döndürülmemeli. Eksik/atlanmış sonuç izin gibi yorumlanmamalı.
- Gerekliyse pending arkadaşlık isteğinin eligibility etkisi owner tarafından netleştirilmeli;
  Recommendation iş kuralını tahmin etmemeli.
- Boyut sınırı, tekilleştirme, tamlık, kaynak güncelliği ve değişen arkadaşlık/engel/rıza
  durumunda yeniden değerlendirme semantiği tanımlanmalı. Başlangıç talebi tek batch ≤100.
- 401/403/timeout/503 veya belirsiz yanıtta aday döndürülmemeli. N+1 pair sorgusu, cross-DB
  erişimi, başka servisin token'ını kullanma ve caller taklidi yapılmamalı.

## Beklenen owner kabul kanıtı

Self, friend, iki engel yönü, unblock, başkasının ilişkisini sorgulama, servis subject/scope/
account binding, batch sınırı/tamlığı/duplicate ve timeout testleri; gerçek Auth grant + Social
HTTP kabulü ve `docs/api.md` kaydı. Sonrasında Recommendation sıralama ve son aşama privacy
testleri yazılabilir. Mevcut TASK 4 yalnız kapalı erişim sınırını doğrular; bu producer kabulü değildir.

## Güncel durum (2026-11-18)

**Tasarım değişikliği:** Recommendation, Social'a engel/arkadaşlık batch eligibility çağrısı yapmaz. Aday keşfi
User servisinde yapılır (`GET /internal/v1/accounts/{accountId}/recommendation-candidates`, bkz.
`user-service-requests.md` ve `docs/api.md` USER bölümü). User, Social'ın zaten ilettiği engel/arkadaşlık
yansımasını (`social_blocks`, `social_friendships`; V72) kendi DB'sinde kullanır. Bu yüzden Social için yeni uç,
yeni Auth hedefi veya yeni scope **istenmez**; yukarıdaki "İstenen en dar kabiliyet" maddelerinden batch uç gereksinimi düşer.

Mevcut kanıt: Social `UserEventPublisher` arkadaşlık/engel olaylarını User `POST /internal/events` ucuna zaten iletiyor
(`events:ingest`). Recommendation'ın bu yansımaya güvenmesi için kalanlar:

| # | Talep | Sahip | Durum |
| --- | --- | --- | --- |
| S1 | ✔ KOD HAZIR (2026-11-18, Alper; Umut yetkisiyle) — **Geçmiş ilişkilerin aktarımı:** olay iletimi başlamadan önce oluşmuş engel ve arkadaşlıklar User yansımasına geçti mi? Geçmediyse tek seferlik aktarım (backfill) gerekir. User belgesi: aktarılmadan `projection-enabled=true` yapılmamalı, yoksa eski engeller bilinmeden aday döner. | Umut (Social) | `UserProjectionBackfill` + `ltz.social.projection-backfill.enabled` (varsayılan kapalı); Social 5 test PASS. Gerçek ortamda çalıştırılmadı: bayrağı açıp User'a göndermek ve `finished` logunu doğrulamak kaldı |
| S2 | **Güvenilir ilan kararı:** S1 sağlanınca ortamda `ltz.user.social.projection-enabled=true` yapılması (ENV değişikliği; kullanıcı onayı ve User sahibi Kerem ile birlikte). Kapalıyken aday ucu bilerek boş döner. | Umut + Kerem | AÇIK |
| S3 | ✔ CEVAPLANDI, RİSK AÇIK — **Olay teslim garantisi:** engel/unblock olayı User'a ulaşamazsa yeniden denenip denenmediği ve gecikme üst sınırı (şu an belgede "birkaç saniye"). Engelli kişinin aday listesinde kalma süresini bu belirler. | Umut | Outbox en az bir kez; 30 sn→1 saat üstel bekleyişle 10 deneme, sonra `FAILED` (otomatik kurtarma yok, el ile müdahale). Relay varsayılan KAPALI. Açık: `FAILED` izleme/yeniden kuyruklama kararı Umut'ta |
| S4 | **REC-14 (arkadaş önerisi):** mevcut arkadaşların önerilmemesi için User'ın aday sorgusuna `social_friendships` dışlaması eklenmesi gerekir (User tarafı, Kerem yetkisiyle yapılabilir; Social'dan yeni şey istenmez). Bekleyen (pending) arkadaşlık isteği olan kişinin önerilip önerilmeyeceği iş kuralı Social sahibi tarafından netleştirilmeli; Recommendation tahmin etmez. | Umut (kural), Alper (User sorgusu) | ✔ KOD HAZIR (2026-11-18): User aday ucuna `excludeFriends` (varsayılan false) eklendi, 12+1 test PASS. **Açık kural:** bekleyen istek dışlanmıyor (User'da veri yok); Umut teyit edene kadar varsayım budur |

Hâlâ geçerli ve değişmeyen: `blocks/check` ucu yalnız messaging/lfp içindir, Recommendation'a açılmaz; gizli engel yönü,
sahibi veya arkadaş listesi Recommendation'a taşınmaz. Başarısızlık/belirsizlikte aday dönmez (User tarafında uygulandı).

Bu dosya `done/` klasörüne **taşınmadı**: S1–S4 kapanmadan REC-14 ve canlı REC-13 açık kalır.

### Umut için mesaj taslağı

> Recommendation aday keşfini User servisinde yapıyor; Social için yeni uç istemiyoruz. User, Social olaylarıyla tuttuğu
> engel/arkadaşlık yansımasını kullanıyor. İki teyit lazım: (1) olay iletimi başlamadan önceki engel ve arkadaşlıklar
> User yansımasına aktarıldı mı, aktarılmadıysa tek seferlik backfill yapılabilir mi? (2) Engel/unblock olayı User
> ulaşılamazken yeniden deneniyor mu, en kötü gecikme nedir? Bunlar tamamsa User tarafında projection-enabled bayrağını
> açmayı Kerem'le birlikte konuşabiliriz. Ayrıca bekleyen arkadaşlık isteği olan kişi arkadaş önerisinde çıkmalı mı?
