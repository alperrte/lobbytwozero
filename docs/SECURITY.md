# LTZ (LobbyTwoZero) — SECURITY.md

> Tarih: 2026-10-20
>
> Durum: **Zorunlu güvenlik politikası ve geliştirme standardı.** Bu belge özelliklerin tamamlandığını iddia etmez. Mevcut uygulama (`[MEVCUT]`) ile hedef gereksinim (`[HEDEF]`) ayrı değerlendirilir.
>
> Kapsam: Java 25 / Spring Boot 4.1.1 mikroservisleri (17 iş servisi + API Gateway), Spring Cloud Gateway Server Web MVC, Spring RestClient, PostgreSQL 17 / Flyway, Next.js 16 / React 19 / TypeScript arayüzü, Docker Compose, `pre-push` kalite kapısı, SonarQube ve kodlama ajanları.
>
> Eş belgeler: [web-rules.md](web-rules.md) (arayüz, erişilebilirlik, SEO, hukuk), [architecture.md](architecture.md), [database.md](database.md), [er-diagram.md](er-diagram.md), [folder-structure.md](folder-structure.md). Çelişki halinde **güvenlik kuralı gevşetilmez**; sorumludan karar alınır.

## 0. Gerçek durum, kapsam ve temel ilkeler

- LTZ; oyun kataloğu, kişisel kütüphane, inceleme/puan, arkadaşlık, mesajlaşma, oyun arkadaşı ilanı (LFP), topluluk gönderileri, haber/içerik, etkinlik, donanım profili, öneri, performans raporu, bildirim ve yönetim akışlarına sahip bir **oyuncu topluluğu platformudur**. Ana güvenlik sınırı **kullanıcı/kaynak sahipliği** ve **rol** (RBAC) bağlamıdır.
- `[MEVCUT]` altyapı: 17 iş servisi + API Gateway, tek PostgreSQL 17 instance'ında servis başına ayrı veritabanı, Flyway, Docker Compose, RestClient timeout yapılandırması ve `contextLoads` testleri. Gateway route'ları tanımlıdır; **Gateway veya servislerde henüz security/CORS filtresi, JWT doğrulaması, RBAC veya iş endpoint'i yoktur** (NOT IMPLEMENTED).
- **Deny by default, least privilege, defense in depth, fail closed, secure by design.** Frontend'in gizlediği hiçbir şey yetki kontrolü sayılmaz.
- Gereksinim dokümanındaki (docx) eski teknoloji ifadeleri (MSSQL, React+Vite, Java 21) geçersizdir; **repo kodu ve `docs/servis-gereksinimleri/` önceliklidir.**
- Kimlik doğrulama yöntemi, servisler arası güven modeli, gerçek zamanlı mesajlaşma taşıma katmanı (WebSocket/SSE) ve dosya depolama için nihai karar verilmeden uygulama önce tasarlanıp onaylanır. Geçici `permitAll` ile bir işlev "bitmiş" sayılmaz.
- Güvenliği test amacıyla kapatmak veya genişletmek yasaktır. Test izolasyonu gereken durumlarda **yalnız test kapsamındaki** güvenli düzenlemeler kullanılır.

## 1. ENV, gizli bilgiler ve onay

