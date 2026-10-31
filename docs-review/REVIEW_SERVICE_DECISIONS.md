# Review Service kararları

## Onaylı kapsam ve iş kuralları

- Kullanıcı Hamza; branch `feature/review-service`. Yalnız Review backend ve bu klasör değiştirilebilir.
- Yalnız Faz 1 uygulanır. Sonraki fazlar ayrı kullanıcı onayı gerektirir.
- Kullanıcı 31 Ekim 2026 tarihinde şu politikaları onayladı: puan 1–10 tam sayı; metin olmadan puan ve tavsiye verilebilir; metin silinince bağımsız puan ve tavsiye korunur; ikinci aktif metin oluşturma 409 döner.
- Puan, metin ve tavsiye ayrı tablolarda tutulur; başka servise FK veya SQL erişimi yoktur.
- Eski `database.md`/ER şeması öneridir; Review'de mevcut iş tablosu/migration yoktur. Gerçek şema yeni PostgreSQL migration'larıyla kurulur. Mevcut migration yeniden yazılmaz.
- Puan PUT işlemi doğal `(user_id,game_id)` tekilliğiyle upsert eder. Aynı değer tekrarında sürüm/zaman değişmez. Eşzamanlı farklı puanlarda DB sırası geçerlidir; mükerrer satır oluşmaz.
- Metin POST işlemi aynı aktif kullanıcı/oyun için 409 döner. PATCH yalnız body ve güncel version kabul eder; eksik/null body reddedilir. Aynı metin/güncel sürüm değişiklik üretmez; eski sürüm 409'dur. Pessimistic row lock ve JPA @Version kayıp güncellemeyi önler.
- Kullanıcı silmesi metni NULL yapar, DELETED durumu/deletedAt yazar; aynı sahibin tekrarı 204'tür. Kayıt yoksa 404, başka sahibin kaydıysa 403. Silme Game erişimine bağlı değildir. Yeniden metin yayımlama yeni reviewId oluşturur; eski kimlik geri açılmaz. Bağımsız puan/tavsiye korunur. Hesap silme ise tüm yerel kişisel kayıtları fiziksel siler.
- Rating/tavsiye atomik UPSERT için parametrik JDBC kullanır; metin JPA entity/@Version ve pessimistic lock kullanır. Her iki yol aynı Spring transaction/DataSource ile hesap silme kilidini paylaşır. JPA validate ve open-in-view=false korunur; V52 gerçek PostgreSQL/JPA eşlemesi test edilir.
- Game doğrulaması mevcut anonim `POST /api/v1/game/games/batch` ile, DB transaction'ından önce yapılır. Kesinti/bozuk yanıt 503; unavailable oyun 404. Otomatik retry yoktur.
- Kimlik yalnız Auth RS256/JWKS `at+jwt` token'ının kanonik UUID `sub` alanıdır. Mevcut Auth rol sözleşmesi kullanılır; servis token'ları ayrı decoder ve namespace ile korunur. Cookie doğrudan kabul edilmez; Gateway mevcut cookie/CSRF politikasının sahibidir.
- Auth iptal/hesap durumu kontrolü diğer servislerde anlık değildir; en fazla access token ömrü ve saat toleransı kadar gecikme vardır. Uydurma introspection kullanılmaz.
- Son kullanıcı talimatı nedeniyle merkezi `docs/api.md` değiştirilemez. API kaydı bu klasörde tutulur; merkezi kataloğa aktarım ayrıca kapsam izni gerektirir.

## Hesap silme yarışı ve TTL kanıtı — kullanıcı onaylı Faz 1 tamamlaması

Kullanıcı, eski access token ile silme sonrası yeniden veri üretimini engelleyen geçici yerel engeli onayladı; önce gerçek token süresinin kaynak koddan doğrulanmasını istedi. Yeni bir servis/özellik veya Auth değişikliği yapılmadı.

Salt okunur doğrulama kaynakları:

- `backend/services/auth-service/src/main/resources/application.properties`: `ltz.auth.jwt.access-token-ttl=${JWT_ACCESS_TTL_SECONDS:60}s` (varsayılan 60 saniye).
- `backend/services/auth-service/src/main/java/com/ltz/auth/infrastructure/config/JwtProperties.java`: `@DurationMin(minutes=1)`, `@DurationMax(minutes=15)` (bağlayıcı en yüksek TTL 900 saniye).
- `backend/services/auth-service/src/main/java/com/ltz/auth/infrastructure/security/AccessTokenIssuer.java`: `expiresAt = now.plus(properties.accessTokenTtl())`; iat ve nbf aynı now.
- Root Compose yalnız okundu: varsayılan JWT_ACCESS_TTL_SECONDS=60. Gerçek ENV değerleri okunmadı; ortamdaki seçilmiş TTL doğrulandığı iddia edilmez.

Review yerel kullanıcı doğrulamasında exp-iat en fazla 15 dakika, ileri iat toleransı 30 saniye, exp toleransı 30 saniyedir. `UserTokenPolicy` engeli **900 + 30 + 30 + 60 = 1020 saniye (17 dakika)** hesaplar. Ek 60 saniye güvenlik payıdır; doğrulanmamış 16 dakika varsayımı kullanılmadı. Auth gelecekte 15 dakikalık üst sınırı artırırsa Review önce güvenli biçimde uzun token'ı reddeder; TTL/politika/kontrat birlikte güncellenmelidir. Hesap ve veri tabanı saatlerinin işletim ortamında UTC ve senkronize olması gerekir; sınırsız saat kayması varsayılmaz.

Tüm yazıcılar `AccountWriteLock.requireWritable` ile aynı hesabın PostgreSQL transaction advisory lock'unu alır ve aktif engeli kontrol eder. Silme de aynı kilidi alır; engel upsert'i ve üç tablonun silinmesi tek transaction'dır. Yarışta önce biten yazma silinir; sonra sıralanan yazma 403 alır. Başka hesapların kilit anahtarları farklıdır (64-bit hash çakışması yalnız gereksiz seri çalışma yaratır, yetki vermez). Silme tekrarı expiresAt'i geriye çekmez.

V54 yalnız `account_hash` (SHA-256, 64 hex) ve `expires_at` tutar; açık UUID, metin, token veya profil verisi tutmaz. Bu teknik kısa ömürlü tablo entity/aggregate değildir; doğal hash PK ve atomik UPSERT kullanır, gereksiz UUID/version eklenmez. Dakikalık temizlik yalnız süresi dolan özetleri siler. Servis çalışırken yaklaşık 17–18 dakika saklama; servis kapalıysa sonraki başarılı temizliğe kadar. Mevcut migration dosyaları yeniden yazılmadı.

## Doğrulama durumu

REV-01/02/03/04/05 kabul testleri sırasıyla tamamlandı. REV-11 ve final kalite koşusu sonuçları faz raporunda kaydedilir. Gerçek ENV okunmadı, canlı DB'ye migration uygulanmadı. Gerçek HTTP testi yerel JWKS/Game sözleşme sunucuları kullanır; canlı Auth/Game/Gateway E2E veya Sonar kanıtı değildir.
