# TASK 4 / LTZ-ALP-REC-04 — checkpoint

2026-11-15 · Alper · **BLOCKED — 0/3 DONE**.

| Kod | Gerçek durum | Eksik sözleşme / owner |
| --- | --- | --- |
| REC-13 | BLOCKED; benzer oyuncu önerisi NOT IMPLEMENTED | İzinli User aday keşfi/tercih batch'i (Kerem) ve Social eligibility (Umut/Kerem) |
| REC-14 | BLOCKED; arkadaş önerisi NOT IMPLEMENTED | Recommendation'a izinli current friend + iki yönlü block + erişim batch kontrolü (Umut), Auth izni ve User aday/işleme amacı (Kerem) |
| REC-15 | BLOCKED; etkinlik önerisi NOT IMPLEMENTED | Event iş API'si yok; açık/geçerli etkinlik keşfi ve viewer katılım/kapasite/zaman eligibility'si (Nisa) |

Master v2 §8.1 kritik gizlilik garantisi yoksa öneri üretmemeyi ve eksik sözleşmeleri
belgelemeyi emreder. AGENTS.md gereği başka owner'ın servisine müdahale edilmedi.
İsim taraması, N+1 profil/ilişki okuması, özel library/rating kullanımı veya başka caller
taklidiyle bu açıklar kapatılmadı. Owner Library rızası sosyal aday olmaya genişletilmedi.

## Gerçek kod/test teslimi

Production endpoint/controller/DTO/ranker/adapter **eklenmedi**. Mevcut security allowlist ve
denyAll aynı kaldı. `/players`, `/friends`, `/events` yalnız negatif test probe yollarıdır;
uygulanmış veya vaat edilen API kaydı değildir. Uygulanmamış kabiliyet başarılı boş liste dönmez.

- `RecommendationApiIntegrationTest.java`: üç yeni test; her probe GET/POST'ta A/B/ADMIN
  token'larını, anonim GET ve CSRF'siz POST'u, kullanıcı yolunda servis token reddini kontrol eder.
- Header/query/body userId, candidateIds ve includePrivate erişim açmaz. Yetkili JWT bile
  mevcut denyAll sınırında 403 alır; ProblemDetail/no-store, aday/kimlik verisi sızmaması ve
  kaynak iş HTTP çağrısı/DB run/observation yazısı yokluğu doğrulanır. JWKS çağrısı hariçtir.
- `application/service/FixedRateLimiterConfiguration.java`: yalnız bu API test context'ine
  import edilen gerçek RecommendationRateLimiter + sabit Clock. Eski quota testinin dakika
  sınırında 11. isteği yeni pencereye taşıyan kararsızlığını giderir; limit azaltılmaz/kapatılmaz.

Bu üç boundary testi self/friend/block/özel profil veya etkinlik filtre/ranking kabulü değildir.
Kaynak sözleşmeleri olmadan pozitif öneri fixture'ı veya kullanılmayan production iskeleti eklenmedi.

## Belgeler ve kapsam

- [User talebi](../integration/dependencies/user-service-requests.md),
  [Social talebi](../integration/dependencies/social-service-requests.md),
  [Event talebi](../integration/dependencies/event-service-requests.md): mevcut DTO/izin kanıtı,
  eksik en dar kabiliyet, owner ve yeniden açılma kabul testleri.
- [Karar](../decisions/task-04-decisions.md), contracts, master checklist, issues, commit
  taslağı, test raporu ve manuel güvenlik notu güncellendi.
- `docs/api.md`: yalnız Recommendation bölümünde NOT IMPLEMENTED kapsam ve gerçek ret
  davranışı not edildi. Yeni/değişen endpoint yok; başka owner'ın API kaydı değiştirilmedi.
- Mimari §10 mevcut blokajı anlatır. DB/ER şeması aynı; migration gerekmedi.

ENV, Compose, bağımlılık, production güven modeli ve Auth/User/Social/Event/Hardware/
Performance üretici kaynakları değiştirilmedi. Gerçek ENV okunmadı veya değiştirilmedi.
Başlangıç worktree temizdi; TASK 3 kullanıcı commit'i `3aa01735` olarak görüldü.
Git stage/commit/push/PR/merge veya kimlik işlemi yapılmadı.

## Doğrulama

[TASK 4 test raporu](../testings/task-04-tests.md) komutları, tüm koşu sonuçlarını ve sınırları taşır.
Son koşu: **69 PASS / 0 failure / 0 error / 0 skipped**; Maven clean verify ve merkezi
runner exit 0. Toplam LINE %97,48; merkezi ek/değişen kod %97,71 (önceki taskları da kapsar).
Checkstyle/PMD 0 yeni ihlal; JAR dependency gate PASS. İlk iki başarısız koşu ve test
düzeltmeleri raporda korunur; başarısızlıklar PASS sayılmaz.
Gerçek Auth issuer/JWKS ve disposable PostgreSQL kullanılır; User/Social/Event üreticileri
canlı çağrılmadı. Feature filtre/sıralama/eligibility ve canlı çok servis E2E NOT VERIFIED.

- [x] Üç REC için mevcut sözleşme/izin engeli ve owner talebi kaydedildi.
- [x] Kapalı erişim sınırı testleri ve test zamanı deterministikliği eklendi.
- [x] Checklist/API/mimari/karar/entegrasyon/test/sorun/commit belgeleri güncellendi.
- [x] 69 test ve hedefli kalite kapıları geçti; feature blokajı ayrı tutuldu.
- [x] git diff --check, yeni dosya whitespace/link ve 23 kaynak gereksinim metni karşılaştırması PASS.
- [ ] REC-13/14/15 tamamlandı — kritik sözleşmeler eksik; checkbox'lar açık tutuldu.
- [ ] Pozitif aday sıralama, self/friend/block/private/deleted/disabled ve etkinlik time/capacity testleri — NOT IMPLEMENTED.
- [ ] Final SonarQube / legacy pre-push — TASK 5'e ayrıldı; merge-ready iddiası yok.

TASK 1/2/3 açıkları korunur; genel desteklenen kapsam sayacı 13/23 DONE'dur.
**DUR: TASK 5 başlatılmadı. Sonraki açık devam talimatı beklenir.**