- Şifreler, JWT imzalama anahtarı, OAuth (Steam/Google/Discord) client secret'ları, SMTP kimlik bilgileri, harici oyun veri sağlayıcısı API anahtarları, GIF platformu anahtarı ve `SONAR_TOKEN` kaynak koda, Dockerfile'a, SQL migration'a, README'ye, frontend bundle'ına, loga veya teste **asla** gerçek değer olarak konmaz.
- Root `.env`, `.env.dev`, `.env.prod`, `*.bak` gibi gerçek ortam dosyaları **Git dışında** kalır (`.gitignore`: `.env`, `.env.*`, `!.env.example`). Repoya yalnız gerçek sır içermeyen `.env.example` konur. Ajanlar gerçek `.env*` dosyalarını **okumaz, kopyalamaz, sohbete/rapora aktarmaz**.
- Ortam değiştirme `ops/env.(cmd|ps1|sh) use dev|prod` ile yapılır; script `.env.<profil>` dosyasını `.env`'e kopyalar. Ajan bu komutu kullanıcı istemeden çalıştırmaz.
- `NEXT_PUBLIC_` değişkenleri herkese açıktır (`NEXT_PUBLIC_API_BASE_URL`); gizli değer içeremez.
- Yeni ENV anahtarı ekleme, silme, yeniden adlandırma, anlamını değiştirme veya `.env*` sözleşmesini düzenleme **öncesinde açık onay** istenir. Onaydan sonra `.env.example`, `docker-compose.yml` aktarımı ve ilgili belgeler tutarlı güncellenir. Örnek: `JWT_ISSUER` ve TTL anahtarları vardır, **imzalama anahtarı anahtarı henüz tanımlı değildir**; eklenmesi onay gerektirir.
- Production'da kritik ENV eksik, boş veya güvensizse uygulama **fail fast** eder. DEV kolaylıkları (`SWAGGER_ENABLED=true`, gevşek CORS, `devtools`) production'a sızmaz.
- SonarQube isteğe bağlıdır; geliştirici kendi sunucusunu ve `SONAR_TOKEN` değerini yerel ayarlar. Pre-push SonarQube çağırmaz, token istemez.

## 2. Repository, ajanlar ve yerel kalite kapısı

- Kodlama ajanları açık izin olmadan `git add`, commit, push, merge, rebase, release, branch silme, repository ayarı değişikliği veya deploy yapmaz.
- Push öncesi yerel kalite komutu (manuel, git hook değildir):
  - Windows: `.\pre-push\pre-push.cmd service | backend | full dev | full prod`
  - Linux/macOS: `sh ./pre-push/pre-push.sh full dev`
  - Gereksinimler: Node 22.12+, Maven, JDK 25. Backend `clean verify` + JaCoCo **≥ %80 satır kapsamı**, compose doğrulama, env/anahtar dosya adı kontrolü, actuator health kontrolü.
- `SAFE TO COMMIT` (veya backend modunda `BACKEND TEST CHECK PASSED`) **yalnız yerel kontrollerin geçtiği** anlamına gelir; SonarQube, Quality Gate veya production güvenlik onayı değildir.
- Test atlama, coverage şişirmek için production kodunu hariç tutma, güvenlik kuralını kapatma, başarısız kontrolü PASS gösterme yasaktır.
- `package-lock.json` ve Maven sürüm kontratları korunur. Yeni bağımlılığın güvenlik ve lisans etkisi incelenir; `npm audit fix --force` onaysız kullanılmaz. HIGH/CRITICAL bulgular ayrıca değerlendirilir.
- SonarQube hedefi **A** notudur; çalıştırma rehberi `sonarqube/sonarqube.md`.

## 3. Kimlik doğrulama, token ve oturum (auth-service)

