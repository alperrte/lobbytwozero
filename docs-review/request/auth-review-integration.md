# AUTH–REVIEW entegrasyon talebi

## 1. Talep eden servis

`review-service` — Hamza. Bu belge Review'ın ihtiyacını bildirir; Auth sahibine kesin geliştirme emri değildir.

## 2. Hedef servis

`auth-service` — Kerem. Nihai yetki/istemci tasarımı Auth sahibince belirlenir.

## 3. İlgili task

REV-07, REV-10 ve REV-12'nin bekleyen Game→Review sunucu entegrasyonu. Bildirim üreticisi Review seçilirse koşullu bildirim yetkisi de REV-10 ile ilişkilidir. REV-06 ve REV-12'nin tamamlanan Review kullanıcı API kabulü bu talepten bağımsızdır. REV-13'ün raporlamaya bağlı kısmı ve REV-15'in entegrasyon bağımlılıkları (Admin→Review moderasyon kararı) da bu belgeyle ilişkilidir; ikisi tamamlanmış sayılmaz.

## 4. Talebin amacı

Gelecekte onaylanacak rapor teslimi ve moderasyon geri bildirimini, kullanıcı token'ını servis kimliği yerine kullanmadan, hedefe bağlı ve en az yetkili servis token'ıyla gerçekleştirmek.

Faz 3 eki: Game'in Review toplu puan özetini sunucudan kendi servis kimliğiyle okuyabilmesi. Kullanıcı mevcut JWT güvenliğinin korunmasını ve yeni servis-token erişiminin bu fazda uygulanmamasını açıkça istedi.

## 5. Repository üzerinde doğrulanan mevcut durum

- [Auth application.properties](../../backend/services/auth-service/src/main/resources/application.properties): `review-service` istemci kaydı ve `${AUTH_REVIEW_SERVICE_CLIENT_SECRET:}` referansı var; **yalnız** `targets.user-service=events:ingest` tanımlı. Gerçek secret değeri/dağıtımı incelenmedi.
- Aynı dosyada community→admin ve community→notification `events:ingest` örnekleri var; bunlar Review'a yetki vermez. Admin'in mevcut hedefi community; admin→review tanımlı değil.
- [ServiceClientConfig](../../backend/services/auth-service/src/main/java/com/ltz/auth/infrastructure/config/ServiceClientConfig.java): hedef kapsam allowlist'i `events:ingest`, `blocks:check`, `privacy:read`, `signals:read`; tanımsız özel kapsam uydurulamaz. `account:erase` ayrı D4 sözleşmesidir ve bu iş için kullanılamaz.
- Mevcut `POST /internal/v1/service-tokens`, hedef audience seçimini destekler. Kayıtsız hedef 403, geçersiz istemci 401 davranışı [Auth servis sözleşmesinde](../../docs-auth/servis-sozlesmeleri.md) belgeli.
- Kullanıcı JWT/JWKS ve Auth→Review D4 silme entegrasyonu zaten mevcut; yeniden kurulması talep edilmiyor.
- Game→Review hedef/kapsamı veya bu amaçlı Game istemci kaydı application.properties içinde yok. Review kullanıcı özet API'leri servis token'ı kabul etmez. Mevcut kapsam allowlist'inde özel puan özeti okuma izni yoktur; D4 silme izni kullanılamaz.

### Auth entegrasyon doğrulaması — güncel durum (31 Ekim 2026)

Güncel Auth kaynağı ve [servis sözleşmeleri](../../docs-auth/servis-sozlesmeleri.md) ile karşılaştırıldı. Etiketler: **IMPLEMENTED / VERIFIED** (kodda var ve testle doğrulandı), **REVIEW TARAFINDA TAMAMLANDI**, **AUTH TARAFINDA GELİŞTİRME BEKLİYOR**, **SÖZLEŞME BEKLİYOR**, **NOT VERIFIED**. "VERIFIED" yalnız şu anlama gelir: Auth'un gerçek üretim kaynakları test sırasında geçici dizine derlenip testin kendi geçici anahtarıyla çalıştırıldı; **canlı Auth servisi ile uçtan uca doğrulama yapılmadı (NOT VERIFIED)**.

