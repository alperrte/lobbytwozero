# docs-event

Event Service (Nisa) çalışma belgeleri. Kural belgelerinin (`docs/`) yerine geçmez; geliştirme sırasında alınan kararları, varsayımları ve servisler arası bağımlılıkları kayıt altına alır.

## İçindekiler

| Belge | Konu |
| --- | --- |
| [event-service/evt-01-kararlar-ve-varsayimlar.md](event-service/evt-01-kararlar-ve-varsayimlar.md) | EVT-01: gereksinimlerden gelenler, açık kararlar, geliştirici varsayımları, API/domain/DB tutarlılığı, doğrulanmamışlar |
| [event-service/evt-02-etkinlik-turleri.md](event-service/evt-02-etkinlik-turleri.md) | EVT-02: etkinlik türleri (POLL eklendi, V112), varsayımlar, doğrulanmayanlar |
| [event-service/hesap-silme-entegrasyonu.md](event-service/hesap-silme-entegrasyonu.md) | Auth hesap silme akışının beklediği `DELETE /internal/v1/accounts/{accountId}` ucu: eksik, sözleşme, bağımlılık |

## Başka servislere talepler

Başka serviste değişiklik gerekirse talep `docs-event/<servis-adı>/talep/req-XX.md` altına, mevcut numaralandırma sırasıyla yazılır.

| Talep | Durum |
| --- | --- |
| [auth-service/talep/req-01.md](auth-service/talep/req-01.md) | AÇIK — pre-push Code Quality: `GoogleLoginService`, `SteamLoginService` `PreserveStackTrace` (2 ihlal) |
| [hardware-service/talep/req-01.md](hardware-service/talep/req-01.md) | AÇIK — pre-push Code Quality: 8 ihlal (Icecat istemcisi, kaynak görünümü, medya depolama) |
