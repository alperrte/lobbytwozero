# docs-event

Event Service (Nisa) çalışma belgeleri. Kural belgelerinin (`docs/`) yerine geçmez; geliştirme sırasında alınan kararları, varsayımları ve servisler arası bağımlılıkları kayıt altına alır.

## İçindekiler

| Belge | Konu |
| --- | --- |
| [event-service/evt-01-kararlar-ve-varsayimlar.md](event-service/evt-01-kararlar-ve-varsayimlar.md) | EVT-01: gereksinimlerden gelenler, açık kararlar, geliştirici varsayımları, API/domain/DB tutarlılığı, doğrulanmamışlar |
| [event-service/evt-02-etkinlik-turleri.md](event-service/evt-02-etkinlik-turleri.md) | EVT-02: etkinlik türleri (POLL eklendi, V112), varsayımlar, doğrulanmayanlar |
| [event-service/evt-03-etkinlik-detayi.md](event-service/evt-03-etkinlik-detayi.md) | EVT-03 (KISMİ): gereksinim tablosu, eksik katılım bilgileri (EVT-13/EVT-08 bağımlılığı), KD-4 misafir erişimi, KD-3 null organizerId |
| [event-service/evt-04-etkinlik-listeleme.md](event-service/evt-04-etkinlik-listeleme.md) | EVT-04: yaklaşan/mevcut etkinlik listesi, zaman tabanlı tanım (varsayım), V113 indeksi, test altyapısı (geçici PostgreSQL) |
| [event-service/evt-05-etkinlik-arama.md](event-service/evt-05-etkinlik-arama.md) | EVT-05 (KISMEN): ada göre arama + oyun adı araması (KD-5/B), Game sözleşme sınırları (önek, yalnız ana oyun, 1000 eşleşme) |
| [event-service/evt-06-etkinlik-filtreleme.md](event-service/evt-06-etkinlik-filtreleme.md) | EVT-06: oyun/tür/tarih/platform filtreleri; platform etkinliğin kendi alanı (KD-6/A); platform sözlüğü KD-6a **onaylandı** (karar kaydı doğrulamadan ayrı) |
| [event-service/evt-17-oyun-baglantisi.md](event-service/evt-17-oyun-baglantisi.md) | EVT-17: `gameId` ile oyun bağlantısı (oluşturma anında), Game doğrulaması |
| [event-service/hesap-silme-entegrasyonu.md](event-service/hesap-silme-entegrasyonu.md) | Auth hesap silme akışının beklediği `DELETE /internal/v1/accounts/{accountId}` ucu: eksik, sözleşme, bağımlılık |

## Başka servislere talepler

Başka serviste değişiklik gerekirse talep `docs-event/<servis-adı>/talep/req-XX.md` altına, mevcut numaralandırma sırasıyla yazılır.

| Talep | Durum |
| --- | --- |
| [auth-service/talep/req-01.md](auth-service/talep/req-01.md) | AÇIK — pre-push Code Quality: `GoogleLoginService`, `SteamLoginService` `PreserveStackTrace` (2 ihlal) |
| [game-service/talep/req-01.md](game-service/talep/req-01.md) | AÇIK — oyun adı arama sözleşmesinin sınırları (önek, yalnız ana oyun, eksiksiz sonuç için `gameId` süzme ucu önerisi); Auth değişikliği gerekmedi |
| [hardware-service/talep/req-01.md](hardware-service/talep/req-01.md) | AÇIK — pre-push Code Quality: 8 ihlal (Icecat istemcisi, kaynak görünümü, medya depolama) |

## Açık bağımlılıklar

| # | Bağımlılık | Engellediği | Kaynak |
| --- | --- | --- | --- |
| B-1 | EVT-13 (kapasite, katılımcı sayacı) | EVT-03 "kapasite / katılımcı sayısı / kalan kontenjan" | [evt-03](event-service/evt-03-etkinlik-detayi.md) §3.1 T-1, T-2, T-4 |
| B-2 | EVT-08 (katılım kaydı) | EVT-03 "kullanıcının katılım durumu" | [evt-03](event-service/evt-03-etkinlik-detayi.md) §3.1 T-3, T-5 |
| B-3 | Auth hesap silme ucu + nullable `organizer_id` (KD-3) | `organizerId`'nin `null` olabilmesi; Auth silme akışının tamamlanması | [hesap-silme-entegrasyonu.md](event-service/hesap-silme-entegrasyonu.md) |
| B-4 | Game Service arama sözleşmesi sınırları (önek, yalnız ana oyun, 1000+ eşleşmede eksiksiz sonuç yok) — [req-01](game-service/talep/req-01.md); ayrıca EVT-14 (oluşturulmuş etkinliğin oyun bağlantısını değiştirme) | EVT-05 "ilişkili oyuna göre arama" tam karşılama; EVT-17 sonradan değiştirme | [evt-05](event-service/evt-05-etkinlik-arama.md) §4, [evt-17](event-service/evt-17-oyun-baglantisi.md) |

EVT-08 ve EVT-13 tamamlanınca **detay yanıtına gerçek katılım bilgileri eklenmeli** ve EVT-03 ancak o zaman kapatılmalıdır. Game talebi (req-01) ve ilgili ürün kararları (önek/"içerir", DLC) sonuçlanınca **oyun adı araması yeniden değerlendirilmeli** ve EVT-05 ancak o zaman kapatılmalıdır.

EVT-06: dört filtre kodlandı; açık karar kalmadı (KD-6a onaylandı). Kapanış koşulu işlevsel doğrulamadır (testler, token'lı kontrol listesi: [evt-06](event-service/evt-06-etkinlik-filtreleme.md) §8).

**Kapanan kararlar:** KD-1 rol (2026-11-09), KD-2 oylama türü (2026-11-09), KD-4 görüntüleme erişimi (2026-11-09), KD-5 oyun adı araması (2026-11-09), KD-6/A platform alanı ve **KD-6a platform sözlüğü (2026-11-11)**. Açık kalanlar: KD-3 (silinen düzenleyici — karar var, uygulama yok), KD-3a (tamamlanan etkinliklerde metin saklama).