- Auth sözleşmesi uygulanana kadar hiçbir endpoint'in kimlik doğruladığı varsayılmaz. `[MEVCUT]` ENV: `JWT_ISSUER=ltz-auth`, `JWT_ACCESS_TTL_SECONDS=900`, `JWT_REFRESH_TTL_SECONDS=604800`.
- `[HEDEF]` Frontend `apiRequest` `credentials: "include"` ile çalışır; bu nedenle tarayıcı oturumu **HttpOnly cookie** ile taşınır. Token'lar `localStorage`, `sessionStorage`, IndexedDB veya JS erişilebilir store içine yazılmaz. Cookie: `HttpOnly`, production'da `Secure`, uygun `SameSite`, dar path/domain ve expiry.
- Cookie ile kimlik doğrulamada state-changing isteklerde **CSRF koruması** tasarlanıp test edilir; CORS CSRF'nin yerine geçmez.
- JWT: imzalama anahtarı yalnız backend secret mekanizmasından; algoritma allowlist, signature/issuer/audience/expiry doğrulaması. Access token kısa ömürlü; refresh token **rotation, revocation, replay tespiti** ve hash'lenmiş saklama ile.
- Parolalar güvenli password encoder ile (BCrypt/Argon2) hash'lenir; düz veya geri döndürülebilir saklanmaz.
- **E-posta doğrulama kodu (AUTH-02):** 6 haneli kod auth-service'te üretilir, **hash'lenerek** saklanır, kısa TTL ve deneme limiti vardır; mail-service yalnız gönderir. Kod loglanmaz, URL query'sine konmaz.
- **Giriş (AUTH-03..06):** e-posta veya kullanıcı adı ile giriş, 2FA, Steam OpenID ve Google OAuth. OAuth/OpenID akışlarında `state`/nonce, PKCE (uygunsa), redirect URI allowlist ve sağlayıcı yanıt imzası/doğrulaması zorunludur. Harici kimlik, mevcut hesaba yalnız doğrulanmış bağlama ile eklenir; e-posta eşleşmesiyle sessiz hesap birleştirme yapılmaz.
- **Brute-force koruması (AUTH-13):** hesap + IP bazlı rate limit/geçici kilit; kullanıcı varlığını ifşa etmeyen genel hata mesajları ("e-posta veya parola hatalı").
- **Hesap durumları (AUTH-12):** `ACTIVE`, `SUSPENDED`, `BANNED`, `DISABLED`. Durum değişince açık oturumlar ve refresh token'lar iptal edilir; diğer servisler eski yetkiyle işlem yapamaz.
- **Hesap silme (AUTH-16):** GitHub tarzı — yeniden kimlik doğrulama + açık onay (ör. kullanıcı adını yazma). Silme olayı diğer servislere event ile yayılır; her servis kendi verisini anonimleştirir/siler.
- Parola değişikliği/sıfırlama sonrası diğer oturumlar sonlandırılır; sıfırlama token'ı tek kullanımlık, kısa ömürlü ve hash'lidir.

## 4. Yetkilendirme, sahiplik ve RBAC — kritik