| # | Madde | Sorumlu | Etiket |
| --- | --- | --- | --- |
| 1 | Kullanıcı `at+jwt` doğrulaması: RS256/JWKS, `iss`, `aud=ltz-api`, `typ`, `kid`, zaman alanları, UUID `sub`, ≤15 dk ömür | Review | IMPLEMENTED / VERIFIED (gerçek Auth `AccessTokenIssuer` çıktısı ve JWKS ile kabul; alg none, HS256, bozuk imza, yanlış `iss`/`aud`/`typ`, eksik/bilinmeyen `kid`, süresi dolmuş/henüz geçerli olmayan, bozuk `sub` reddi). Canlı Auth: NOT VERIFIED |
| 2 | Roller → yetki: beş kanonik rol; Review `MODERATOR`/`ADMIN`'e ek yetki tanımaz; liste olmayan/bilinmeyen `roles` yetki vermez | Review | REVIEW TARAFINDA TAMAMLANDI (bu görevde sertleştirildi; `aud` claim'i eksik imzalı token artık 500/NPE yerine 401) |
| 3 | Auth → Review servis token'ı: `typ=svc+jwt`, `aud=review-service`, `sub=svc:auth-service` (tam), `scope=account:erase`, `account_id` = yol | Review | IMPLEMENTED / VERIFIED (gerçek `ServiceTokenIssuer`; başka `aud`/kapsam/hesap bağı ve kullanıcı token'ı reddi) |
| 4 | Auth → Review hesap silme (`DELETE /internal/v1/accounts/{id}`): Auth'un gerçek `HttpAccountDataEraser`'ı Review'ın gerçek HTTP sunucusuna karşı; idempotent 204; başka hesap korunur; yanlış `aud` onay sayılmaz (`SERVICE_ERASURE_HTTP_401`) | Review | IMPLEMENTED / VERIFIED. Canlı Auth silme akışı (14 gün bekleme, worker): NOT VERIFIED |
| 5 | Silme × REV-13 kota sayaçları: silme sayaç satırlarını da siler; silinmiş hesabın eski token'ı kota satırı yeniden oluşturamaz (403) | Review | REVIEW TARAFINDA TAMAMLANDI |
| 6 | Review → User `events:ingest` (`ReviewSummaryChanged`): Auth çifti **mevcut** (`review-service` → `user-service`, `events:ingest`), User alıcısı mevcut (önerilen T7 sözleşmesi) | Auth hazır; Review üretici | Auth tarafı VERIFIED (yapılandırmada); Review üreticisi/token istemcisi **NOT IMPLEMENTED** (REV-10; `AUTH_REVIEW_SERVICE_CLIENT_SECRET` için ENV onayı gerekir) |
| 7 | Review → Admin rapor (`events:ingest`) | Auth, Admin | AUTH TARAFINDA GELİŞTİRME BEKLİYOR (çift tanımsız) + SÖZLEŞME BEKLİYOR (Admin alıcısı yok) |
| 8 | Admin → Review moderasyon kararı | Auth, Admin | AUTH TARAFINDA GELİŞTİRME BEKLİYOR (çift ve kapsam tanımsız; Auth bunu "tanımsız çift" A6 olarak izliyor) + SÖZLEŞME BEKLİYOR |
| 9 | Game → Review toplu özet okuma | Auth, Game | AUTH TARAFINDA GELİŞTİRME BEKLİYOR (`game-service` istemcisi ve hedef/kapsam yok; Game'de Review tüketicisi yok) + SÖZLEŞME BEKLİYOR |
| 10 | Review → Notification (koşullu, üretici Review ise) | Auth, Notification | SÖZLEŞME BEKLİYOR |
| 11 | İnsan moderatör yetkisinin Review'a güvenilir aktarımı (servis kimliği ≠ insan yetkisi) | Admin, Auth, Review | SÖZLEŞME BEKLİYOR |
| 12 | Canlı Auth ↔ Review uçtan uca | Tümü | NOT VERIFIED |

Süreç notu: Auth sahibi kendi klasöründe (`docs-auth/verilen-cevaplar/review-service/talep-NN-….md`) talep görmek ve izin listesine ekleme yapmak ister. Bu görevde yalnız `docs-review/` yazılabildiğinden ihtiyaç bu belgede tutulur; Auth sahibi buradan alıp kendi klasörüne taşıyabilir. Auth kodu, izin listesi veya belgeleri değiştirilmedi.

## 6. Eksik gereksinimler

- Admin ile alıcı protokolü onaylanınca Review'ın yalnız rapor olayını Admin'e iletebilmesine uygun hedef/kapsam çifti.
- Admin'in onaylı rapor durum/karar mesajlarını yalnız Review'a iletebilmesine uygun ters hedef/kapsam çifti.
- **Koşullu:** moderasyonun uygulandığı bilgisini Notification'a Review gönderecekse Review→Notification için ayrı hedef/kapsam. Üretici Admin seçilirse gereksiz Review izni verilmemeli.
- Mevcut Review→User izni korunmalı; bu taleple genel admin yetkisi, başka hesap okuma veya tüm hedeflere erişim istenmiyor.

**REV-13 / REV-15 için Auth sorumluluğu (yalnız gerçekten gerekli olanlar):**

- **[Auth] REV-13:** Review'ın rapor kotası, tekilliği ve sayacı Review içinde çözülür; Auth'tan yeni izin istenmez. Rapor teslimi için gereken tek çift yukarıdaki Review→Admin çiftidir ve Admin alıcı protokolü onaylanana kadar bekler.
- **[Auth] REV-15 — Admin→Review moderasyon kararı:** Servis token'ı yalnız *hangi servisin* konuştuğunu kanıtlar. `events:ingest` mevcut allowlist'te bir olay alma kapsamıdır; moderasyon *komutu* (gizle/arşivle/yeniden görünür yap) için ayrı bir kapsam gerekip gerekmediği veya `events:ingest` + Review'daki tam producer/olay-türü allowlist'inin yeterli olup olmadığı **KARAR BEKLİYOR** (Auth ve Admin sahipleri). Yeni kapsam adı bu belgede öngörülmemiştir.
- **[Auth] İnsan moderatör ile servis kimliğinin ayrımı:** Auth'ta kullanıcı rolleri (`MODERATOR`, `ADMIN`) vardır, fakat Review bu rollere özel bir yetki tanımaz; `/api/v1/review/**` tüm izinli rollere aynı kullanıcı işlemlerini açar ve moderasyon yetkisi vermez. Bir kullanıcı `at+jwt`'si hiçbir koşulda Review'da moderasyon kararı yerine geçmemeli. İnsan moderatörün kimliğinin/yetkisinin Review'a nasıl güvenilir aktarılacağı (Admin'in doğrulayıp audit aktör referansını imzalı servis çağrısı içinde taşıması mı, Auth'un aktöre bağlı bir token üretmesi mi) **KARAR BEKLİYOR**; body'deki `actorId` tek başına kabul edilmez.
- **[Auth] Bildirim:** Üretici Admin ise Review→Notification izni gerekmez; Review ise yukarıdaki koşullu çift geçerlidir. Üretici seçimi [Notification talebinde](notification-review-integration.md) **KARAR BEKLİYOR**.

## 7. Önerilen sözleşme — kesinleşmedi

Faz 3 ek talebi: Game→Review için yalnız puan özeti okumaya izin veren hedef/kapsam çifti. `rating-summaries:read` yalnız örnek isim önerisidir; mevcut allowlist'te yoktur ve uygulanmadı. Mevcut izinlerden birinin uygunluğu veya yeni kapsam gerekip gerekmediği Auth sahibince belirlenmeli. Mevcut token edinme protokolünün kullanımı ve Review'da dar okuma alıcısı karşılıklı onay bekler. Kullanıcı API'si veri modeli için [Game talebi](game-review-integration.md) esas alınabilir; servis-token alıcısı bugün mevcut değildir.

Yeni token endpoint'i yerine mevcut token ucunun kullanılması önerilir. Kavramsal istek: `clientId=review-service`, bellekte taşınan mevcut client secret, `audience=admin-service`. Gerçek secret belgeye/komut satırına yazılmaz.

Önerilen en dar çiftler:

- review-service → admin-service: alıcı olay modeli seçilirse `events:ingest`.
- admin-service → review-service: geri bildirim olay modeli seçilirse `events:ingest`; Review ayrıca tam producer/olay türü allowlist'i uygular.
- review-service → notification-service: yalnız üretici kararı Review ise `events:ingest` önerisi.

Bunlar bugün yapılandırılmış izinler değildir. Senkron komut veya durum sorgusu seçilirse farklı kapsam gerekip gerekmediğine Auth ve hedef servis sahipleri karar verir; `events:ingest` otomatik okuma/moderasyon yetkisi sayılmaz. Mevcut response alanları `accessToken`, `tokenType`, `expiresIn`, `scope` üzerinden kalabilir; yeni token claim'i dayatılmıyor.

## 8. Kimlik doğrulama ve yetkilendirme beklentileri

Mevcut D3 modeliyle uyum: RS256/Auth JWKS, `typ=svc+jwt`, geçerli `kid`, issuer, tek ve doğru hedef audience, tam `sub=svc:<çağıran>`, scope, ham iat/nbf/exp/jti, en fazla 60 saniye ömür ve sözleşmedeki saat toleransı. Alıcı token sub ile zarf producer eşleşmesini ayrıca denetlemeli. Kullanıcı `at+jwt`'si, başka audience veya başka servis kimliği kabul edilmemeli.

Servis token'ı insan moderatör rolünün yerine geçmez. Kararı alan insanın doğrulanması, yetki kapsamı ve audit aktörünün Review'a nasıl güvenilir aktarılacağı Admin/Auth/Review sahiplerinin ortak kararıdır. Sadece body içindeki actorId'ye dayanarak moderasyon yapılmamalı. D4 hesap silme token'ı bu akıştan ayrı tutulmalı.

## 9. Hata ve güvenilirlik beklentileri

Game okumasında da token edinimi/yenilenmesi bounded olmalı; yanlış yetki için kullanıcı token'ına veya account:erase kapsamına fallback yapılmamalı. Salt okunur puan sorgusu için outbox gerekmez.

Secret boş/geçersiz, token üretimi başarısız, hedef/kapsam reddi veya süresi dolmuş token durumunda teslim fail-closed olmalı; kullanıcı JWT'sine/header'a fallback olmamalı. Token kısa süreli bellekte tutulmalı; gizli değerler loglanmamalı. 401 ile 403 ayrımı korunmalı. Bounded timeout, yenileme ve retry bütçesi gerçek teslim sözleşmesiyle birlikte belirlenmeli. Yetkisizlikte kontrolsüz retry yapılmamalı.

Outbox/relay bu belgede yalnız gelecekteki güvenilirlik gereksinimidir; Review'da bu çalışma kapsamında geliştirilmedi. Auth token üretmesi tek başına Admin'in raporu kalıcı kabul ettiğinin kanıtı değildir.

## 10. Kabul kriterleri

- Game→Review çifti onaylanırsa yalnız doğru sub/aud/typ/scope ile özet okunur; diğer endpointlere yetki yayılmaz. Yanlış kapsam, yanlış servis, süre sonu ve kullanıcı token'ının servis kimliği yerine kullanılması reddedilir. Gerçek Auth token'ıyla Game/Review entegrasyonu ayrıca doğrulanır; bu fazda NOT VERIFIED.

- Test ortamında her onaylı çift doğru audience ve gerekli kapsamla token alır; onaylanmayan çiftler 403, yanlış secret 401 alır.
- Yanlış alg/imza/typ/issuer/audience/producer/scope, eksik zaman claim'i, expired token ve kullanıcı token'ı alıcılarda reddedilir.
- Mevcut Review→User ve Auth→Review D4 regresyonları geçer; yeni imtiyaz yayılımı oluşmaz.
- Gerçek Auth token'ıyla Admin/Review alıcı uçları arasında olumlu ve olumsuz HTTP testleri yapılır; yalnız mock token üretimi teslim kabulü sayılmaz.

- REV-15, karar onaylanırsa: insan moderatör kullanıcı token'ı, başka bir servisin token'ı, doğru servis ama yanlış kapsam ve doğru token fakat gövdedeki sahte aktör referansı Review karar alıcısında reddedilir (olumsuz testler); servis kimliğiyle insan yetkisi karıştırılmaz.

## 11. Bağımlılık ve etki

Admin alıcı protokolü ve güven modeli birlikte onaylanmadan REV-07 gerçek teslimi ve REV-10 tam entegrasyonu kapanamaz. REV-06'nın kullanıcı JWT'siyle yerel beğeni akışını engellemez.

Game servis kimliği eksikliği REV-12 Review backend kabulünü engellemez; Game→Review gerçek tüketimi ve REV-10 kapanışı bekler. Review kullanıcı JWT'sini Game servis kimliği olarak kullanmayacak.

## 12. Durum

**SÖZLEŞME BEKLENİYOR / TALEP–ÖNERİ — Yeni hedef yetkileri henüz uygulanmadı.** Auth kodu, ENV, Compose veya ortak ayarlarda değişiklik yapılmadı.

Review tarafındaki Auth uyumluluğu (kullanıcı JWT, roller, `svc+jwt` alıcısı, hesap silme, kota/silme etkileşimi) **REVIEW TARAFINDA TAMAMLANDI ve yerel olarak VERIFIED**; canlı Auth ile uçtan uca **NOT VERIFIED**. Aşağıdaki Auth çiftleri (Review→Admin, Admin→Review, Game→Review, koşullu Review→Notification) **AUTH TARAFINDA GELİŞTİRME BEKLİYOR / SÖZLEŞME BEKLİYOR** olarak kalır; bu yüzden REV-07, REV-10, REV-12'nin Game tüketimi ve REV-15 tamamlanmış sayılmaz.

## 13. Karar bekleyen konular

Faz 3: Game istemci kimliği, hedefe bağlı dar kapsam, allowlist uyarlaması, güvenli dağıtım ve Review okuma alıcısının yolu netleştirilmeli. Yeni scope/ENV/token bu fazda üretilmedi.

Olay mı senkron komut mu kullanılacak; hangi çiftler hangi kapsamı alacak; Notification üreticisi kim olacak; insan moderatör yetkisi nasıl doğrulanacak; secret'ın mevcut güvenli dağıtımı ve rotasyonu nasıl işletilecek? Nihai kararı ilgili servis sahipleri verir; Review sonradan onaylı sözleşmeye uyarlanır.

**REV-13/REV-15 eki — KARAR BEKLİYOR:** moderasyon komutu için ayrı kapsam gerekliliği; insan moderatör yetkisinin Review'a aktarım modeli (Admin güveni mi, aktöre bağlı Auth token'ı mı); Notification üreticisi seçilirse buna bağlı çift. REV-13'ün kota/tekillik ihtiyaçları Auth kararı gerektirmez.

