# Auth — Açık Güvenlik Borçları Kaydı

> Güncelleme: 2026-10-21 (Faz 6 sonu). Bu liste **tamamlanmış gösterilmeyen** işleri izler. Repository geneli Security
> güncellemesi ve altı faz sonrası kapsamlı denetim sırasında yeniden ele alınacaktır.

| ID | Borç | Etki | Bağımlılık / karar |
| --- | --- | --- | --- |
| B1 | Güvenilir istemci IP'si yok (K8): IP bazlı limit yok; yalnız kimlik/hesap bazlı sınırlar | Dağıtık kayıt/giriş denemesi IP ile sınırlanamaz | Gateway `trusted-proxies` (ortak servis, onay) |
| B2 | Diğer servislerde token iptali en fazla 5 dk gecikmeli (D3) | Askıdaki hesap diğer servislerde kısa süre erişebilir | Olay/inbox altyapısı veya servis içi durum kontrolü |
| B3 | Gateway CORS ve cookie → Bearer çevirisi yok | Tarayıcı entegrasyonu çalışmaz; servisler cookie ile değil Bearer ile doğrulayacak | Ortak Gateway değişikliği (Security güncellemesiyle birlikte) |
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
| S3 | `pending-account-ttl=PT24H` operasyonel varsayımdır | Çok kısa değer meşru kullanıcının kodunu geçersiz kılar | Ürün onayı |
| S4 | Bekleyen hesap e-posta adresini TTL boyunca (24 saat) meşgul eder (kayıt sahibi kanıtı için kimlik bilgisi üzerine yazılmaz) | Kısa süreli e-posta "squatting" | Kısa TTL veya e-posta sahibi kanıtlı yeniden kayıt tasarımı |
