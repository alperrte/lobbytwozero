# LIB-10 / LIB-11 / LIB-14 — Library tarafı bitti; diğer servislerde kalan işler

Belge kimliği: `LIB-10-11-14-Kalan-Isler-Servis-Sorumluluklari` · Tarih: 9 Kasım 2026 · Hazırlayan: Hamza (library-service)
Okuyucular: **Kerem** (user-service ve auth-service), **Alper** (recommendation-service ve frontend), **Umut** (social-service)

> Kısa özet: Library servisi bu üç görev için kendi kodunu bitirdi ve DEV'e aldı. Görevlerin kabul ölçütlerinin geri kalanı
> başka servislerin işine bağlı. Aşağıda **kime ne kaldığı**, **neden gerektiği** ve **bitti sayılması için ne görülmesi
> gerektiği** yazıyor. Bu belge hiçbir servisin kodunu değiştirmez; sahipleri kendi kararını verir.

Önceki talepler: `user-library-Integration-request.md`, `recommendation-library-Integration-request.md`,
`social-library-Integration-request.md`, `auth-library-Integration-request.md`. Kerem'in cevabı:
`docs-user/integration/services/library-review-talepleri/library-cevap-01.md` (`feature/user-service`, commit `357213cc`).

---

## 1. Library'de tamamlananlar (kanıtla)

| Görev | Library'de yapılan | Kanıt |
| --- | --- | --- |
| LIB-11 | `UserServiceLibraryVisibility`: her okumada User'a `POST /internal/v1/privacy/decisions` (`field=LIBRARY`). Yalnız `allowed` okunur; true → ALLOW, false → 403, hata/zaman aşımı/bozuk yanıt/eksik sahip/token yok → 503. Önbellek ve retry yok | 9 adaptör testi (sahte HTTP), `LibraryApiTest.PrivacyTests` |
| LIB-10 | `LibrarySummaryChanged` yayıncısı: V43 `library_summary_outbox`, her koleksiyon değişiminde veriyle aynı transaction'da işaret, relay mutlak sayıları gönderim anında okuyup User'ın `POST /internal/events` ucuna iletir. Mevcut sahipler V43 ile bir kez işaretlenir. Hesap silinince bekleyen işaret silinir | 6 yayıncı testi, 13 PostgreSQL testi; **DEV'de** sentetik kullanıcıyla Auth token'ı + DEV User teslimi 2xx (iki kez) |
| LIB-14 | Aynı adaptör `field=RECOMMENDATION`, `viewerId = ownerId` ile rıza sorar; rıza yoksa 403, karar alınamazsa 503. Sinyal ucu `signals:read` + `account_id` ile korunur (önceden hazırdı) | adaptör testleri (RECOMMENDATION alanı, farklı viewer → DENY), `LibraryApiTest.PrivacyTests` |
| Ortam | `docker-compose.yml` library-service bloğuna `AUTH_LIBRARY_SERVICE_CLIENT_SECRET` referansı eklendi; Auth ve Library konteynerleri aynı sırrı taşıyor (`docker inspect` ile doğrulandı, değer yazdırılmadı) | DEV konteynerleri |

Toplam: 148 test geçti, Checkstyle/PMD sıfır ihlal. Merkezi kapsam kapısı ve `pre-push` bu makinede çalıştırılamadı (aşağıda §6).

---

## 2. Kime ne kaldı

### 2.1 Kerem (user-service)

| # | İş | Neden gerekli | Bitti sayılma ölçütü |
| --- | --- | --- | --- |
| **K1** | `feature/user-service` dalını **push edip PR açmak ve `main`'e almak** (commit `357213cc`: V76 `user_consents`, `GET/PUT /me/consents`, `RECOMMENDATION` karar alanı) | DEV'deki User servisi `RECOMMENDATION` alanını bilmiyor; Library bu alanı sorunca 400 alıyor ve 503 veriyor. Öneri sinyali **çalışmıyor** | `main`'de V76 var; DEV User `field=RECOMMENDATION` isteğine 200 dönüyor |
| **K2** | `ltz.user.social.projection-enabled=true` açılışına karar vermek ve Umut'un başlangıç aktarımının bittiğini teyit ettikten sonra açmak | Kapalıyken User, sahibi olmayan izleyiciye `allowed=false` döner: başkasının PUBLIC koleksiyonu da **403** olur. Library'nin "izinli kişi görür" yönü bu bayrağa bağlı | Bayrak açık; sahibin `library=PUBLIC` ayarında B kullanıcısı A'nın koleksiyonunu 200 ile görüyor, `PRIVATE` ve engelde 403 |
| **K3** | **Hesap ayarları sayfasına rıza anahtarı** eklemek (`features/user`, `GET/PUT /api/v1/user/me/consents`) | Arayüz yoksa hiçbir gerçek kullanıcı öneri rızası veremez; LIB-14 pratikte kullanılamaz kalır | Kullanıcı arayüzden rızayı açıp kapatabiliyor; kapatınca Library sinyal ucu bir sonraki istekte 403 |
| **K4** | Belgeler: `docs/api.md` USER gizlilik kararı bölümünde "tüketicilerle uçtan uca" ifadesini Library için güncellemek; `profile_counters` hesap silme sonrası yeniden oluşma notunu `NOTLAR.md`'ye yazmak (cevap belgesinde yapacağını belirtmişti) | Sözleşme kaydı güncel kalsın | İlgili bölümlerde Library satırı `VERIFIED` ya da açık nedenli `NOT VERIFIED` |
| **K5** | Hamza ile **ortak uçtan uca deneme** (§5 kontrol listesi) | Gerçek hesaplı akış hiç denenmedi | §5 maddeleri geçti |
| K6 | (Auth, bilgi) `library-service → user-service` çifti (`privacy:read`, `events:ingest`) hazır ve çalışıyor. Ek iş yok | — | — |

