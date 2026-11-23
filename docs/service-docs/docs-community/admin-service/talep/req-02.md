# Admin Talebi 02 — Moderatörün raporlanan içeriği görmesi (içerik okuma sözleşmesi)

> Tarih: 2026-11-07 · Gönderen: Nisa (community-service) → `admin-service`
> Sorumlu geliştirici: **atanmamış** (ortak servis; bkz. `req-01.md`). Bu talep aynı zamanda **Auth** (yeni kapsam) kararı gerektirir; Admin sahibi öneriyi kabul ederse Auth'a ayrıca talep açılacaktır (şimdi açılmadı).
> Durum: **TALEP / ÖNERİ — karar bekliyor.** Community'de ve Admin'de hiçbir şey uygulanmadı.
> Community gereksinimleri: **COM-16**, **COM-17**.
> Bağımlılık: `req-01.md` (olay alıcısı/göndericisi). Bu talep ondan bağımsız karara bağlanabilir, ama ikisi birlikte olmadan moderasyon akışı kullanılamaz.

## 1. İhtiyaç ve kullanım senaryosu

Moderatör, Admin'deki vakada raporlanan gönderi ya da yorumun **kendisini** görmelidir (gereksinim belgesi: "rapor ve bağlı içeriği inceler"). Karar gerekçesi içeriğe bakılmadan verilemez. Özellikle:

- içerik raporlandıktan sonra düzenlenmiş olabilir (vaka anındaki değil, güncel metin gerekir);
- içerik daha önce gizlenmiş (`HIDDEN`) olabilir ve karar `RESTORE` olabilir;
- rapor birden fazla kişiden gelebilir ve moderatör aynı içeriği tek yerden görmelidir.

## 2. Karşı servisin mevcut desteği ve eksik nokta

- **Admin:** yalnız uygulama sınıfı vardır; Community'ye herhangi bir istemci, token alma ya da okuma çağrısı yoktur.
- **Community:** raporlanan içeriği Admin'e verecek bir uç **yoktur**. Mevcut herkese açık okuma uçları (`GET /api/v1/community/posts/{id}`, yorum listeleri) gizli, silinmiş, engellenmiş ve gizli forum içeriği için `404` döner ve moderatöre bir istisna tanımaz (içerik sızdırmama kuralı). `ContentReported` yükü içeriğin metnini bilerek taşımaz (kişisel veri azaltma).
- **Auth:** yalnız `events:ingest` kapsamlı `admin-service → community-service` çifti vardır. İçerik okuma için kapsam ve çift **tanımlı değil**.

**Eksik:** Admin'in raporlanan içeriği güvenli biçimde okuyacağı bir sözleşme yok. Bu, `req-01.md` içindeki olay sözleşmesiyle çözülemez.

## 3. Seçenekler ve öneri

| Seçenek | Açıklama | Artı | Eksi |
| --- | --- | --- | --- |
| **A (önerilen)** | Community iç okuma ucu: `GET /internal/v1/moderation/posts/{postId}` ve `.../comments/{commentId}`; Admin gerektiğinde çağırır | İçerik yalnız ihtiyaç anında ve güncel okunur; Admin veritabanında içerik kopyası birikmez; erişim Community'de denetlenir | Yeni Auth kapsamı + çift, Community'de yeni uç, Admin'de yeni istemci |
| B | `ContentReported` yüküne kırpılmış metin özeti (ör. ilk 500 karakter) eklenir | Yeni uç/kapsam gerekmez | İçerik kopyası outbox'ta ve Admin'de birikir (saklama/silme yükü), düzenlemeler yansımaz, görsel yok, hesap silmede Admin kopyası temizlenmeli |
| C | Moderatör rolüne Community herkese açık uçlarında gizli içerik istisnası | Yeni sözleşme yok | Rolü olan herkesin kullanıcı ucundan gizli içerik görmesi; yetki sınırı bulanık; kullanıcı token'ı Admin'den geçmez |

**Öneri A.** Aşağıdaki sözleşme yalnız A içindir ve **öneridir**.

### 3.1 Önerilen uç (Community sağlar, Admin tüketir)

`GET /internal/v1/moderation/posts/{postId}`, `GET /internal/v1/moderation/comments/{commentId}`

- **Kimlik:** Auth servis token'ı, `aud=community-service`, **yeni** kapsam `moderation:read` (Auth'ta henüz yok), `sub=svc:admin-service`, ≤ 60 sn. Kullanıcı token'ı ve `events:ingest` token'ı kabul edilmez.
- **Denetim izi:** Hangi moderatörün hangi içeriği açtığı **Admin'in** denetim kaydında tutulur. Community'nin güvendiği tek kimlik servis token'ıdır; moderatör kimliği gövdeden ya da başlıktan okunmaz, bu uçta kullanılmaz.
- **Yanıt (öneri):** `id`, `type` (`POST|COMMENT`), `status` (`VISIBLE|HIDDEN|DELETED`), `authorId`, `body` (düz metin; `DELETED` ise **gönderilmez**), `createdAt`, `updatedAt`, `postId` (yorumda), `gameId`, görsel meta verisi (kimlik, tür, boyut; ikili veri değil), `openReportCount`.
- **Hatalar:** `401` kimlik yok/geçersiz, `403` kapsam ya da `sub` uygun değil, `404` içerik yok, `400` geçersiz UUID. Engelleme ilişkisi bu uçta uygulanmaz (moderatör çağıran değildir; hedef içerik doğrudan okunur).
- **Gizlilik:** yanıt yalnız moderasyon için gerekli alanları taşır; e-posta, ad, token ve ikili görsel içermez. Admin yanıtı kalıcı kaydetmemelidir (vaka ekranında gösterim için bellekte); kaydetmesi gerekiyorsa saklama süresi ve hesap silme davranışı Admin sahibince belirlenir.

## 4. Community tarafında tamamlanan hazırlık ve bekleyen iş

- Tamamlanan: yok (bilinçli). Karşı taraf kullanmayacak, Auth'ta kapsam yok ve sözleşme karara bağlı olmadan Community'de ayrıcalıklı bir okuma ucu açmak gereksiz saldırı yüzeyidir.
- Bekleyen: Admin sahibi seçeneği belirler → A seçilirse (1) Auth'a `moderation:read` kapsamı ve `admin-service → community-service` için çift talebi açılır, (2) Community uçları, güvenlik yapılandırması, testler ve `docs/api.md` kaydı yazılır, (3) Admin istemcisi yazılır.

## 5. Kabul kriterleri (A seçilirse)

1. Moderatörün açtığı vakada raporlanan içeriğin güncel metni gösterilir; rapordan sonra düzenlenen metin de güncel görünür.
2. `HIDDEN` içerik moderatöre görünür, aynı içerik herkese açık Community ucunda `404` kalır.
3. `DELETED` (kaldırılmış ya da hesap silinmiş) içeriğin metni Admin'e verilmez; yalnız durum döner.
4. `events:ingest` kapsamlı token, kullanıcı token'ı, başka bir servis (`sub`) ve süresi dolmuş token `401/403` alır; yanıtta kişisel veri yoktur.
5. Community kesintisinde vaka ekranı "içerik şu an okunamıyor" der; karar akışı içeriği görmeden yanlışlıkla otomatik uygulanmaz.
