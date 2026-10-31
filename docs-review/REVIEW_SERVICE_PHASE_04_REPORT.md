# LTZ-HAM-REV-04 — Faz 4 çalışma raporu

Kapsam: yalnız `backend/services/review-service/` ve `docs-review/`; branch `feature/review-service`; veritabanı PostgreSQL. Commit, push, merge ve branch değişikliği yapılmadı.

## 1. Faz 4'ün genel durumu

**FAZ 4 KISMEN TAMAMLANDI — ENTEGRASYON BEKLEYEN GÖREVLER VAR.**

| Görev | Durum |
| --- | --- |
| REV-13 — Spam ve kötüye kullanım kontrolü | `[ ]` KISMEN TAMAMLANDI — REV-07 ENTEGRASYONU BEKLİYOR |
| REV-14 — İnceleme ve puan istatistikleri | `[x]` Review backend kabulü tamamlandı |
| REV-15 — Moderasyon sonrası içerik davranışı | `[ ]` ENTEGRASYON BEKLİYOR + ürün kararı bekliyor; kod yazılmadı |

**Devralma:** Faz 4, Codex'in REV-13 oran sınırı sorusunda kesilen oturumundan ("İncele Faz 1 PostgreSQL promptu") devralındı. Oturum kaydından doğrulananlar: Faz 4 talimatı verildi; kullanıcı önerilen sınırları açıkça onayladı (31 Ekim 2026, 12:07). Repository'de ise hiçbir Faz 4 kodu, migration'ı veya belgesi yoktu (çalışma ağacı temiz, son commit `920a3c4`, son migration V56). İş bu nedenle onaylı politikayla ilk satırdan uygulandı; Faz 1–3 kodu yeniden yazılmadı.

## 2. REV-13 — yapılan değişiklikler ve açık bağımlılıklar

Uygulanan (kullanıcı onaylı politika):

- İnceleme oluşturma: kullanıcı başına 60 saniyede 5 deneme. Beğenme ve beğeni kaldırma: ortak kotada kullanıcı başına 60 saniyede toplam 60 deneme.
- Tekrarlanan ve iş kuralı açısından geçersiz istekler kotaya sayılır (409, self-like 403, olmayan hedef 404, doğrulama 400, Game kesintisi 503). Anonim/yetkisiz istekler güvenlik zincirinde kaldığından 401/403 alır ve sayaç yazmaz.
- Aşımda 429 `ProblemDetail`, doğru `Retry-After` (pencere bitimine kalan süre, yukarı yuvarlı, en az 1 sn) ve `Cache-Control: private, no-store`.
- Sayaç PostgreSQL'de tek atomik `INSERT … ON CONFLICT DO UPDATE … RETURNING` ile tutulur; servis örnekleri arasında ortaktır. Kota iş transaction'ından önce `REQUIRES_NEW` ile kesinleşir; iş transaction'ı geri alınsa da deneme geri verilmez.
- Sınırlar `ltz.review.abuse.*` altında merkezidir (doğrulanır; yeni ENV yok). Süresi dolan sayaçlar dakikada bir silinir (bitişten 5 dk sonra).
- Kod: `AbuseAction`, `RateLimitExceededException`, `AbuseControlProperties`, `AbuseControlConfiguration`, `AbuseQuotaInterceptor`, `AbuseQuotaService`, `AbuseQuotaRepository`, `AbuseQuotaCleanup`; 429 işleyicisi `AccountErasureExceptionHandler`'a eklendi.

Bilinen sınırlar: sabit pencere algoritması pencere sınırında art arda iki pencerede `2×limit` işleme izin verebilir. Örnek saatleri NTP ile senkron olmalıdır (hesap silme engeliyle aynı işletim varsayımı). Puan PUT, tavsiye PUT, inceleme PATCH/DELETE ve okumalar için sayısal sınır onaylanmadığından uygulanmadı.

