# EVT-03 — Etkinlik detayı: gereksinim karşılama durumu

> **EVT-03 KISMİ — tamamlandı DEĞİL.** Ad, açıklama, başlangıç/bitiş zamanı (ve tür, durum) kodlandı; **"katılım bilgileri" bölümü eksik**, çünkü gerçek veri kaynağı yok (EVT-13, EVT-08). KD-4 karara bağlandı: detayı yalnız giriş yapan kullanıcılar görür, misafir 401 alır.
> Son güncelleme: 2026-11-09 · Sorumlu: Nisa · Önceki görevler: [EVT-01](evt-01-kararlar-ve-varsayimlar.md), [EVT-02](evt-02-etkinlik-turleri.md)

## 1. Kaynaklar

| # | Kaynak | İçerik |
| --- | --- | --- |
| K-1 | `LTZ_Servis_Icerikleri_Nisa.md` EVT-03 | "Etkinliğin adı, açıklaması, başlangıç/bitiş zamanı ve katılım bilgilerinin görüntülenebilmesi." |
| K-2 | `LTZ Gereksinim Dokümanı` FR-CONT-05 | "Etkinlik tarih, açıklama ve katılım bilgisi içermelidir." |
| K-3 | aynı, FR-CONT-06 | "Kullanıcı etkinliğe katılabilmeli ve etkinlik takvimini görüntüleyebilmeli. **Katılım durumu kullanıcı hesabında tutulmalıdır.**" |
| K-4 | `docs/web-rules.md` §7.3 | Arayüzde etkinlik durumları, **kalan kontenjan** ve katılım cezası (EVT-10) açıkça gösterilir. |
| K-5 | `docs/web-rules.md` §2, §8 | `(public)` keşifte "herkese açık etkinlikler"; SEO yalnız gerçekten herkese açık sayfalarda. |
| K-6 | `docs/architecture.md` §7.3, §8 | Katılım yalnız `PLANNED` etkinlikte; kapasite eşzamanlı istekte korunur; katılım durumları `ACTIVE/CANCELLED_BY_USER/CANCELLED_BY_EVENT/ATTENDED`. |
| K-7 | `docs/database.md` §3.12 | `events.capacity`, `participant_count`; `event_participations`; `participation_penalties`. |
| K-8 | Gereksinim dokümanı UC-16, izlenebilirlik tablosu | "Haber / Kampanya / Etkinlik Gör" aktörü **Kullanıcı** (UC-02/UC-05 gibi "Misafir / Kullanıcı" değil); "İçerik / Etkinlik" → `AKT-02, AKT-06` (Misafir `AKT-01` yok). |

## 2. Gereksinim karşılama tablosu

Durum sütunu: **Karşılandı** = bu görevin kapsamında kodlandı; **Eksik** = bağımlı görev gerekli, bu görevde yapılamaz; **Karar bekliyor** = ürün kararı gerekli. "Doğrulama" sütunu: neyin gerçekten çalıştırıldığı.

