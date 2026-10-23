# LTZ-ALP-HW-03 — Donanım Gizliliği, Karşılaştırma ve Servis API'leri

Sorumlu: Alper. Başlangıç: 2026-10-21. Durum: DONE — DEFERRED_AUTH / DEFERRED_CONTRACT; 3/3. PDA kapanışı kullanıcı tarafından bildirildi.

- [x] HW-08 — Donanım gizliliği
- [x] HW-09 — Donanım karşılaştırma
- [x] HW-17 — Donanım API entegrasyonları

Kullanıcı HW-02 PDA kapanışını doğruladı ve HW-03 geçişini onayladı.
HW-02 commit 6756210; önceki 134 test/%98,02 ve iki kalite kapısı kendi kapsamıyla
kanıtlıdır. Bunlar HW-03 final sonucu değildir.

## İnceleme

Mevcut profil detail/read/mutation yalnız owner; privacy alanı/tablosu yok.
V1–V5 uygulanmış test şemasıdır; migration'lar değiştirilmeyecek, sıradaki boş sürüm V6.
User/Social API kaydı bulunamadı. Global görünürlük, arkadaşlık ve engelleme
doğrulaması için gerçek sözleşme yok. Performance/Recommendation gerçek tüketici
sözleşmesi de hazır değil; dış endpoint veya kimlik modeli uydurulmayacak.
LTZ_PDA_Alper.md repository'de yok; kök master 5/17 checklist'i temel alınır.
Genel DOCX'in FR-HW-03, NFR-SEC-05 ve NFR-DATA-04 gizlilik maddeleri .NET XML ile okundu.

[Somut gizlilik/karşılaştırma/batch önerisi](../decisions/HW03-privacy-integration-proposal.md)
hazırlandı ve kullanıcı tarafından erişim modeli ile Auth dışı güvenli erteleme
ayrı ayrı onaylandı. Gerçek dış bağlantılar DEFERRED_CONTRACT / NOT_VERIFIED.

## Korunan harici değişiklikler

Oturum başında şu beş dosya zaten değiştirilmişti: pre-push/backend-check.mjs,
menu.test.mjs, pre-push.md, quality.mjs, quality.test.mjs. Bu çalışmada düzenlenmez,
geri alınmaz ve görev diff'i diye sahiplenilmez. Kalite komutu çalıştırılmadan
güncel davranış/syntax okunur. Git add/commit/push yapılmaz.

## Test durumu ve ilk devam adımı

Mevcut runner regresyonları: node --test pre-push/menu.test.mjs
pre-push/quality.test.mjs pre-push/runtime-checks.test.mjs — 32 PASS.
HW-03 yeni test / pre-push / Sonar: NOT_RUN. Final kapsam kullanıcı isteğiyle
pre-push full dev (tüm backend + mevcut Compose kontrolleri) ve Sonar scan dev.
İlk adım HW-08 privacy migration/entity/DTO ve görünürlük filtreleri,
owner version kilidi, üretimde deny eden dış policy portu ve test-only policy adapter'ı.
Anonim/IDOR, PRIVATE/FRIENDS/PUBLIC, engelleme/bilinmeyen dış tercih, alan/kategori
ve gizli sayaç/kimlik sızıntısı, CSRF, rollback ve sürüm yarışları doğrulanacak.
HW-08 kanıtlanmadan HW-09/HW-17 geliştirmesine geçilmeyecek.

## HW-08 kanıtı

V6, iki rule collection, PRIVATE varsayılanı, GET/PUT privacy ve GET shared eklendi.
Sadece test scope'ta policy mock'u izin verir; üretim adapter'ı daima deny eder.
17 yeni MockMvc/PostgreSQL testi: görünürlük katmanları, IDOR, alan/kategori/kimlik
ve sayaç sızıntısı, dış hata/null/engel, CSRF, replacement, aggregate version ve
privacy/component paralel yarış. Modül 151 PASS, 0 failure/error/skipped,
Maven verify exit 0, 2026-10-21 06:01:40 +03; JaCoCo 776/790=%98,23.
Log: logs/hw03-privacy-final.log. Üç endpoint docs/api.md HARDWARE kaydına eklendi.
Sıradaki alt task HW-09. Final pre-push/Sonar henüz NOT_RUN.