**Açık bağımlılık (REV-07):** Rapor oluşturma API'si yok; rapor kotası, mükerrer rapor engeli ve kendi incelemesini raporlayamama kuralı uygulanmadı ve test edilmiş sayılmaz. `REPORT_CREATE` eylemi aynı tabloya migration olmadan eklenebilir; mükerrer rapor tekilliği Admin sözleşmesiyle birlikte kararlaştırılmalıdır.

## 3. REV-14 — geliştirilen API ve istatistikler

`GET /api/v1/review/games/{gameId}/statistics` (kullanıcı JWT; anonim ve servis token yok):

- `ratings`: `ratingCount` (puan veren benzersiz kullanıcı), `averageRating` (REV-08/REV-12 ile aynı `round(avg(rating),2)`), 10 kovalı `distribution` (boş kova `0`).
- `reviews.activeCount`: yalnız `VISIBLE` metin incelemeleri; puan veren kullanıcı sayısından ayrıdır.
- `recommendations`: olumlu/olumsuz sayı, `totalCount`, yüzdeler (0–100, iki ondalık, HALF_UP, payda `totalCount`; payda 0 ise `null`).
- Boş veri: sıfır sayılar, 10 sıfırlı kova, `null` ortalama/yüzdeler, 200.
- Tek SQL ifadesi / tek snapshot; N+1, kalıcı sayaç, yeni tablo veya migration yok. Plan: `ix_game_ratings_game_id` ve `ix_game_recommendations_game_id` kullanılır; 10.000 puan + 4.000 tavsiye + 2.000 inceleme ~3,8 ms (sıcak yerel ölçüm, SLA değildir).

Sözleşme ayrıntısı [API belgesindedir](REVIEW_SERVICE_API.md).

## 4. REV-15 — uygulanan ve bekleyen davranışlar

**Uygulanan yeni davranış yok.** Doğrulanan mevcut durum: şemada yalnız `VISIBLE`/`DELETED` vardır; tüm okuma, güncelleme, beğeni, öne çıkan ve `activeCount` yolları `VISIBLE` beyaz listesindedir, yani gelecekte eklenecek her gizli durum bu yollarda otomatik dışlanır. Sayısal puan ve tavsiye inceleme durumundan bağımsızdır. Gizli durum şemada olmadığından bu davranış testle kabul edilmiş sayılmaz.

Bekleyenler: Admin→Review karar sözleşmesi (Admin/Auth/Notification), `HIDDEN/ARCHIVED` durum modeli, rapor kapanışı, ve ürün kararları: gizlenen/arşivlenen yazarın sayısal puanının ortalamaya girmesi, tavsiyenin aynı kuralı izlemesi, yazarın gizli içeriği silme/düzenleme hakkı, beğenilerin akıbeti ve tek aktif inceleme hakkının tutulması. Ayrıntı ve öneriler [kararlar belgesinde](REVIEW_SERVICE_DECISIONS.md) ve [Admin talebinde](request/admin-review-integration.md) §13'tedir. Sahte moderasyon ucu, kullanıcıya moderatör yetkisi, gövdeden `actorId`, yeni servis token yetkisi ve yerel karar taklidi eklenmedi.

## 5. PostgreSQL ve migration değişiklikleri

- Yeni: `V57__create_review_rate_limits.sql` — `review_rate_limits(account_hash, action, window_start, expires_at, attempts)`, PK `(account_hash, action, window_start)`, hash/action/sayaç/pencere CHECK'leri, `expires_at` indeksi. V54 gibi teknik ve kısa ömürlü tablodur; ham kullanıcı kimliği yoktur (SHA-256 özeti). Aralık: V51–V60; sonraki boş numara V58.
- V51–V56 değiştirilmedi. REV-14 için migration eklenmedi.
- Canlı/geliştirme veritabanına dokunulmadı; yalnız disposable `postgres:17-alpine` container'ı kullanıldı ve iş sonunda kaldırıldı.