- Aktörler (gereksinim AKT-01..10): Misafir, Kayıtlı Oyuncu, İçerik Üreticisi, Oyun Geliştiricisi, Moderatör, Sistem Yöneticisi; harici aktörler (oyun veri sağlayıcısı, bağlı platform, e-posta sağlayıcısı, zamanlayıcı) kullanıcı yetkisi taşımaz. Kesin rol/izin adları auth-service'te onaylanır.
- **`userId` daima doğrulanmış kimlikten alınır**; request body, query veya client header'ından (`X-User-Id`, `X-Role`) gelen kimlik güven kaynağı değildir.
- Tüm `id`'ler (`gameId`, `reviewId`, `postId`, `conversationId`, `listingId`, `eventId`, `deviceId`, `hardwareProfileId` vb.) **untrusted** girdidir. Her okuma/yazmada sunucu tarafında sahiplik veya üyelik kontrolü yapılır. IDOR/BOLA negatif testleri zorunludur.
- Örnek sınırlar:
  - Başkasının incelemesi, gönderisi, yorumu, ilanı, donanım profili veya performans raporu düzenlenemez/silinemez (moderatör istisnası audit'lidir).
  - Sohbet mesajları yalnız sohbet üyelerince okunur; gruptan çıkarılan üye yeni mesajları göremez.
  - Engellenen kullanıcı mesaj, arkadaşlık isteği ve LFP iletişimi başlatamaz.
  - Resmi duyurular yalnız admin; içerik yayından kaldırma yalnız moderatör/admin.
  - Oyun Geliştiricisi yalnız kendisine bağlı oyunların geliştirici içeriğini yönetir.
- Backend yetkilendirmesi Gateway'de yapılsa bile ilgili servisin sınırında **yeniden doğrulanır**. İç Docker ağı kendiliğinden güvenli değildir.
- **Gizlilik ayarları** (USER-06, LIB-11, HW-08, SOC-12, PERF-22) sunucuda uygulanır. Profil tamamen gizlenemez, fakat alan bazlı görünürlük (kütüphane, donanım, arkadaş listesi, performans) diğer servislerin API yanıtlarında da uygulanır.
- Admin panelleri `noindex` olsa bile **backend auth/RBAC olmadan açılmaz**. Admin yetkileri rol bazında ayrılır (FR-ADM-08); global admin olmak her işlemi audit'siz yapma hakkı vermez.

## 5. Kullanıcı içeriği (UGC), moderasyon ve kötüye kullanım

- İnceleme, gönderi, yorum, içerik/haber, ilan, mesaj, etkinlik açıklaması, donanım yorumu ve profil alanları **güvenilmez kullanıcı içeriğidir**.
- Sunucuda uzunluk, boş/whitespace, karakter seti ve izinli biçim kontrolü yapılır. Zengin metin gerekiyorsa allowlist tabanlı ve test edilmiş sanitizer kullanılır; frontend `dangerouslySetInnerHTML` ile kullanıcı HTML'i render etmez.
- Kullanıcılar içeriği ön onay olmadan yayımlayabilir; moderatör yayından kaldırabilir. Raporlar (REV-07, COM-16, HW-13) tekrar raporlamaya karşı `(reporterId, targetType, targetId)` tekilliğiyle korunur.
- Repost orijinal yazara atfı korur; orijinal kaldırılınca repost içeriği sızdırmaz.
- Spam/kötüye kullanım (REV-13): kullanıcı + IP bazlı oran sınırı, aynı içeriğin tekrar gönderimi ve beğeni/oy manipülasyonu kontrolleri. Beğeni `(userId, targetId)` UNIQUE.
- Moderasyon sonucu (gizleme/kaldırma/askıya alma) sahibine bildirilir, audit log'a yazılır; kaldırılan içerik ortalama puan ve sayaçlardan tutarlı çıkarılır.

## 6. Mesajlaşma ve gerçek zamanlı iletişim

- Gerçek zamanlı taşıma (WebSocket veya SSE) henüz kararlaştırılmamıştır (`[KARAR BEKLİYOR]`). Seçilen yöntemde bağlantı kurulurken kimlik doğrulanır, `Origin` doğrulanır, abonelik başına sohbet üyeliği kontrol edilir, bağlantı ve mesaj oranı sınırlanır.
- Mesaj gönderiminde istemci tarafı `clientMessageId` ile idempotency sağlanır; ağ yeniden denemesi çift mesaj oluşturmaz.
- "Benden sil" yalnız kullanıcının görünümünü etkiler; "Herkesten sil" yalnız gönderen tarafından, tanımlı süre/kurala göre yapılır ve içerik sunucuda gerçekten kaldırılır.
- Okundu bilgisi ve "okuyanlar" listesi yalnız sohbet üyelerine gösterilir.
- Mesaj içeriği log, analitik veya bildirim önizlemesinde gizlilik tercihine aykırı biçimde açılmaz.

## 7. Harici sağlayıcılar, entegrasyonlar ve SSRF

- Harici oyun veri sağlayıcıları (ör. Steam Web API) yalnız resmi API ve kullanım koşullarıyla kullanılır. **SteamDB veya benzeri sitelerden scraping yapılmaz.**
- Sağlayıcı anahtarları yalnız ENV/secret üzerinden; istekler timeout, oran sınırı ve hata toleransıyla (GAME-22) yapılır. Sağlayıcı yanıtı da **güvenilmez girdidir**; şema doğrulaması ve uzunluk sınırı uygulanır.
- Kullanıcı tarafından verilen URL'lere sunucu istek atmaz; zorunluysa allowlist, şema, DNS çözümleme ve iç/özel IP engeli uygulanır. RestClient `redirects=dont-follow` ayarı korunur.
- **Bağlı hesaplar (USER-07/08):** yalnız Steam ve Discord. Kullanıcı açık onayıyla bağlanır, minimum scope istenir, erişim token'ları şifreli saklanır, bağlantı kaldırıldığında token'lar silinir ve mümkünse sağlayıcıda iptal edilir. Bağlı hesaptan alınan veri yalnız kullanıcının onay verdiği amaçla kullanılır (NFR-SEC-09).
- GIF platformu (MSG-22) çağrıları backend proxy veya güvenli istemci anahtarı ile yapılır; içerik filtresi ve rating ayarı belirlenir.

## 8. Mikroservis, Gateway ve RestClient güvenliği

- Tüm istemci trafiği **API Gateway** üzerinden girer (H-13, CON). Path standardı: `/api/v1/<servis>/**` (ör. `/api/v1/hardware/**`). Servis host portları DEV'de yalnız `127.0.0.1`'e bağlıdır; production'da yalnız Gateway/edge yayımlanır.
- Servis adresleri `<SVC>_SERVICE_URL` ENV değerlerinden okunur; servisler arası çağrılar RestClient ile doğrudan hedef servise yapılır.
- İç servisler client'ın eklediği kimlik/rol header'larına kanıtsız güvenmez. Gateway'in ilettiği kimlik bilgisi doğrulanmış JWT'den türetilir; servisler token'ı kendileri doğrular (`oauth2-resource-server`). Servisler arası kimlik modeli auth entegrasyonunda kesinleşir.
- `REST_CLIENT_CONNECT_TIMEOUT_MS=3000` / `REST_CLIENT_READ_TIMEOUT_MS=5000` korunur. Non-idempotent POST otomatik retry edilmez. Circuit breaker/kuyruk teknolojisi ancak ölçülmüş ihtiyaçla eklenir.
- Servisler birbirinin DB'sine erişmez; ham JPA entity paylaşılmaz. API sözleşmeleri DTO ile en az gerekli alanı döner. Toplu ihtiyaçlar için batch endpoint (`POST .../batch`) kullanılır; N+1 servis çağrısı yapılmaz.
- Gateway routing testi güvenlik testi değildir. Auth/CORS/CSRF eklendiğinde negatif testler (401/403) yazılır. Hedef: **20/20 yetkisiz erişim testi 401/403** (H-12).

## 9. PostgreSQL, Flyway ve veri ayrımı

- Her servis kendi veritabanını kullanır (`auth_db`, `user_db`, ..., `notification_db`; tam liste [database.md](database.md)). **Başka servisin DB'sine SQL/JPA erişimi yoktur.** Servisler arası ilişki yalnız ID referansıdır (REF), FK değildir.
- Production'da servis başına ayrı DB kullanıcısı ve least privilege; DEV'deki ortak kullanıcı production için yeterli değildir.
- `spring.jpa.hibernate.ddl-auto=validate` ve `spring.jpa.open-in-view=false` korunur. Şema yalnız Flyway ile değişir: `src/main/resources/db/migration/V{NUMARA}__{aciklama}.sql`. Numara, servise [decisions/migrations.md](decisions/migrations.md) ile ayrılmış aralıktan alınır (ör. hardware V1–V10, auth V61–V70); başka servisin aralığı veya DB'si kullanılmaz.
- Uygulanmış migration değiştirilmez. `clean` production'da kapalı; `baseline-on-migrate`, `out-of-order`, `repair`, veri reseti veya volume silme **açık izin** gerektirir.
- Sorgular parametriktir; dinamik sıralama/filtre alanları allowlist ile sınırlanır. Sayfalama limitleri sunucuda zorunludur.
- Eşzamanlılık: etkinlik kapasitesi (EVT-13), tekil inceleme (REV-11), kütüphane `(userId, gameId)` tekilliği ve beğeniler DB constraint + optimistic/pessimistic lock ile korunur; yalnız uygulama içi kontrol yeterli değildir.
- Kişisel veri saklama/imha ve backup/restore süreçleri tanımlanır; üretim verisi DEV'e maskesiz kopyalanmaz.

## 10. API kontratları, validation ve hata cevapları

- Her endpoint için method, path, public/protected, gerekli rol, sahiplik kuralı, DTO ve hata durumları dokümante edilir.
- Her request **güvenilmezdir**. Jakarta Validation, uzunluk/aralık/enum, body boyutu, sayfalama limiti sunucuda zorunludur. `[MEVCUT]` `spring.jackson.deserialization.fail-on-unknown-properties=true` korunur (mass assignment / beklenmeyen alan koruması).
- Hata yanıtları Spring `ProblemDetail` ile tutarlıdır; stack trace, SQL, iç hostname, dosya yolu, token, cookie veya kişisel veri içermez (NFR-SEC-08).
- **401** kimlik yok/geçersiz, **403** yetki yok, **404** kaynak yok veya güvenli gizleme, **409** çakışma/tekillik, **400/422** doğrulama, **429** oran sınırı.
- Swagger/OpenAPI yalnız `SWAGGER_ENABLED=true` iken açılır (varsayılan `false`); production'da kapalıdır. Örneklere gerçek secret veya kişisel veri yazılmaz.
- Actuator yalnız `health,info` açar, `show-details=never`. Ek endpoint açmak onay gerektirir.

## 11. CORS, CSRF, tarayıcı ve güvenlik header'ları

- CORS explicit allowlist ile yönetilir: `CORS_ALLOWED_ORIGINS` (DEV varsayılanı `http://localhost:5001`). Credential ile `*` birleştirilmez; method/header listeleri dar tutulur. CORS **Gateway'de tek noktada** yapılandırılır (NOT IMPLEMENTED).
- GET istekleri state değiştirmez. Cookie auth ile CSRF politikası (SameSite + token veya Origin doğrulama) tüm mutasyonlarda test edilir.
- Production: HTTPS/TLS, HSTS, `X-Content-Type-Options: nosniff`, CSP (`frame-ancestors` dahil), `Referrer-Policy`, `Permissions-Policy`. Next.js CSP nonce/hash planı gerçek render ile test edilir.
- Kimlikli/özel yanıtlar `Cache-Control: private, no-store` ile korunur; paylaşımlı cache kullanıcı verisini karıştırmaz.
- Açık yönlendirme, kontrolsüz dış link ve `target="_blank"` riskleri (`rel="noopener noreferrer"`) engellenir.

## 12. Rate limiting, idempotency ve gerçek istemci IP'si

- Ayrı risk profilleri: kayıt, giriş, 2FA, doğrulama kodu, parola sıfırlama, mesaj gönderimi, arkadaşlık isteği, rapor oluşturma, ilan/gönderi oluşturma, dosya yükleme, harici sağlayıcı çağrıları, admin mutasyonları.
- Rate limit backend/edge'de uygulanır; `429` + gerekirse `Retry-After`. IP tek başına yeterli değildir; doğrulanmış kullanıcı bazlı limit de kullanılır.
- Reverse proxy arkasında istemci IP'si yalnız güvenilir proxy zincirinden okunur; dışarıdan gelen `X-Forwarded-For`'a doğrudan güvenilmez.
- **Idempotency zorunlu alanlar:** performans verisi kabulü (PERF-18), masaüstü senkronizasyonu (PERF-17), mail gönderimi (MAIL-14), mesaj gönderimi, kütüphane/favori işlemleri (LIB-12), etkinliğe katılım, event tüketimi (inbox). `Idempotency-Key` veya doğal tekillik constraint'i kullanılır; yalnız frontend buton kilidine güvenilmez.
- Bağlantı havuzları sınırlıdır (`DB_POOL_MAX_SIZE=3`, `DB_POOL_MIN_IDLE=1`); sınırsız bellek, kuyruk veya dosya büyümesi engellenir.

## 13. Dosya ve medya yükleme

- Profil fotoğrafı/kapak (USER-03/04), gönderi/içerik görselleri (COM-10, CONT-14), mesaj görseli/GIF (MSG-17) ve sesli mesaj (MSG-21, opsiyonel) yüklemeleri için:
  - Boyut sınırı sunucuda kontrol edilir (mesaj medyası **≤ 1 MB**; diğerleri servis sahibinin belirleyeceği limit).
  - Uzantı ve `Content-Type` tek doğrulama değildir; magic byte/MIME, piksel boyutu ve kota kontrol edilir. SVG/HTML aktif içerik kabul edilmez.
  - Kullanıcı dosya adı depolama anahtarı olarak kullanılmaz; sunucu rastgele anahtar üretir. EXIF/konum metadata'sı temizlenir.
  - Özel içerik (mesaj medyası) yalnız yetkili kullanıcıya, imzalı/süreli URL veya yetki kontrollü endpoint ile sunulur. `nosniff`, güvenli `Content-Disposition`.
- Ayrı bir medya servisi yoktur; depolama çözümü karar aşamasındadır (`[KARAR BEKLİYOR]`).

## 14. Performans, donanım ve masaüstü istemcisi

- Web tarayıcısı CPU/GPU sıcaklığı veya FPS **ölçemez**. Ölçüm yokken gerçek veri varmış gibi gösterilmez; manuel kaynak (`MANUAL`) ile senkronize kaynak (`SYNCED`/`DESKTOP`) ayrı işaretlenir.
- Masaüstü istemcisi (`ltz-performance-app`) MVP sonrasıdır. Cihaz eşleştirme (PERF-01) kısa ömürlü tek kullanımlık eşleştirme kodu + cihaz başına iptal edilebilir kimlik bilgisi ile tasarlanır; cihaz token'ı kullanıcı oturum token'ı değildir ve dar scope'ludur.
- Cihazdan gelen ölçüm verisi değer aralığı, zaman damgası ve hacim bakımından doğrulanır; tekrar gönderim idempotency ile reddedilir (PERF-18).
- Donanım ve performans verisi kişisel veridir; görünürlük kullanıcı tercihlerine bağlıdır (HW-08, PERF-22). Silme talebi (HW-16, PERF-20) gerçekten uygulanır.
- REC-22 yardımcı **yerel LLM/AI modeli çalıştırmaz**; Python algoritmaları kullanıcı adına yetkisiz otomatik işlem yapamaz ve kullanıcı verisini dışarı göndermez.

## 15. Bildirim, e-posta ve admin

- **mail-service:** kalıcı kuyruk, retry, durumlar (`PENDING`, `PROCESSING`, `SENT`, `FAILED`) ve idempotency. Doğrulama kodu, sıfırlama linki/token'ı loglanmaz; gönderim geçmişinde maskelenir. SMTP kimlik bilgileri yalnız ENV/secret. Şablon değişkenleri HTML escape edilir.
- **notification-service:** kullanıcı yalnız kendi bildirimlerini okur/işaretler. Bildirim içeriği gizlilik ayarlarına uyar; engellenen kullanıcıdan bildirim üretilmez.
- **admin-service:** kullanıcı askıya alma/yasaklama, rapor moderasyonu ve içerik yönetimi **gerekçe ile** yapılır ve `audit_logs`'a yazılır (NFR-SEC-06, FR-ADM-07). Audit kaydı değiştirilemez (append-only). Admin, auth-service'in hesap durumunu kendi DB'sinde değil auth-service API'si üzerinden değiştirir.

## 16. Veri mahremiyeti, KVKK ve loglama

- Kişisel veri (e-posta, kullanıcı adı, IP, donanım/performans, mesaj, bağlı hesap, oyun kütüphanesi) amaçla sınırlı işlenir (NFR-DATA, NFR-LEG). KVKK aydınlatma, açık rıza gerektiren durumlar, saklama/imha ve başvuru kanalı [web-rules.md](web-rules.md) ile koordine edilir.
- API yanıtlarında yalnız gereken alanlar döner; e-posta başka kullanıcıya gösterilmez.
- Log ve audit: olay kodu + gerekli ID'ler. Parola, token, cookie, doğrulama kodu, OAuth token, mesaj içeriği ve tam e-posta loglanmaz (NFR-DATA log minimizasyonu). `LOG_LEVEL` production'da `INFO` veya üstüdür.
- Analitik (varsa) kullanıcı mesajı, token, özel URL veya kişisel veri almaz; isteğe bağlı çerezler onaysız başlatılmaz.
- Steam/Discord kullanım koşulları, medya hakları ve UGC politikaları ürün kararıyla değerlendirilir (NFR-LEG); bu belge hukuki danışmanlık değildir.

## 17. Docker, ağ ve production

- `[MEVCUT]` DEV Compose: tüm servis portları `127.0.0.1`'e bağlı, `ltz_network`, PostgreSQL `postgres:17-alpine`, init SQL salt-okunur mount. Frontend Compose'da değildir; ajan onaysız frontend Dockerfile/Compose servisi eklemez.
- Production'da yalnız HTTPS reverse proxy/Gateway ve public frontend yayımlanır; backend servisleri, PostgreSQL, Actuator ayrıntıları ve SonarQube internete açılmaz.
- Image'lar minimum runtime içerir; `.dockerignore` ile `.env*`, log, `node_modules`, build artefaktları image'a girmez. Container mümkün olduğunca non-root çalışır.
- Health response secret veya topoloji açığa çıkarmaz. Yedek/geri yükleme test edilmeden veri kaybı riski olan migration yapılmaz.

## 18. Test, güvenlik denetimi ve kabul kapıları

- Her işlevin test matrisi: normal kullanım + hatalı input + anonim/yanlış rol + **başka kullanıcının kaynağı (IDOR)** + engelleme/gizlilik + eşzamanlılık/idempotency (uygun olduğunda).
- Backend: JUnit + Spring Boot test slice'ları, gerçek PostgreSQL ile Flyway/JPA testleri, RestClient timeout testleri (`RestClientSettingsTest` örneği). Frontend: Vitest + Testing Library; akış olgunlaştığında Playwright E2E ve erişilebilirlik taraması.
- OWASP ASVS / API Security Top 10 / ZAP gibi araçlar yalnız **izinli test ortamlarında** kullanılır.
- Production çıkışını durduran örnekler: auth bypass, IDOR/BOLA, başka kullanıcının sohbetini okuma, engellemeyi aşma, refresh token replay, webhook/OAuth callback forgery, secret leak, dışa açık DB/servis, yetkisiz admin işlemi, geri alınamaz veri kaybı.
- Uygulanmamış kontrol **NOT IMPLEMENTED**, test edilmemiş kontrol **NOT VERIFIED** olarak açıkça raporlanır.

## 19. Ajan çalışma ve onay kuralları

- Ajan önce gerçek repository'yi ve bu belgeyi okur; plan veya gereksinimi uygulanmış özellik gibi göstermeden kodu/testleri doğrular.
- Şunlardan **önce onay** istenir: yeni public endpoint, auth/cookie/CSRF/CORS/RBAC modeli, servisler arası güven modeli, yeni servis veya DB, migration'ın veri silen kısmı, ENV değişikliği, yeni harici sağlayıcı/bağımlılık, production ağ topolojisi, başka geliştiricinin servisinde değişiklik.
- Kimlik doğrulama bir testin geçmesi için kapatılamaz.
- Tamamlanan her API için teslim bilgisi: method/path, public/protected, rol + sahiplik kuralı, request/response DTO, validation, hata statüleri, test kanıtı, ENV/migration değişiklikleri.
- Güvenlik problemi bulunursa secret sızdırmadan risk, etki, tekrar üretme koşulu ve çözüm önerisi raporlanır.

## 20. Özellik tamamlama güvenlik kontrol listesi

- [ ] Secret/ENV sızıntısı yok; yeni ENV için onay ve `.env.example` uyumu var.
- [ ] Public/protected endpoint matrisi güncel; deny-by-default uygulanıyor.
- [ ] Kimlik doğrulama ve (cookie ise) CSRF sunucuda test edildi.
- [ ] `userId` token'dan alınıyor; IDOR/BOLA negatif testleri PASS.
- [ ] Rol/izin kontrolü servis sınırında test edildi; 401/403 ayrımı doğru.
- [ ] Gizlilik ayarları ve engelleme kuralları API yanıtlarında uygulanıyor.
- [ ] UGC validation/sanitization ve spam/rate limit kontrolleri var.
- [ ] Gerekli mutasyonlarda idempotency ve tekillik constraint'leri test edildi.
- [ ] Başka servisin DB'sine erişim yok; Flyway migration güvenli; `ddl-auto=validate`.
- [ ] Hata yanıtları `ProblemDetail`, hassas veri içermiyor.
- [ ] CORS allowlist, güvenli cookie ve güvenlik header'ları uygun.
- [ ] Dosya yükleme boyut/MIME/yetki kontrolleri var (varsa).
- [ ] Harici sağlayıcı çağrıları timeout, rate limit ve şema doğrulamalı; scraping yok.
- [ ] Loglarda parola/token/kod/mesaj içeriği/kişisel veri yok; admin işlemleri audit'li.
- [ ] HIGH/CRITICAL bağımlılık riskleri değerlendirildi.
- [ ] Backend/frontend testleri ve `pre-push` gerçek sonuçları raporlandı.
- [ ] Uygulanmamış / doğrulanmamış kontroller açıkça işaretlendi.

**Temel ilke:** Kullanıcı kimliği, kaynak sahipliği, rol ve gizlilik tercihi her kritik istekte **backend tarafından** doğrulanır. Geliştirme hızı veya UI davranışı güvenlik sınırını zayıflatamaz.
