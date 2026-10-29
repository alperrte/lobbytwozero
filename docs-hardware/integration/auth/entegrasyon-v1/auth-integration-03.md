# HW-AUTH-03 — RBAC, açık bağımlılıklar ve doğrulama planı

Tarih: 2026-10-24. Sorumlu: Alper. Durum: IMPLEMENTED / QUALITY_BLOCKED (ortak Sonar).

## 1. Amaç
Gerçek Auth kimliğini mevcut Hardware sahiplik ve uygulama izinleriyle bağlamak; eksik dış sözleşmeleri görünür tutmak.
## 2. Kapsam
46 mevcut iş ucu ve yeni D4 iç ucu. Auth/Gateway ve diğer servislerin kaynakları değiştirilmez.
## 3. Kaynaklar
Alper gereksinimleri HW-01–HW-17; docs/api.md; Auth AccessTokenIssuer, ServiceTokenIssuer,
TokenClaims, RoleCode, AccountDeletionCoordinator; Game/Social güvenlik örnekleri ve Auth D4 rehberi.
Kaynak kodu, eski genel rehberdeki henüz-uygulanmadı ifadelerinden önceliklidir.
## 4. Etkilenen endpoint grupları
Profil/bileşen/gizlilik/istatistik/karşılaştırma/batch/preview/consent: doğrulanmış kullanıcı ve mevcut ownership.
Katalog/kampanya okumaları: doğrulanmış kullanıcı. Yazmaları: aşağıdaki ADMIN eşlemesi.
Yorum ve rapor: mevcut owner/audience kuralları. Moderasyon: aşağıdaki iki rol.
Snapshot apply: ayrı cihaz kimliği; kullanıcı JWT'sinden cihaz üretilmez.
## 5. Auth sözleşmesi
Roller yalnız PLAYER, CONTENT_CREATOR, GAME_DEVELOPER, MODERATOR, ADMIN adlarından oluşan listedir.
Auth hardware:catalog:write veya hardware:reviews:moderate claim'i üretmez; bunlar yerel uygulama izinleridir.
## 6. Onaylı karar
ADMIN → hardware:catalog:write ve hardware:reviews:moderate.
MODERATOR → yalnız hardware:reviews:moderate.
Diğer üç rol → kimlik doğrulanan normal kullanıcı işlemleri. Roller ownership'i aşmaz.
İstemci header/body'sindeki rol/kimlik, scope/permissions listesi veya bozuk roles biçimi yükseltme sağlamaz.
## 7. Port / RestClient / Saga
CurrentActor, CatalogWriteAccess, HardwareReviewModerationAccess portları korunur.
JWKS tek teknik HTTP bağımlılığıdır. D4'te Auth mevcut kalıcı orkestratördür; Hardware yerel katılımcıdır.
Kişisel veriyi geri yükleyen compensation veya ikinci Saga/inbox oluşturulmaz.
## 8. Güvenlik
RS256/kid/typ/iss/aud/ham zaman alanları ve UUID subject doğrulanır. Kullanıcı azami ömrü 15 dakika,
saat toleransı 30 saniye; D4 servis ömrü en fazla 60 saniye ve tek hedef audience.
User JWT, sid veya ADMIN rolü cihaz credential'ı değildir. D4 token'ı iş uçlarında kullanılamaz.
Gateway cookie → Bearer dönüşümünde CSRF + Origin uygular; Hardware doğrudan cookie kimliği kabul etmez.
## 9. Sahiplik ve dış policy
User/Social paylaşım ve UGC kararları değişmedi: üretimde non-owner paylaşım/yorum görünürlüğü deny.
PERF aktif cihaz/sahiplik doğrulaması deny kalır. Admin/Notification teslimi yerel PENDING outbox'tır.
## 10. Dosyalar
HardwareJwtAuthenticationConverter, HardwareSecurityConfiguration, SecurityContextCurrentActor,
SecurityContextHardwareSyncDeviceAccess; yeni entegrasyon testleri, kendi API/DB/ER kayıtları.
## 11. Uygulama adımları
JWT/RBAC → D4 temizliği/fence → gerçek üretici sözleşme testleri → tüm backend pre-push → Sonar.
Nihai kanıt burada bağlantılı kalite raporuna kaydedilir; eski task testleri yeni kapı sayılmaz.

