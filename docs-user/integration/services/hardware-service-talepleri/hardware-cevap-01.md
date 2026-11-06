# HW-USER-REQ-01 / REQ-02 — User cevabı

Tarih: 2026-11-06. Sorumlu: Kerem / User. Karşı talep: `docs-hardware/integration/user-service-talepleri/user-requests-01.md`.

## Cevap

| Talep | Karar |
| --- | --- |
| HW-08 profil paylaşımı | Ek iş yok. `field=HARDWARE` mevcut `POST /internal/v1/privacy/decisions` ile çalışır; Hardware tüketicisi sözleşmeyle uyumlu. |
| REQ-01 yansıma | `ltz.user.social.projection-enabled=false` kalır. Social başlangıç aktarımı bitip doğrulanınca User sahibi açar; Hardware açmaz. O zamana kadar sahibi olmayan izleyiciye `allowed=false` (fail-closed). |
| REQ-02 yorum görünürlüğü | **Ürün yorumu profildeki HARDWARE ayarına bağlı değildir.** Bu ayar kişinin kendi donanım listesini korur; yorum herkese açık katalog içeriğidir. Yorumu yalnız yazarın hesap durumu ve çift yönlü engel belirler. |

## REQ-02 sözleşmesi (uygulandı)

Yeni uç yok. Aynı toplu uç, yeni alan değeri:

```json
POST /internal/v1/privacy/decisions
{ "viewerId": "UUID|null", "field": "REVIEW_AUTHOR", "ownerIds": ["yorum yazarları, en çok 100"] }
```

- `setting` her zaman `PUBLIC`; profil ayarı ve misafir erişimi uygulanmaz.
- `allowed=false`: yazar hesabı yasaklı/devre dışı/silinmekte, ya da izleyici ile yazar arasında iki yönden biri engelli.
- Yansıma güvenilir değilken yazar dışında kimseye `allowed=true` dönmez (yorum yazarı kendi yorumunu görür).
- Misafir (`viewerId=null`) güvenilir modda engelsiz yazarın yorumunu görür.

## Hardware'in yapacağı

`HardwareReviewAudiencePolicy`: sayfa almadan önce yorum yazarlarını (distinct, ≤100) tek istekte sor; `allowed=false` yazarları
`blockedAuthors` yap ve bunları **sorgunun içinde** (WHERE author NOT IN) filtrele; böylece toplam, sayfalama ve puan özeti
yalnız izinli yorumlardan oluşur. >100 yazar varsa 100'lük gruplarla sor. Eksik sahip, 401/403/429/5xx ve timeout → paylaşım
yok (mevcut kapalı policy).

Not: şu an yansıma kapalı olduğundan sahibi olmayan izleyici hiçbir yorum yazarını göremez; bu REQ-01 açılana kadar beklenen davranıştır.