## 6. Test sonuçları

Komut (yalnız Review modülü, Maven `test` aşaması): `mvn -o -B -ntp -s pre-push/ci/maven-settings.xml -gs pre-push/ci/maven-settings.xml -f backend/pom.xml -pl services/review-service -Pci -Dltz.test.postgres.port=<geçici port> test`

- Başlangıç doğrulaması (kod değişmeden): 102/102 PASS.
- Son sonuç: **135 test, 0 failure/error/skipped, BUILD SUCCESS**; Flyway V51–V57 (7 migration) doğrulandı, Hibernate `validate` geçti.
- Yeni testler (+33): `AbuseQuotaApiTest` 14, `AbuseQuotaServiceTest` 4, `AbuseControlPropertiesTest` 2, `AbuseQuotaFailureTest` 1, `ReviewStatisticsApiTest` 10, `ReviewHttpIntegrationTest` +2 (gerçek TCP/RS256/JWKS ile 429 + `Retry-After` ve istatistik erişimi).
- REV-13 kanıtları: onaylı 5/60 ve 60/60 değerleri, `Retry-After` hesabı, pencere yenilenmesi, tekrar/geçersiz/self-like/404/503 sayımı, kota deposu hatasında güvenli ret, iş transaction'ının sayacı geri almaması, 16 eşzamanlı inceleme ve 75 eşzamanlı beğenide tam sayım, ortak beğeni/kaldırma kotası, GET'lerin sınırsızlığı, 401/403'ün sayaç yazmaması, temizlik ve tablo kısıtları.
- REV-14 kanıtları: boş sonuç, dağılım ve kova toplamı, inceleme–puan–tavsiye ayrımı, REV-08 ortalamasıyla eşitlik, yüzde yuvarlama (33,33/66,67; 0,13/99,88), puan/tavsiye güncellemesi, metin silme bağımsızlığı, hesap silme, 10.000 puan/tek SQL, eşzamanlı yazıcılarla iç tutarlılık, yetki/validation.
- Mevcut `ReviewApiTest` dışındaki hiçbir test değiştirilmedi; o sınıfta yalnız test içi `review-create.limit=1000` verildi (aynı kullanıcıyla 11–12 istek atan doğrulama/eşzamanlılık testleri için).
- Sınırlar: canlı Auth/Game/Gateway E2E yapılmadı; HTTP testindeki karşı taraflar yerel sözleşme sunucularıdır. Gerçek saat kullanan HTTP kota testi pencere sınırına denk gelirse en çok iki pencere (10 kabul) tolere eder; kesin sayımlar kontrollü saatli testlerdedir.

## 7. Güvenlik ve veri sahipliği kararları

- Kimlik yalnız Auth RS256/JWKS `at+jwt` `sub` alanıdır; kota anahtarı da budur. Anonim ve servis-token erişimi eklenmedi; güvenlik yapılandırması değişmedi.
- Kota deposu hata verirse istek fail-closed 500 ile reddedilir (kota atlanmaz).
- Sayaç satırları ham kullanıcı UUID'si içermez; hesap silme bu tabloya dokunmaz, satırlar kendiliğinden silinir.
- Kendi incelemesini beğenme yasağı, silinmiş hedefte 404, tek aktif metin (409), hesap silme guard'ı ve puan/metin/tavsiye bağımsızlığı korundu.

## 8. Diğer servislere yönelik entegrasyon talepleri

Yeni talep dosyası açılmadı. [admin-review-integration.md](request/admin-review-integration.md) §13, REV-15 karar maddeleriyle genişletildi (13 başlıklı yapı korundu). Mevcut talepler (Auth, Admin, Notification, Game) öneri olarak sürer; hiçbiri uygulanmış veya doğrulanmış değildir. Başka servisin kodu değiştirilmedi.

## 9. Çalıştırılmayan kalite kontrolleri