| # | Gereksinim | Durum | Kaynak | Kodlandı | Doğrulandı (kanıt) | Docker'da çalışan |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Etkinlik **adı** görüntülenir | **Karşılandı** | K-1 | `EventResponse.title` | Birim/WebMvc testleri yazıldı; çalıştırılmadı (EVT-03 testleri) | İmajda var (jar doğrulandı); davranış token'la denenmedi |
| 2 | Etkinlik **açıklaması** görüntülenir | **Karşılandı** | K-1, K-2 | `description` | aynı | İmajda var (jar doğrulandı); davranış token'la denenmedi |
| 3 | **Başlangıç / bitiş** zamanı görüntülenir | **Karşılandı** | K-1, K-2 | `startsAt`, `endsAt` (UTC) | aynı | İmajda var (jar doğrulandı); davranış token'la denenmedi |
| 4 | Tür ve durum görüntülenir (ek) | **Karşılandı** | K-4 | `type`, `status` | aynı | İmajda var (jar doğrulandı); davranış token'la denenmedi |
| 5a | **Kapasite** | **Eksik** (EVT-13) | K-1, K-2, K-7 | — (kolon yok) | — | — |
| 5b | **Katılımcı sayısı** | **Eksik** (EVT-13 / EVT-08) | K-1, K-2, K-7 | — (sayaç/tablo yok) | — | — |
| 5c | **Kalan kontenjan** | **Eksik** (EVT-13) | K-4 | — | — | — |
| 5d | **Mevcut kullanıcının katılım durumu** | **Eksik** (EVT-08) | K-3 | — (`event_participations` yok) | — | — |
| 5e | Katılım cezası bilgisi | **Kapsam dışı — EVT-03 zorunluluğu değil** | K-4 (arayüz kuralı), EVT-10/EVT-11 | — | — | — |
| 6 | Kimliği doğrulanmış kullanıcı görüntüleyebilir | **Karşılandı** | K-1, K-8 | `SecurityConfig` (`anyRequest().authenticated()`), `getEvent` | `EventEndpointSecurityTest` yazıldı; çalıştırılmadı | İmajda var (jar doğrulandı); davranış token'la denenmedi |
| 7 | **Misafir** (anonim) görüntüleyemez; yalnız giriş yapan kullanıcı görür | **Karşılandı** (KD-4 kararı) | K-5, K-8, KD-4 | Misafir 401 (`SecurityConfig`); görünürlük alanı eklenmedi | `EventEndpointSecurityTest` (token yok → 401) yazıldı; çalıştırılmadı | Misafir `GET` → 401 Docker'da Gateway üzerinden **gözlendi** (19:42 UTC imaj) |
| 8 | Bulunamayan etkinlik 404, geçersiz id 400, kimliksiz 401, `ProblemDetail` | **Karşılandı** | SECURITY.md §10 | `EventNotFoundException`, `GlobalExceptionHandler` | Test yazıldı; çalıştırılmadı | İmajda var (jar doğrulandı); davranış token'la denenmedi |
| 9 | `organizerId` KD-3 sonrası `null` olabilir | **Bağımlılık** (hesap silme ucu + migration) | KD-3 | Henüz değil (şu an her zaman dolu) | — | — |

**Sonuç:** 1–4, 6, 7, 8 karşılandı (kodlandı, doğrulanmadı). 5a–5d eksik (veri kaynağı yok). 9 bağımlılık. EVT-03 bu yüzden **tamamlanmış sayılmaz**.

### 2.1 5e (ceza) hakkında

Belgelerde cezanın etkinlik **detay** yanıtında bulunması açıkça istenmez. `web-rules.md` §7.3 yalnız arayüzde cezanın "açıkça gösterilmesini" söyler; cezanın kendi görünürlüğü EVT-11'in ("kullanıcı kendi cezasını, hangi etkinlik için engel bulunduğunu ve geçmişi görüntüleyebilir") ayrı uç noktasıdır. Bu yüzden EVT-03'e eklenmedi (talimat: zorunlu gereksinim olarak ekleme).

## 3. Neden "katılım bilgileri" eklenemedi? (bağımlılıklar)

Kod ve migration'lar incelendi (V111, V112): `events` tablosunda `capacity` ve `participant_count` **kolonu yoktur**; `event_participations` ve `participation_penalties` **tabloları yoktur**; kodda katılımla ilgili hiçbir sınıf yoktur. Dolayısıyla yanıta konabilecek **gerçek** bir veri kaynağı bulunmuyor. Sabit/sahte değer (ör. `capacity: 0`, `participantCount: 0`, `joined: false`) **bilerek döndürülmez**: kapasite "0" gerçekte "sınırsız", "sayı bilinmiyor" ve "dolu"yu ayırt edemez ve kullanıcıyı yanıltır.

| Eksik bilgi | Gereken altyapı | Hangi görev | Not |
| --- | --- | --- | --- |
| Kapasite, kalan kontenjan | `events.capacity` (null = sınırsız olup olmadığı **karar**), `participant_count`, `CHECK participant_count <= capacity`, yeni migration (V113+) | **EVT-13** | Eşzamanlılık: DB kısıtı + kilit (SECURITY.md §9); kapasite oluşturma isteğine de eklenir |
| Katılımcı sayısı | `participant_count` sayacı veya `event_participations` üzerinden sayım | **EVT-13 + EVT-08** | Sayaç, katılım/iptal işlemleriyle aynı transaction'da güncellenmelidir |
| Kullanıcının katılım durumu | `event_participations` (`ACTIVE`, `CANCELLED_BY_USER`, `CANCELLED_BY_EVENT`, `ATTENDED`), katılım/iptal uçları | **EVT-08** (+ EVT-09 iptal) | Yanıta kullanıcıya özel alan eklenir; yanıt artık kullanıcıya özgü olur → önbellekleme (`Cache-Control: private`) ve DTO ayrımı gözden geçirilir |