DEV'de kalan veri: Library'nin sentetik teslim denemeleri yüzünden DEV User `profile_counters` içinde sıfır sayılı, hiçbir uçtan
okunamayan iki satır (rastgele kimlik) var. Başka servisin verisi olduğu için Library silmedi; Kerem uygun görürse temizler.

### 2.2 Alper (recommendation-service ve frontend)

| # | İş | Neden gerekli | Bitti sayılma ölçütü |
| --- | --- | --- | --- |
| **A1** | **Recommendation → Library istemcisini yazmak.** Recommendation servisi bugün yalnız boş iskelet (`RecommendationServiceApplication` ve `.gitkeep`) | LIB-14'ün kabulü "Recommendation yalnız izinli veriyi sözleşmeli API'den alır" der; çağıran taraf yok | Aşağıdaki akış çalışıyor |
| **A2** | Compose'ta `recommendation-service` bloğuna `AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET` referansını eklemek ve değeri `.env.dev`'e koymak (üretici: `node ops/dev.mjs keys`) | Auth çifti (`recommendation-service → library-service`, `signals:read`) tanımlı, ama istemci sırrı konteynerde yoksa token alınamaz | Konteynerde sır dolu |
| A3 | `docs/api.md` RECOMMENDATION bölümünü ve gerekiyorsa V11–V20 aralığında migration'ı kendi servisinde kaydetmek | AGENTS.md §4.1 | Kayıt var |
| A4 | Frontend ortak sözleşmesi: Kerem'in K3'teki rıza ekranı için `apiRequest` kalıpları (CSRF, 401 sonrası yenileme) | K3'ü engellememesi için | Ortak kalıp kararı |

**A1 — Recommendation'ın uygulayacağı akış** (Library sözleşmesi: `docs/api.md` → LIBRARY → "Recommendation girdileri (LIB-14)"):

1. Her kullanıcı için `POST {AUTH_SERVICE_URL}/internal/v1/service-tokens` ile `clientId=recommendation-service`, `audience=library-service`,
   `accountId=<kullanıcı UUID>` gönder; `signals:read` kapsamlı, 60 sn token gelir (hesaba bağlıdır, başka kullanıcıda kullanılamaz).
2. `GET {LIBRARY_SERVICE_URL}/internal/v1/library/users/{userId}/recommendation-signals?page=0&size=50` çağır
   (sayfa ≥ 0, boyut 1–50; yalnız `gameId`, `favorite`, `completed` döner).
3. Durum kodlarını **ayırt et:**
   | Kod | Anlam | Recommendation ne yapmalı |
   | --- | --- | --- |
   | 200 | İzin var, veri geldi | Öner |
   | **403** | Kullanıcı öneri rızası vermemiş (ya da kapsam/hedef reddi) | Kullanıcıyı **atla**, boş liste sayma, tekrar deneme baskısı yapma |
   | **503** | Rıza kararı alınamadı (User kapalı vb.) | Geçici hata; sonra yeniden dene, "veri yok" sayma |
   | 401 | Token/yapılandırma hatası | Alarm, yeniden deneme yok |
   | 400 | Sayfa/boyut/UUID hatası | Kodu düzelt |
4. Library verisini kalıcı kopyalama (veri sahibi Library; ZG-8). Gerekeni kadar alan kullan; kişisel liste loglama.
5. Rıza geri alınırsa bir sonraki istekte 403 gelir; saklanan türetilmiş veriyi bu durumda silme/kullanmama politikasını Recommendation belirler (ZG-11).

### 2.3 Umut (social-service)

| # | İş | Neden gerekli | Bitti sayılma ölçütü |
| --- | --- | --- | --- |
| **U1** | Mevcut arkadaşlık ve engellerin **başlangıç aktarımı**nı (yeniden yayın) User'a tamamlamak ve Kerem'e haber vermek (`docs-user/integration/services/social-service-talepleri/social-requests-01.md`) | Kerem, aktarım bitmeden `projection-enabled` bayrağını açamaz (eski engeller bilinmeden izin verilirdi). Bayrak açılmadan Library'de başkasının koleksiyonu görünmez (K2) | Kerem aktarımı teyit etti |

Library'nin Social'dan ayrıca bir isteği yok; `FRIENDS_LIST` adaptörü Social'ın kendi görevidir.

### 2.4 Gateway, Notification, Game ve diğerleri

