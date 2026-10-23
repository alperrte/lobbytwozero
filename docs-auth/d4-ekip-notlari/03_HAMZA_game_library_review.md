# Hamza — Game, Library, Review: Hesap Silme İç Ucu

> Tarih: 2026-10-24 · Kimden: Kerem (Auth) · Önce [01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md](01_ORTAK_SILME_UCU_UYGULAMA_REHBERI.md)'yi
> oku; bu dosya yalnız sana özel kısım.

## Özet

| Servis | Durum | Yapılacak |
| --- | --- | --- |
| **game** | **Yeni silme hedefi** (S2 kararı, 2026-10-23). Uç yok | İkinci decoder (`svc+jwt`) + uç + REF'ler için silme/anonimleştirme kararı |
| **library** | Uç yok | Ucu aç; kütüphane, istek listesi vb. silinir |
| **review** | Uç yok | Ucu aç; inceleme içeriği silinir veya anonimleşir (karar) |

## 1. Game belgen için güncellemeler

- Game belgesindeki §11'de devrettiğin **46 Auth + 3 Gateway Sonar bulgusunun hepsi kapandı** (`ltz-auth-gateway` projesi:
  0 bulgu, 0 hotspot, tüm notlar A, Quality Gate PASSED). §11'i "kapandı" diye işaretleyebilirsin.
- "D4 servis kimliği açık sınır" maddesi: Auth→servis yönü **uygulandı**. Açık kalan: Admin→Auth iç uç (Auth'ta henüz yok).
- "Hesap silme açık sınır" maddesi: Auth artık Game'i gerçekten çağırıyor; Game ucu açana kadar hiçbir hesap `DELETED` olamaz.

## 2. Game — hesaba bağlı sütunlar (main'deki V31–V40'tan)

| Tablo | Sütun | Önerim |
| --- | --- | --- |
| `games` | `editor_actor_id` (null olabilir) | `NULL` yap |
| `game_identity_audits` | `actor_id` **NOT NULL** | Kaydı tut, aktörü anonimleştir (§3) |
| `game_field_overrides` | `actor_id` **NOT NULL** | Değişiklik kalsın, aktörü anonimleştir |
| `game_admin_audits` | `actor_id` **NOT NULL** | Kaydı tut, aktörü anonimleştir |
| `game_developer_grants` | `developer_user_id` NOT NULL | Hesabın grant satırları **silinir** |
| `game_developer_grants` | `granted_by`, `revoked_by` | Başkasının grant'ında aktör anonimleşir |
| `game_developer_contents`, `game_developer_media`, `game_update_notes` | `author_id` NOT NULL | İçerik oyunla ilgili ve halka açıksa yazar anonimleşir; kişisel içerikse silinir (karar) |
| `game_developer_feedback` | `author_id` NOT NULL | Feedback silinir (kişisel içerik) |

Tabloyu kendi koduna göre doğrula; migration'lardan grep'le çıkardım, eksik olabilir.

## 3. Anonimleştirme ve migration kısıtı

Birkaç sütun `NOT NULL` ve Game'in aralığı V31–V40 **dolu** (`docs/decisions/migrations.md`: yeni migration için ekipçe yeni
aralık gerekiyor). Bu yüzden önerim **migration gerektirmeyen** yol:

- Sabit bir "silinmiş hesap" aktörü: `00000000-0000-0000-0000-000000000000` (nil UUID). Gerçek bir hesap bu değeri alamaz (Auth
  UUID'leri rastgele). `actor_id` buna çevrilir; kayıt, zaman ve eylem kalır, kişi kalmaz.
- `NULL`'a izin veren sütunlarda (`editor_actor_id`, `revoked_by`) `NULL`.

Hangi kaydın hukuki gerekçeyle saklanacağı (audit) **birlikte** karar verilecek; seçimi Game belgesine yaz. `204` "bu hesaba ait
kişisel veri kalmadı" demek: anonim audit satırı buna uyar, `actor_id` dolu kalan satır uymaz.

## 4. Game — güvenlik zinciri

Game'in kullanıcı token'ı doğrulaması (at+jwt, RS256, kid, iss/aud, ±60 sn) aynen kalır. Rehber §4.2–§4.4'teki gibi ikinci decoder
ve yola göre `authenticationManagerResolver` ekle (`ltz.game.auth.service-audience=game-service`). `SecurityBoundaryTest`'e
`/internal/**` için: kullanıcı token'ı (ADMIN dahil) `401`, servis token'ı `/api/v1/game/**`'da `401`.

`RealAuthIntegrationTest` zaten gerçek Auth üreticisiyle çalışıyor; aynı yaklaşımla Auth'un `ServiceTokenIssuer`'ını kullanan bir
uyumluluk testi ekleyebilirsin (rehber §5 madde 17). Auth kaynağını değiştirme, `ProductionAuthSupport`'taki gibi kopyada derle.

## 5. Library ve Review

İskelet olsalar da uç açılır ve `204` döner; tablo eklendiğinde silme mantığı da eklenir. `ltz.library.auth.service-audience=
library-service`, `ltz.review.auth.service-audience=review-service`. Review'da inceleme metni kişisel içeriktir: silinir ya da metin
kaldırılıp yazar anonimleşir (oyunun puan ortalaması korunmak isteniyorsa); kararı belgeye yaz.