**Bu görevin engellenen kısmı:** yalnız 5a–5d. EVT-08/EVT-13 kendiliğinden geliştirilmedi. Şimdilik yanıtta sahte veya varsayılan değer yoktur. **EVT-08 ve EVT-13 tamamlandığında detay yanıtına gerçek katılım bilgilerinin eklenmesi GEREKİR** (aşağıdaki takip listesi); bu yapılana kadar EVT-03 tamamlandı işaretlenmez. Yanıt için "tam alan listesi" kilidi bilinçli olarak **yoktur**: yeni alanların eklenmesini engelleyen test konmadı.

### 3.1 Takip listesi (EVT-03'ü kapatmak için)

| # | Yapılacak | Tetikleyen görev | Durum |
| --- | --- | --- | --- |
| T-1 | `events.capacity` + `participant_count` kolonları, CHECK, migration (V113+); kapasite oluşturma isteğine eklenir; `null` = sınırsız mı kararı | EVT-13 | Bekliyor |
| T-2 | Eşzamanlı katılımda kapasite aşımını engelleyen DB kısıtı + kilit | EVT-13 | Bekliyor |
| T-3 | `event_participations` tablosu, katılım/iptal uçları, `(event_id, user_id)` kısmi UNIQUE | EVT-08 (+ EVT-09) | Bekliyor |
| T-4 | **Detay yanıtına ekle:** `capacity`, `participantCount`, `remainingCapacity` (gerçek DB değerleri; sınırsızlık için açık bir gösterim) | T-1..T-3 tamamlanınca | Bekliyor — EVT-03'ün kapanış koşulu |
| T-5 | **Detay yanıtına ekle:** mevcut kullanıcının katılım durumu (`event_participations`'tan; kullanıcıya özel yanıt → `Cache-Control: private`) | T-3 tamamlanınca | Bekliyor — EVT-03'ün kapanış koşulu |
| T-6 | `docs/api.md` EVT-03 kaydı ve yanıt şeması güncellenir; `EventServiceTest`/`EventControllerTest`'e gerçek değer testleri eklenir; bu belgede tablo satırları 5a–5d "Karşılandı" yapılır | T-4, T-5 | Bekliyor |
| T-7 | (Opsiyonel, EVT-03 dışı) ceza bilgisi yanıta konacaksa EVT-10/EVT-11 sonrası ayrıca karar | EVT-10/11 | Karar gerekmiyor |

Takip: bu liste [docs-event/README.md](../README.md) "Açık bağımlılıklar" tablosunda da izlenir.

**Başka serviste değişiklik gerekmiyor:** katılım verisi yalnız Event Service'in kendi DB'sindedir (EVT-24: sahibi yalnız Event Service). Bu nedenle `docs-event/<servis>/talep/` dosyası açılmadı.

## 4. Karar — KD-4: Misafir erişimi (KARAR VERİLDİ: yalnız giriş yapan kullanıcılar)

**Karar (Nisa, 2026-11-09):** Etkinlik detaylarını şimdilik **yalnız giriş yapan kullanıcılar** görebilir. Misafirler **401** alır. **Yeni görünürlük alanı eklenmez.**

**Gerekçe (belge karşılaştırması):**

| Kaynak | Ne söylüyor | Misafire açmayı destekler mi? |
| --- | --- | --- |
| `web-rules.md` §2, §8 | `(public)` keşifte "herkese açık etkinlikler"; SEO yalnız "herkese açık" etkinlik sayfalarında | Yalnız bir alt küme ima eder; tüm etkinlikleri kapsadığını söylemez, alt kümeyi ayıran model yok |
| Gereksinim UC-16 | "Haber / Kampanya / Etkinlik Gör" aktörü **Kullanıcı** (UC-05 gibi "Misafir / Kullanıcı" değil) | İmajda var (jar doğrulandı); davranış token'la denenmedi |
| İzlenebilirlik tablosu | "İçerik / Etkinlik" → AKT-02, AKT-06 (AKT-01 Misafir yok) | İmajda var (jar doğrulandı); davranış token'la denenmedi |
| `SECURITY.md` §0 | Deny-by-default | İmajda var (jar doğrulandı); davranış token'la denenmedi |

**Uygulama:** değişiklik gerekmedi; `SecurityConfig` zaten `anyRequest().authenticated()` ile `GET`'i kimliğe bağlar. `EventEndpointSecurityTest` token yok / bozuk / `svc+jwt` için 401'i denetler.

**Sonraki adım (karar değişirse):** misafire açma ürün kararıyla ve görünürlük modeliyle (yeni alan + migration + sahiplik kontrolü) ayrıca ele alınır; bu karar bunu **bugün** kapsamaz. `web-rules.md`'deki "herkese açık etkinlik" ifadesi bu karar nedeniyle şimdilik uygulanmamıştır (SEO/`(public)` etkinlik sayfası için backend desteği yoktur).

## 5. Bağımlılık — KD-3 ve `organizerId`

KD-3 (hesabı silinen düzenleyicinin bağlantısı kaldırılır, gösterimde "Silinmiş kullanıcı") uygulandığında `organizerId` **`null`** olabilir. Bunun için gerekenler ([hesap-silme-entegrasyonu.md](hesap-silme-entegrasyonu.md)):

- `events.organizer_id` şu an `NOT NULL`; nullable yapan yeni migration (V113+) ve Auth silme ucu (`DELETE /internal/v1/accounts/{accountId}`).
- Şu anki durum: `organizerId` **her zaman doludur**; `null` yalnız KD-3 uygulandıktan sonra oluşur. `EventResponse.organizerId` Java'da zaten `UUID` (null alabilir) olduğundan DTO değişikliği gerekmez; `docs/api.md` şeması KD-3 uygulanınca "UUID | null" yazılacak.
- İstemci (frontend) bu aşamada da `organizerId`'yi null'a dayanıklı işlemelidir.

## 6. Doğrulama durumu

**Kodlanan (bu repoda):** `GET /api/v1/event/events/{eventId}`; `EventNotFoundException`. Önceki turda eklenen `EventResponseContractTest` (tam alan listesi kilidi) ve `EventControllerTest`'teki katılım alanlarının yokluğu doğrulamaları **kaldırıldı**: testler yalnız mevcut alanların doğru değerlerle döndüğünü denetler (kimlik, düzenleyici, tür, başlık, açıklama, başlangıç/bitiş, durum, oluşturma/güncelleme zamanı); ileride katılım alanlarının eklenmesini engellemez.

**Doğrulanan (yalnız kanıtı olanlar):**
- Derleme ve Checkstyle/PMD: `mvn ... test-compile pmd:pmd` çıkış 0, `lint-ratchet` event-service 0 yeni ihlal (ajan çalıştırdı).
- Senin 19:09 (UTC) pre-push koşunda (EVT-02 sonrası, **EVT-03 öncesi** kod): event-service 72 test PASS, satır kapsamı %98,82; `EventServiceApplicationTests` gerçek `event_db`'ye **V112'yi uyguladı** ve `ddl-auto=validate` ile başladı. EVT-03 testleri bu koşuda **yoktu**.

**Doğrulanmayan:** EVT-03 testlerinin hiçbiri çalıştırılmadı; gerçek DB'den `findById` ile okuma, Gateway üzerinden çağrı, gerçek Auth token'ı.

**Docker'da bulunan:** 2026-11-09 19:42 UTC'de yalnız event-service yeniden build edilip başlatıldı (`docker compose build event-service` + `up -d --no-deps --no-build event-service`; `down` yok, volume ve diğer servisler korundu). Çalışan imajın jar'ı incelendi: EVT-01 `POST`, KD-1 (`PLAYER`, `MODERATOR`, `ADMIN`), EVT-02 (`POLL` dahil 5 tür, `V112` dosyası) ve EVT-03 `GET /{eventId}` (`EventNotFoundException` dahil) **imajda bulunuyor**. Başlatma logu: 2 migration doğrulandı, şema 112'de güncel, `Started EventServiceApplication`, `ddl-auto=validate` hatasız; sağlık 200 (doğrudan ve Gateway). Gateway üzerinden token'sız `GET`/`POST` ve sahte Bearer 401. **Doğrulanmadı:** geçerli Auth token'ıyla 201/200/403/404 davranışı (token yok), `events` tablosu 0 satır.
- Dev veritabanı (`event_db`): V111 ve V112 uygulanmış (`success = true`). V112'yi imaj değil, bir pre-push testi (`EventServiceApplicationTests`, 19:09 UTC) uygulamıştı; imajın V112'yi içerdiği ise yukarıdaki jar incelemesiyle ayrıca doğrulandı.