Ek iş yok. Library iç uçları Gateway'de yönlendirilmez (doğru); `GET /api/v1/library/users/{userId}/entries` zaten Gateway'den geçer.

---

## 3. Bağımlılık sırası

```
Umut U1 (aktarım) ──► Kerem K2 (bayrak) ──► "başkası görür" yönü çalışır (LIB-10/11)
Kerem K1 (PR/merge) ──► DEV'de RECOMMENDATION kararı ──► Library sinyal ucu 200/403
Kerem K3 (arayüz) ─────► gerçek kullanıcı rıza verebilir
Alper A1+A2 ───────────► Recommendation sinyalleri gerçekten tüketir (LIB-14 kabulü)
Hepsi hazır ──► Kerem K5 + Hamza ortak E2E ──► "NOT VERIFIED" ifadeleri kalkar
```

K1 ve U1 birbirinden bağımsız, hemen başlayabilir. A1 için Kerem'in K1'ini beklemek gerekmez (403 ile 200 yolları ayrı denenebilir).

---

## 4. Kabul ölçütleri, bugünkü durum

| Görev | Kabul | Bugün |
| --- | --- | --- |
| LIB-11 | Gizli koleksiyon yalnız frontend'de değil sunucuda kapalı; yetkisiz istek ve User'a erişilememe gizli veriyi açmaz | **Library'de sağlandı** (testle). Başkasının görmesi K2'ye bağlı |
| LIB-10 | User profil akışına yetkili, görünürlüğe uygun koleksiyon verisi sunulur | Özet olayı DEV'de teslim ediliyor. Gerçek hesapta `summary.library` doğrulaması K5'e bağlı |
| LIB-14 | Recommendation yalnız izinli veriyi sözleşmeli API'den alır; veri sahibi Library kalır | Library tarafı hazır. Rıza kararı K1'e, arayüz K3'e, tüketici A1'e bağlı |

---

## 5. Ortak uçtan uca kontrol listesi (K5)

Önkoşul: User, `feature/user-service` içeriğiyle ve `projection-enabled=true` ile çalışıyor (`ops/dev.mjs` içindeki `launchUser(true)` ya da K1+K2 sonrası DEV).
İki test hesabı A ve B (`node ops/dev.mjs accounts`). Sahip ayarları `PUT /api/v1/user/me/privacy` ve `PUT /api/v1/user/me/consents` ile hazırlanır (veritabanına elle yazma).

1. A koleksiyona oyun ekler → birkaç saniye içinde `GET /api/v1/user/profiles/{A}` yanıtında `summary.library.status=AVAILABLE` ve doğru sayılar.
2. A `library=PUBLIC` iken B `GET /api/v1/library/users/{A}/entries` → **200**. A `PRIVATE` yapar → **403**. Engel varsa → **403**.
3. User'ı durdur → B'nin isteği **503** (veri yok); User'ı aç → eski davranış.
4. Rıza: rızasız `signals:read` token'ı ile `GET /internal/v1/library/users/{A}/recommendation-signals` → **403**; `PUT /me/consents` ile rıza → **200** (yalnız `gameId`, `favorite`, `completed`); rıza geri → **403**.
5. A hesabı silinir (Auth D4) → Library kaydı ve bekleyen özet işareti silinir.
6. Yetkisiz: B'nin token'ı ile A'nın `recommendation-signals` ucu → **401/403**; `userId` sahteciliği → yok sayılır.

---

## 6. Hamza'da kalanlar ve bilinen sınırlar

- `.\pre-push\pre-push.cmd service` ve merkezi kapsam kapısı: Claude'un çalıştırdığı `node pre-push/ci/backend.mjs` bu makinede "Disposable PostgreSQL startup failed" ile düştü (elle başlatılınca konteyner sağlıklı). Kendi terminalinde çalıştırılacak; sonuç `SAFE TO COMMIT` olmadan PR açılmaz.
- E2E (§5) K1, K2, K3 sonrası Kerem'le yapılacak; sonuca göre `docs/api.md` LIBRARY satırları `NOT VERIFIED`'den `VERIFIED`'e çekilecek.
- Eski `library:profile:read` iç uçlarının (DEPRECATED) kaldırılması ayrı temizlik işidir.
- `contextLoads` testi `-Pci` dışında geliştirici DB'sine (`ltz_dev`) bağlanır. V43 DEV'e 9 Kasım 2026'da yedek (`library_db`, Git dışı) ve geri alma etiketiyle (`ltz/library-service:dev-rollback-v43`) uygulandı.
- Başkasının koleksiyonunu gösteren bir frontend sekmesi bu görevlerin kapsamında değil; istenirse ayrı görev olarak planlanır.

## 7. Doğrulanmayanlar

- Gerçek hesaplarla uçtan uca akış (§5) hiç çalıştırılmadı.
- `RECOMMENDATION` kararı gerçek User ile denenmedi (DEV User henüz bu alanı bilmiyor).
- Recommendation tüketicisi yok; sinyal ucu yalnız Auth'un gerçek token üreticisi ve sahte User kararıyla sınandı.
- Merkezi kapsam kapısı ve `pre-push` sonucu elde edilmedi.