Pre-push hook/CI, `backendCheck`, Trivy, SonarQube Scanner/Quality Gate/A-A-A, strict Maven verify, PMD ve merkezi coverage/ratchet: **KULLANICI TALEBİYLE ÇALIŞTIRILMADI.** Önceki fazların kalite sonuçları Faz 4 koduna taşınmamıştır; yeni kodun coverage/Sonar durumu bilinmiyor.

Kullanıcı kararıyla güncellenmeyenler: `docs/api.md` (merkezi katalog) ve `docs-security/`. REV-13/REV-14 endpoint bilgisi yalnız bu klasördeki [API belgesinde](REVIEW_SERVICE_API.md) tutulur.

## 9a. Rapor sonrası kullanıcı kararları

Kullanıcı raporu kabul etti: gizlenen/arşivlenen incelemelerin bağımsız puanı (ayrıca geçersiz kılınmadıkça) ortalamaya dahil kalır; bu incelemeler herkese açık listelerde ve öne çıkanlarda gösterilmez; kalan moderasyon kararları Admin entegrasyonuyla netleşir. Kayıt: [kararlar](REVIEW_SERVICE_DECISIONS.md). Kod değişmedi; hiçbir açık görev tamamlandı işaretlenmedi.

## 9b. Entegrasyon taleplerinin tamamlanması (yalnız dokümantasyon)

REV-13 raporlama ve REV-15 moderasyon için dış servis gereksinimleri mevcut Admin, Auth ve Notification talep belgelerine işlendi (13 başlıklı yapı korundu, yeni dosya yok). Sorumluluk: Review (rapor doğrulaması, tekillik, oran sınırı, idempotent karar alıcısı, uygulama ACK'i), Admin (olay kabulü/tekilleştirme, vaka ve karar yönetimi, audit, rapor kapanışı), Auth (servis kimliği ve kapsam; insan rolü ile servis kimliği ayrımı), Notification (alıcı bildirimi, tekilleştirme). Kod değişmedi; görev durumları değişmedi.

## 10. Tamamlanan ve bekleyen görevler

### FAZ 1 — LTZ-HAM-REV-01

- [x] REV-01 — Oyun puanlama
- [x] REV-02 — İnceleme oluşturma
- [x] REV-03 — İnceleme güncelleme
- [x] REV-04 — İnceleme silme
- [x] REV-05 — Olumlu/olumsuz değerlendirme
- [x] REV-11 — Tekil değerlendirme/puanlama

### FAZ 2 — LTZ-HAM-REV-02

- [x] REV-06 — İnceleme beğenileri
- [ ] REV-07 — Uygunsuz inceleme raporlama — ENTEGRASYON BEKLİYOR
- [x] REV-09 — Öne çıkan incelemeler
- [ ] REV-10 — Servis sınırları ve entegrasyon — ENTEGRASYON BEKLİYOR

### FAZ 3 — LTZ-HAM-REV-03

- [x] REV-08 — Ortalama oyun puanı
- [x] REV-12 — Toplu oyun puanı özetleri — REVIEW BACKEND TAMAMLANDI, GAME ENTEGRASYONU BEKLİYOR

### FAZ 4 — LTZ-HAM-REV-04

- [ ] REV-13 — Spam ve kötüye kullanım kontrolü — KISMEN TAMAMLANDI, REV-07 ENTEGRASYONU BEKLİYOR
- [x] REV-14 — İnceleme ve puan istatistikleri
- [ ] REV-15 — Moderasyon sonrası içerik davranışı — ENTEGRASYON BEKLİYOR

Kalan işler: REV-07 (rapor + mükerrer rapor/rapor kotası), REV-10 ve Game→Review entegrasyonu, REV-15 ürün politikaları ve Admin karar sözleşmesi; ayrıca kullanıcı isterse Faz 4 kodu için strict verify/coverage/Sonar/Trivy.
