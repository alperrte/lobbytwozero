# Auth — Açık Güvenlik Borçları Kaydı

> Güncelleme: 2026-10-21 (Faz 6 sonu). Bu liste **tamamlanmış gösterilmeyen** işleri izler. Repository geneli Security
> güncellemesi ve altı faz sonrası kapsamlı denetim sırasında yeniden ele alınacaktır.

| ID | Borç | Etki | Bağımlılık / karar |
| --- | --- | --- | --- |
| B1 | **KISMEN KAPANDI:** Gateway Auth uçlarında IP başına dakikalık sınır uyguluyor ve gelen Forwarded/X-Forwarded-* başlıklarını siliyor (`GATEWAY_TRUSTED_PROXIES` ile güvenilir proxy tanımlanır). Auth servisi IP'yi henüz kullanmıyor; sayaç Gateway örneği belleğinde | Çok örnekli kurulumda sınır örnek başınadır; hesap/IP birleşik kilidi yok | Ortak limitleyici (Redis vb.) + Auth'a güvenilir IP başlığı sözleşmesi |
| B2 | Diğer servislerde token iptali en fazla 5 dk gecikmeli (D3) | Askıdaki hesap diğer servislerde kısa süre erişebilir | Olay/inbox altyapısı veya servis içi durum kontrolü |
| B3 | **KAPANDI (Gateway):** CORS allowlist, cookie → Bearer çevirisi, çerezli isteklerde CSRF + Origin doğrulaması uygulandı | Servislerde JWT doğrulaması hâlâ yok (her servis kendi sahibi) | Servis sahipleri `AUTH_SERVICE_ENTEGRASYON_REHBERI.md` ile doğrulayıcıyı ekler |
| B4 | Servis kimliği (D4) uygulanmadı | Admin→Auth ve Auth→servis iç çağrıları kullanıcı token'ına/yok adaptöre bağlı | Sözleşme: `servis-sozlesmeleri.md` §3 |
| B5 | Production Mail Service yok (3 e-posta portu); `prod` profili bu yüzden başlamaz | Production'da e-posta akışları çalışmaz | MAIL-01..03 + D4 |
| B6 | Hedef servislerde silme ucu yok; silme görevleri PENDING kalır | Hesap `DELETED` olamaz, kişisel veri Auth'ta korunur | Servis geliştirmeleri + D4 |
| B7 | İlk ADMIN oluşturma yolu yok | Yönetici uçları yalnız elle ADMIN verilmiş hesapla kullanılır | Proje sahibi kararı |
| B8 | BANNED kaldırma ve süreli askı kararı yok | Yasak kalıcı; askı süresi otomatik bitmez | Ürün kararı |
| B9 | **KISMEN KAPANDI (SEC-01):** geçici kayıt/bekleyen hesap temizliği var. `security_events` ve durum/rol geçmişi saklama süresi **uygulanmıyor** (varsayılan: silme yok) | Denetim tabloları büyür | Hukuki saklama süresi kararı → `ltz.auth.cleanup.security-event-retention` |
| B10 | Hesap bağlama/ayırma ucu yok (Google/Steam) | Kayıtlı e-postalı Google girişi `ACCOUNT_LINK_REQUIRED` ile durur | Faz sonrası iş |
| B11 | Parolasız hesap parola belirleyemez | Sosyal hesap sahibi parola ile giriş yapamaz | Ürün kararı |
| B12 | Parola değişti/sıfırlandı/silme bildirim e-postaları yok (MAIL-03) | Hesap sahibi izinsiz değişikliği e-postadan öğrenemez | Mail Service |
| B13 | Bağımlılık yamaları yalnız auth-service pom'unda (Tomcat 11.0.25, Jackson 2.21.7/3.1.7) | Diğer 17 servis CVE'li sürümlerde | Ortak parent pom / Boot yama sürümü |
| B14 | Gerçek Google/Steam ile uçtan uca denenmedi | Sahte sağlayıcı testleri gerçek davranışı kanıtlamaz | OAuth kimlik bilgileri |
| B15 | Zamanlama farkı: parola sıfırlama isteğinde bilinmeyen hesapta DB yazısı yok | Milisaniye mertebesinde sızıntı | Gerekirse sabit gecikme |
| B16 | Application katmanı Spring Data repository'lerini doğrudan kullanıyor | Katman saflığı borcu | Gerekirse port ayrımı |
| B17 | Silme sonrası geri alma süresi (ör. 14 gün) yok; silme onayda başlar | Yanlışlıkla silme geri alınamaz | Hukuki/ürün kararı |
| S1 | Silme onayında Auth PII'sinin (e-posta, kullanıcı adı, parola, TOTP, kimlikler) hemen anonimleştirilmesi **uygulanmadı** | Servisler silmeyi onaylayana kadar PII Auth'ta kalır (B6 nedeniyle süresiz olabilir) | B17 geri alma süresi + zamanlama kararı (bkz. `sec-01-kapanis-raporu.md`) |
| S2 | Silme hedef servis listesi (14) ve "uygulanamaz" mekanizması yok | Verisi olmayan servis görevi sonsuza dek PENDING kalır | Servis sahipleriyle liste kararı |
| S3 | ~~`pending-account-ttl=PT24H` varsayımı~~ **ONAYLANDI** (proje sahibi, 2026-10-21) | - | Kapandı |
| S4 | Bekleyen hesap e-posta adresini TTL boyunca (24 saat) meşgul eder (kayıt sahibi kanıtı için kimlik bilgisi üzerine yazılmaz) | Kısa süreli e-posta "squatting" | Kısa TTL veya e-posta sahibi kanıtlı yeniden kayıt tasarımı |
| S5 | **Migration geçmişi değişmezliği:** SEC-02'de (30121eb) V61/V62 içeriği değiştirildi; Faz 1'de uygulanmış yerel `auth_db` sağlama toplamı uyuşmazlığı verdi (2026-10-21'de yalnız yerel DEV `auth_db` yedeklenip yeniden oluşturuldu). | Uygulanmış bir migration'ı değiştirmek, o sürümü uygulamış her ortamda Flyway doğrulamasını bozar | **Kural:** uygulanmış/commit'li migration değiştirilmez, `flyway repair` ile susturulmaz; değişiklik yeni sürümle (V65+). Push öncesi bir kez daha doğrulanır |
| S6 | ~~Yerel DEV `AUTH_JWT_PRIVATE_KEY` geçersiz~~ **KAPANDI (2026-10-21):** anahtar 3072 bit RSA'ydı ama PKCS#1 biçimindeydi; aynı anahtar PKCS#8'e çevrildi (`.env`, `.env.dev`). Yeni anahtar üretimi: `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -outform DER | base64 -w0` | - | Kapandı |
| S7 | **Argon2 aşırı yük koruması:** `ltz.auth.password-hashing.acquire-timeout=PT0.5S` bilinçli bir overload korumasıdır (en çok 4 eşzamanlı hash, ~19 MiB/hash; slot bulunamazsa `503 SERVICE_BUSY`). Yoğun meşru kayıt/giriş trafiğinde istemciler 503 görebilir | Kapasite aşımında kullanılabilirlik kaybı (güvenlik açığı değil); testlerde yüklü makinede 503 görüldü, test desteğinde 10 sn'ye çıkarıldı | Gerçek yük ölçümü sonrası `max-concurrent-hashes`/süre ayarı; istemcide `Retry-After` ile yeniden deneme; Gateway tarafında hız sınırı (B1) |
| S8 | **SameSite değerlendirmesi (karar notu, borç değil):** OAuth state cookie `Lax` + `Path=/` + `__Host-`; Google (OIDC code) ve Steam (OpenID return) dönüşleri üst düzey GET olduğundan state cookie geri gelir, `Strict` olsaydı akış kırılırdı. CSRF cookie ayrı ve `Strict` (yalnız aynı site SPA isteklerinde; GET callback CSRF dışıdır). Refresh cookie `Strict` + `Path=/api/v1/auth/session`. Cookie öznitelikleri ENV ile değiştirilemez | Bilinen sorun yok | Gerçek Google/Steam ile uçtan uca doğrulama hâlâ yok (B14); Gateway cookie→Bearer (B3) gelince yeniden bakılır |