**Auth entegrasyon görevi eki (31 Ekim 2026) — Auth sahibinin kararını bekleyenler (Review hiçbirini uydurmaz):** (1) `admin-service` → `review-service` ve `review-service` → `admin-service` çiftlerinin ve kapsamının var olup olmadığı; mevcut `events:ingest` allowlist'i yeterli mi, moderasyon *komutu* için ayrı kapsam gerekir mi. (2) Game için Review'ı okuyabilen istemci/hedef çifti ve kapsam; mevcut allowlist'te (`events:ingest`, `blocks:check`, `privacy:read`, `signals:read`) puan özeti okumaya uygun bir kapsam yok, `rating-summaries:read` yalnız örnek addır. (3) İnsan moderatör yetkisinin Review'a aktarım modeli: Auth servis token'ından kullanıcı yetkisi türetmez ve delegasyon token'ı üretmez (servis-sozlesmeleri §3.5); dolayısıyla Admin'in doğrulayıp imzalı servis çağrısıyla aktarması mı, başka bir model mi kararlaştırılmalı. (4) Review → User `ReviewSummaryChanged` için Review tarafında üretici ve `AUTH_REVIEW_SERVICE_CLIENT_SECRET` dağıtımı REV-10 onayı ve ENV onayıyla birlikte ele alınır; Auth tarafında ek geliştirme gerekmez.