- [x] Zorunlu kaynak okuma ve Aşama 1 analizi; Aşama 2 kullanıcı onayı.
- [x] JWT/actor ve beş rolün yerel izinlere eşlemesi.
- [x] Kerem'le onaylı V8, atomik D4, audit/outbox temizliği ve fence.
- [x] Gerçek Auth üreticisi/Gateway ve PostgreSQL sözleşme testleri.
- [x] Paketli gerçek Auth/Gateway/Hardware HTTP kabulü — 16 kontrol PASS; Auth hardware görevi CONFIRMED.
- [x] Kendi endpoint/API/DB/ER kayıtları ve açık Auth talepleri.
- [x] Taze tam pre-push full dev PASS / SAFE TO COMMIT — 1138 test, %95,88, 18 HTTP PASS.
- [ ] Taze Sonar A/A/A ve Quality Gate PASS.
- [x] Son kanıt ve checkpoint kaydı; Social müdahalesinin reddi ve QUALITY_BLOCKED gerekçesi.
- [ ] İki kapı PASS sonrasında tek feature mesajıyla teslim.

Son analizde Hardware 0 açık bulgu; Social 17 nedeniyle gate FAIL, genel A/C/A.
Kullanıcı Social müdahalesini reddetti. Kanıt/checkpoint güncellendi; iki açık kutu
teslim koşulu sağlanmadığı için işaretlenmez ve hazır commit/PDA önerisi verilmez.
## 12. Test matrisi
Gerçek Auth üreticisi + JWKS, beş rol, IDOR/mass assignment, cookie/CSRF/Origin,
servis/kullanıcı tür ayrımı, D4 rehberi 17 senaryo, tüm kişisel tablolar, rollback/race/expiry;
JWKS cache/rotation/boyut/hata ve redirect davranışı. Yalnız geçici anahtar ve izole PostgreSQL.
## 13. Kabul
İki taze kalite kapısı PASS ve açık eksiklikler doğru etiketli olmadan SAFE/COMPLETE sonucu verilmez.
Canlı Auth kayıt-giriş / gerçek Gateway reverse proxy / bütün D4 Saga E2E'si yerel sözleşme testi değildir.
## 14. Uyumluluk
Mevcut request/response DTO'ları değişmez; eklenen D4 iç ucu dış Gateway'e yönlendirilmez.
Auth kaynağı, ENV, Compose, bağımlılık veya başka servisin API kataloğu değiştirilmez.
## 15. Açık talepler
[HW-AUTH-REQ-01: iptal/durum yayılımı](auth-service-talepleri/auth-requests-01.md).
[HW-AUTH-REQ-02: cihaz credential sözleşmesi](auth-service-talepleri/auth-requests-02.md).
[HW-AUTH-REQ-03: gerçek D4 DEV kabulü](auth-service-talepleri/auth-requests-03.md).
[HW-AUTH-REQ-04: DEBUG/TRACE OAuth log sınırı](auth-service-talepleri/auth-requests-04.md).
Bu belgeler talep kaydıdır; Kerem'e araçla mesaj gönderilmedi ve endpoint uydurulmadı.
## 16. Kanıt
Uygulama ve yerel testler tamamlandı. İki tam kapının sonuçları
[kalite raporunda](auth-integration-quality.md): pre-push PASS, genel Sonar FAIL.
Gerçek paketli Auth/Gateway/Hardware izole HTTP kabulü 16 kontrol PASS;
[runtime kaydı](auth-integration-04.md). Ortak dağıtım ve diğer 15 Saga ACK'i doğrulanmadı;
genel kalite kapısı nedeniyle teslim QUALITY_BLOCKED kalır.