## HW-09 kanıtı

Üç read-only POST karşılaştırma endpoint'i ve DTO/service eklendi; profil/bileşen
owner-only, katalog iki aktif genel modelle sınırlı. Farklı bileşen kategorisi 400;
bilinmeyen teknik değer null; RAM/depolama bilinen toplam ve bilinmeyen adet ayrıdır.
9 yeni integration senaryosu; toplam 160 PASS, exit 0, 2026-10-21 06:04:01 +03.
Log logs/hw03-comparison-verify.log. Üç endpoint docs/api.md HARDWARE altında kayıtlı.
Sıradaki HW-17; final tüm backend kalite kapıları NOT_RUN.

## HW-17 kanıtı

POST profiles/batch: en fazla 20 tekil owner profil, istenen sayfa boyutu toplamı
en fazla 200. Owner sorgusu + toplu count + parametrik PostgreSQL array unnest/LATERAL
sayfa sorgusu + tek hydration; 20 profil testinde üç ORM sorgusu ölçüldü (artı tek JDBC).
DTO'da kişisel profil adı/owner UUID/seri numarası/tercih yok; sayfa, toplam ve devam açık.
14 yeni integration senaryosu, modül 174 PASS, exit 0, 2026-10-21 06:10:20 +03.
Log logs/hw03-final-local.log. Dış consumer E2E DEFERRED_CONTRACT / NOT_VERIFIED.
Runner regresyonlarının tam mevcut kümesi 47 PASS: logs/hw03-runner-regressions.log.
Sonraki adım final pre-push full dev ve Sonar scan dev; henüz READY değildir.

## Final kalite düzeltmesi

İlk full denemesi durmuş DB yüzünden FAIL; mevcut LTZ container'ları başlatılınca
ikinci full 192 test/%94,94/18 HTTP PASS ve SAFE TO COMMIT verdi. Sonar V1–V7'yi
gerçek PostgreSQL'de ve 192 backend testinde doğruladı; A/A/A elde edildi fakat
üç MINOR yeni kod bulgusu gate'i durdurdu. EnumMap ve unnamed catch düzeltildi.
Taze full dev + scan dev gereklidir; [kalite geçmişi](../testing/LTZ-ALP-HW-03-quality.md).

## Final teslim — 2026-10-21

Gerekli taze kapılar tamamlandı: full dev PASS / exit 0 / SAFE TO COMMIT,
18 modül 192 test ve 18 HTTP health PASS, backend %94,94; log kapanışı 06:23:51 +03.
Sonar scan dev PASS / exit 0, A/A/A + Quality Gate PASS; new issue 0,
açık issue/hotspot 0, overall %91,2 / new %93,7 / duplication %0; 06:27:35 +03.
Analiz ad6e17fe-a77a-4269-92b7-9a64fda8e8c9. Hardware 174 PASS / %98,44;
V1–V7 PostgreSQL/Flyway/JPA PASS. Üç MINOR düzeltmesi son kodda doğrulandı.
[Teslim raporu](../reports/LTZ-ALP-HW-03-delivery.md) ve kalite kaydı eşleşir.
Toplam alt görev 13/17. HW-03 ana kutusu PDA kapanışını kullanıcı bildirene kadar
açık; HW-04/HW-05 NOT_STARTED. Gerçek Auth ve dış consumer/policy E2E ertelenmiş;
production-ready değildir. Commit/PDA kullanıcıya ait; ajan burada durur.

## PDA kapanışı ve HW-04 geçişi

2026-10-21: Kullanıcı HW-03'ü PDA'da kapattığını ve HW-04'e geçilebileceğini bildirdi.
Kullanıcı commit'i 104524a doğrulandı; başlangıç çalışma ağacı temizdi. HW-03 ana
kutusu bu kapanış bildirimiyle [x]; HW-04 başlangıç/erişim önerisi kendi kaydındadır.
