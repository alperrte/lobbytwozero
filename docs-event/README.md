# docs-event

Event Service (Nisa) çalışma belgeleri. Kural belgelerinin (`docs/`) yerine geçmez; geliştirme sırasında alınan kararları, varsayımları ve servisler arası bağımlılıkları kayıt altına alır.

## İçindekiler

| Belge | Konu |
| --- | --- |
| [event-service/evt-01-kararlar-ve-varsayimlar.md](event-service/evt-01-kararlar-ve-varsayimlar.md) | EVT-01: gereksinimlerden gelenler, açık kararlar, geliştirici varsayımları, API/domain/DB tutarlılığı, doğrulanmamışlar |
| [event-service/evt-02-etkinlik-turleri.md](event-service/evt-02-etkinlik-turleri.md) | EVT-02: etkinlik türleri (POLL eklendi, V112), varsayımlar, doğrulanmayanlar |
| [event-service/evt-03-etkinlik-detayi.md](event-service/evt-03-etkinlik-detayi.md) | EVT-03 (KISMİ): gereksinim tablosu, eksik katılım bilgileri (EVT-13/EVT-08 bağımlılığı), KD-4 misafir erişimi, KD-3 null organizerId |
| [event-service/hesap-silme-entegrasyonu.md](event-service/hesap-silme-entegrasyonu.md) | Auth hesap silme akışının beklediği `DELETE /internal/v1/accounts/{accountId}` ucu: eksik, sözleşme, bağımlılık |

## Başka servislere talepler

Başka serviste değişiklik gerekirse talep `docs-event/<servis-adı>/talep/req-XX.md` altına, mevcut numaralandırma sırasıyla yazılır.

| Talep | Durum |
| --- | --- |
| [auth-service/talep/req-01.md](auth-service/talep/req-01.md) | AÇIK — pre-push Code Quality: `GoogleLoginService`, `SteamLoginService` `PreserveStackTrace` (2 ihlal) |
| [hardware-service/talep/req-01.md](hardware-service/talep/req-01.md) | AÇIK — pre-push Code Quality: 8 ihlal (Icecat istemcisi, kaynak görünümü, medya depolama) |

## Açık bağımlılıklar

| # | Bağımlılık | Engellediği | Kaynak |
| --- | --- | --- | --- |
| B-1 | EVT-13 (kapasite, katılımcı sayacı) | EVT-03 "kapasite / katılımcı sayısı / kalan kontenjan" | [evt-03](event-service/evt-03-etkinlik-detayi.md) §3.1 T-1, T-2, T-4 |
| B-2 | EVT-08 (katılım kaydı) | EVT-03 "kullanıcının katılım durumu" | [evt-03](event-service/evt-03-etkinlik-detayi.md) §3.1 T-3, T-5 |
| B-3 | Auth hesap silme ucu + nullable `organizer_id` (KD-3) | `organizerId`'nin `null` olabilmesi; Auth silme akışının tamamlanması | [hesap-silme-entegrasyonu.md](event-service/hesap-silme-entegrasyonu.md) |

EVT-08 ve EVT-13 tamamlanınca **detay yanıtına gerçek katılım bilgileri eklenmeli** ve EVT-03 ancak o zaman kapatılmalıdır.
