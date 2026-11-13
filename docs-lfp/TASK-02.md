# LTZ-UMT-LFP-02 — İlanları Ara, Güncelle ve Süre Sonunda Kapat (TASK-02)

> Tarih: 2026-11-13 · Sorumlu: Umut · Servis: `lfp-service` · Dal: `feature/lfp-service` · Kapsam: LFP-10, LFP-11, LFP-12, LFP-13, LFP-14, LFP-15 · Durum: **kod, testler ve belgeler tamam; merge öncesi doğrulama PASS** (testler kullanıcı kararıyla servisin tüm görevleri bitince toplu koşuldu; sonuçlar: [TASK-03 §8](TASK-03.md#8-merge-öncesi-doğrulama-2026-11-13))
>
> Bu belge lfp-service çalışmasına özgüdür; ortak kurallar `AGENTS.md`, `docs/SECURITY.md` ve `docs/api.md` içindedir. Endpoint sözleşmeleri `docs/api.md` → `## LFP` altındadır. Önceki görev: [TASK-01](TASK-01.md).

## 1. Sonuç

| Görev | Durum | Not |
| --- | --- | --- |
| LFP-10 Aktif ilanları listeleme | IMPLEMENTED | `GET /api/v1/lfp/listings`; en yeni önce, keyset sayfalama |
| LFP-11 Arama ve süzgeç | IMPLEMENTED | Oyun, platform, seviye, rol, zaman aralığı, mikrofon, metin (`q`); hepsi birlikte |
| LFP-12 İlan güncelleme | IMPLEMENTED | `PUT /api/v1/lfp/listings/{id}`; yalnız sahip, yalnız açık ilan, sürüm denetimi |
| LFP-13 İlan kapatma | IMPLEMENTED | `POST /api/v1/lfp/listings/{id}/close`; idempotent |
| LFP-14 İlan durumları | IMPLEMENTED | `OPEN → CLOSED`, `OPEN → EXPIRED`; geri dönüş yok; yanıtta okuma anındaki durum |
| LFP-15 Süreli ilanlar | IMPLEMENTED | Zamanlayıcı dakikada bir `EXPIRED` yazar; okumalar zamanlayıcıyı beklemeden süresi geçeni gizler |

Ek uçlar: `GET /api/v1/lfp/listings/mine` (sahibin tüm ilanları, durum süzgeci) ve `GET /api/v1/lfp/listings/{id}` (detay).

Doğrulama durumu:

| Kontrol | Sonuç |
| --- | --- |
| Derleme (`test-compile`, JDK 25) | PASS — Checkstyle derleme sırasında temiz |
| Birim + entegrasyon testleri (toplam 100 test; bu görevde 31 yeni) | PASS — merge öncesi toplu koşu (TASK-03 §8) |
| JaCoCo, PMD, `lint-ratchet`, `pre-push full dev`, SonarQube | pre-push ve full dev PASS; SonarQube bkz. TASK-03 §8 |

## 2. Uçlar ve akış

| Uç | Ne yapar |
| --- | --- |
| `GET /api/v1/lfp/listings` | Aktif ilanlar + süzgeçler |
| `GET /api/v1/lfp/listings/mine` | Sahibin ilanları (tüm durumlar) |
| `GET /api/v1/lfp/listings/{id}` | Detay (her durum) |
| `PUT /api/v1/lfp/listings/{id}` | Sahip günceller (tam değiştirme) |
| `POST /api/v1/lfp/listings/{id}/close` | Sahip kapatır |

1. **Okuma anındaki durum:** `Listing.statusAt(now)` — `OPEN` ve `expiresAt ≤ now` ise `EXPIRED`. Liste süzgeci (`ListingSpecifications.openAt`), "kendi ilanlarım" durum süzgeci, detay yanıtı, güncelleme/kapatma kuralları ve açık ilan sınırı aynı kuralı kullanır. Zamanlayıcı gecikse ya da kapalı olsa da süresi dolan ilan hiçbir yerde açık görünmez.
2. **Arama:** Criteria API (`JpaSpecificationExecutor`) ile süzgeçler VE ile birleşir; değerler her zaman parametre olarak bağlanır. LIKE'ta `%`, `_`, `\` kaçışlanır. Rol süzgeci `exists` alt sorgusuyla (`lower(role)` indeksi), sayfa `limit + 1` satırla tek sorgu, roller `@BatchSize(50)` ile toplu yüklenir.
3. **Güncelleme:** içerik kuralları transaction dışında → transaction: varlık (404) → sahiplik (403) → açık mı (409) → istemcinin `version`'ı (409) → `Listing.update` (oyun zamanı) → `saveAndFlush` (`@Version` eşzamanlı değişikliği yakalar).
4. **Kapatma:** varlık → sahiplik → `Listing.close` (kapalıysa değişiklik yok; süresi dolmuşsa 409).
5. **Süre dolumu:** `ListingExpiryWorker` (`@Scheduled`, `ltz.lfp.expiry.interval=PT1M`) → `ListingExpiryService.expireDue()` → her parti ayrı transaction'da `update … where id in (select … for update skip locked limit 500)`; tek çalıştırmada en çok 20 parti. Hata bir sonraki çalıştırmayı engellemez, logda yalnız olay kodu + hata türü.

## 3. Kararlar ve varsayımlar (itiraz edilebilir)

| Konu | Karar | Neden |
| --- | --- | --- |
| Seviye süzgeci | Metin içerir + seviyesi **belirtilmemiş** ilanlar da eşleşir | LFP-11 "seviye (belirtilmişse)"; seviye beklentisi olmayan ilan her seviyeye açık. Seviye serbest metin olduğundan tam eşleşme yerine içerir |
| Zaman süzgeci | `playFrom`/`playTo` planlanan başlangıca uygulanır; zamanı belirtilmemiş ilan eşleşmez | Zaman arayan kullanıcı planlanmış oyun ister |
| Rol süzgeci | Tam eşleşme, büyük/küçük harf duyarsız | Roller kısa etiketlerdir; içerir eşleşmesi "Tank" ile "Main Tank"ı karıştırırdı |
| Metin araması (`q`) | Oyun adı + açıklama, içerir | "İlan arama"; tam metin indeksi bu ölçekte gereksiz |
| Güncelleme biçimi | `PUT` tam değiştirme + zorunlu `version` | Kısmi güncellemede "alanı sil" ile "gönderme" ayırt edilemez (ör. seviyeyi kaldırmak); kayıp güncellemeyi `version` önler |
| Değişmeyen alanlar | Oyun ve süre güncellenemez | Oyunu değiştirmek ilanın anlamını değiştirir; süre uzatma ilanı sonsuza kadar açık tutardı |
| Geçmiş başlangıç | Değişmediyse korunabilir | Planlanan zamanı gelmiş açık ilanın açıklaması düzeltilebilsin |
| Başkasının ilanı | `403 NOT_LISTING_OWNER` (404 değil) | İlanlar giriş yapmış herkese görünür; varlığı gizli değil |
| Kapatma | İdempotent (kapalıysa 200, değişiklik yok) | Çift tıklama / ağ tekrarı hata vermesin |
| Detay | Her durumdaki ilan görünür | Arayüz "ilan kapandı/süresi doldu" gösterebilsin; içerik zaten herkese açıktı |
| Zamanlayıcı aralığı | 1 dakika, parti 500 | Okumalar zaten doğru durumu gösterdiğinden kalıcı yazım acil değil; DB yükü düşük |
| Sayfa boyutu | Varsayılan 20, en çok 50 | Diğer servislerle aynı |

## 4. Güvenlik

| Katman | Koruma |
| --- | --- |
| Yetki | Güncelleme/kapatma yalnız sahip; IDOR negatifleri (başkası 403, değişiklik yok; yok 404) testli |
| Enjeksiyon | Süzgeç değerleri parametre; LIKE jokerleri kaçışlanır; sıralama sabit (istemci sıralama alanı seçemez) |
| Girdi | Süzgeç metinleri uzunluk + kontrol karakteri denetimi; enum/UUID/tarih biçim hatası 400; sayfa boyutu sunucuda sınırlı; imleç en çok 128 karakter, sıkı ayrıştırma |
| Eşzamanlılık | `@Version` + istemci `version`'ı; zamanlayıcı sürüm artırır, aynı anda gelen sahip yazımı 409 alır; `SKIP LOCKED` örnekler arası çakışmayı önler |
| Kaynak tüketimi | Sayfa başına sabit sayıda sorgu (N+1 yok); zamanlayıcı parti ve tur sınırlı |
| Log | Zamanlayıcı yalnız olay kodu + sayı/hata türü yazar |

## 5. Gereksinim → test → dosya

| Gereksinim | Test | Ana dosya |
| --- | --- | --- |
| LFP-10 | `ListingSearchTest` (sıra, durumlar, sayfalama, sayfa boyutu/imleç) | `ListingQueryService.search`, `ListingSpecifications.openAt`, `KeysetCursor` |
| LFP-11 | `ListingSearchTest` (platform, mikrofon, rol, seviye, zaman, metin, birleşim, geçersiz süzgeç) | `ListingSearch`, `ListingSpecifications` |
| LFP-12 | `ListingUpdateCloseTest` (7), `ListingTest` | `ListingService.update`, `Listing.update` |
| LFP-13 | `ListingUpdateCloseTest` (4), `ListingTest` | `ListingService.close`, `Listing.close` |
| LFP-14 | `ListingTest.sureSonundaDurumExpiredSayilir`, `ListingSearchTest.kendiIlanlarim…`, `ListingSearchTest.detay…` | `Listing.statusAt`, `ListingSpecifications.statusAt`, `ListingMapper` |
| LFP-15 | `ListingExpiryTest` (4), `ListingSearchTest.aktifIlanlar…` | `ListingExpiryService`, `ListingExpiryWorker`, `ListingRepository.expireDue` |

## 6. Değişen dosyalar

- `backend/services/lfp-service/`: `application.properties` (sayfa boyutu, `ltz.lfp.expiry.*`), `ListingController` (5 yeni uç), `LfpApiPaths`, `UpdateListingRequest`, `ListingPageResponse`, `ListingResponse`/`ListingView` (+`version`), `ListingQueryService`, `ListingService` (update/close), `ListingExpiryService`, `ListingSearch`, `ListingPageView`, `KeysetCursor`, `UpdateListingCommand`, `ListingMapper` (okuma anı), `Listing` (statusAt/update/close), `LfpErrorCode` (6 yeni kod), `ListingRepository` (Specification + `expireDue`), `ListingSpecifications`, `ExpiryProperties`, `ListingExpiryWorker`, `ListingProperties` (sayfa boyutu), `LfpApplicationConfig` (`@EnableScheduling`).
- Testler: `ListingSearchTest`, `ListingUpdateCloseTest`, `ListingExpiryTest`, `ListingTest` (genişletildi), `IntegrationTestSupport` (yardımcılar, zamanlayıcı testte kapalı).
- `docs/api.md` (LFP ortak kurallar: durumlar, süre dolumu, sayfalama; 5 yeni kayıt).
- Migration yok: V141'deki indeksler (`ix_lfp_listings_open_created`, `ix_lfp_listings_open_game_platform`, `ix_lfp_listings_open_expires`, `ix_lfp_listing_roles_role`) bu görevin sorgularını karşılar.

## 7. Açık noktalar

1. **Testler çalıştırılmadı.** Merge öncesi `.\pre-push\pre-push.cmd lfp-service` → `full dev` → SonarQube.
2. **Engel/gizlilik süzgeci** (LFP-17: engelli kullanıcıların ilanları listede/detayda görünmesin) ve ilan sahibiyle iletişim (LFP-16) LTZ-UMT-LFP-03'te.
3. **Metin araması ölçeği:** `q` içerir araması indeks kullanmaz; ilan sayısı büyürse `pg_trgm` GIN indeksi düşünülebilir (yeni eklenti = onay gerektirir).
4. **Süre dolumu bildirimi:** ilan sahibine "ilanının süresi doldu" bildirimi yok; notification-service'in alıcı ucu tanımlandığında LTZ-UMT-LFP-03 kapsamında değerlendirilecek.
