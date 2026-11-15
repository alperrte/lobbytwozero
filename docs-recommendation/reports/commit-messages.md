# Commit taslakları

Bu ajan gerçek commit/stage/push yapmadı. TASK 1–5 için beş ayrı taslak vardır.
Başlık/body bağlayıcı master v2 §12 ve kullanıcının Conventional Commit talebine göre hazırlanmıştır.

## TASK 1 / LTZ-ALP-REC-01

```text
feat(recommendation): LTZ-ALP-REC-01 sürümlü kural motoru ve analitik temelini kur

- REC-20: rules-v1 motoruna sınırlandırılmış dört sinyal, katkı tabanlı skor ve gerekçe, UUID eşitlik sırası, tekil aday ve sonuç limitleri ekle.
- REC-21: değişmez RecommendationAlgorithm input/output portunu ve sürüm doğrulayan execution servisini oluştur; alternatif algoritmayı aynı sözleşmeyle test et.
- REC-23: yalnız gerçek motor çalışması için GENERATED, EMPTY ve UNAVAILABLE gözlemlerini kaydet; skor/sonuç toplamlarını gösterim, tıklama veya memnuniyet ölçümü olarak sunma.
- Veri: V11__create_recommendation_observations.sql ile recommendation_db içinde PK tekilleştirme, sayım/durum CHECK'leri ve tarih indeksini oluştur; mevcut migration'ları koru.
- Saklama: 30 günlük özet/saklama penceresi ve dakikada 1000 satırlık expired temizliği uygula; tekrar teslimi tekilleştir ve değişen içerikte conflict üret.
- Güvenlik: analitikte kullanıcı/hedef kimliği, token, profil ve ham oyun sinyali saklama; yeni HTTP veya güven modeli açmadan başlangıç 401/CSRF 403 yüzeyini doğrula.
- Testler: disposable PostgreSQL 17.11 üzerinde Maven clean verify ve merkezi runner ile 29 testi geçir; JaCoCo toplam %98,57, değişen kod %100, Checkstyle/PMD 0 ihlal ve dependency gate PASS sonuçlarını kaydet.
- Test güvenliği: context ve DB testlerini DisposableDatabaseInitializer ile yalnız geçici CI veritabanına sınırla; DEV/production verisine geri dönüşü engelle.
- Entegrasyonlar: Auth/User/Game/Library/Review/Hardware/Performance/Social/Event/Gateway gerçek sözleşme ve izin envanterini belgele; canlı tüketici veya Python çalıştırıldığı iddiası ekleme.
- Belgeler: docs/api.md, database/ER/mimari/klasör/migration kayıtlarını; TASK 1 checklist, karar, test, ilerleme ve sorun raporlarını güncelle.
```

Bu checkpoint iş HTTP endpoint'i, kullanıcı feedback akışı, Python runtime veya merge-ready
teslimi değildir. Pre-push ve ayrı SonarQube final gate'i TASK 5 kapanışında uygulanacaktır.

## TASK 2 / LTZ-ALP-REC-02 — kısmi teslim

Durum: PARTIAL; REC-01/04/17/18/19 DONE, REC-02/03/05 BLOCKED, REC-16 PARTIAL.
Başlık ve body yalnız gerçekten uygulanan kapsamı anlatır.

```text
feat(recommendation): LTZ-ALP-REC-02 tür önerisi, owner geçmişi ve feedback akışını ekle

- REC-01/04: User'ın izinli tür tercihlerini Game'in kanonik discovery filtresiyle birleştir; bounded aday penceresi, MAIN_GAME/adult=false seçimi ve açık PARTIAL/EMPTY yanıtı üret.
- REC-17: rules-v1 skoruyla tutarlı GENRE_AFFINITY gerekçesi, katkı ve eşleşen türleri yalnız sahibine döndür.
- REC-18: POST games, owner run/history okumaları, cursor sayfalaması, 30 günlük saklama ve 500 run kotasını uygula.
- REC-19: owner item için feedback GET/PUT uçlarını, LIKE/NOT_INTERESTED state'ini, version conflict ve idempotent tekrar semantiğini ekle; ilgilenilmeyen oyunu sonraki öneriden dışla.
- Veri: V12 ile run/item/feedback tablolarını, yerel FK/CASCADE, unique/check kısıtlarını ve indeksleri ekle; V11'i değiştirmeden analitik ve history yazımını aynı transaction'da tut.
- Güvenlik: gerçek Auth JWKS/RS256 doğrulaması, kullanıcı-servis token ayrımı, canonical role, rıza ve ACTIVE account-bound grant kontrolü ekle; Basic/form/local kullanıcı fallback'ini kaldır.
- Silme: Auth D4 subject/scope/account binding ile idempotent hesap temizliği ve rıza geri çekildiğinde history purge uygula; body/response/timeout/redirect ve instance bazlı oran sınırlarını koru.
- Entegrasyonlar: User/Game/Library RestClient adaptörlerini ekle; kullanıcı token'ını request kapsamında, Library servis token'ını hesap bağlı kullan; N+1 veya çapraz DB erişimi yapma.
- Yapılandırma: kullanıcı onayıyla mevcut AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET anahtarını yalnız Recommendation Compose ortamına ve property binding'ine aktar; örnek ENV yorumunu güncelle.
- Eksik kapsam: REC-02/03/05 için Game metadata ve kişisel Review rating batch taleplerini belgele; sistem uygunluğu filtresini ve eksik sinyalleri uygulanmış gibi gösterme.
- Testler: disposable PostgreSQL ve gerçek Auth issuer/JWKS ile 45 testi geçir; toplam LINE %96,83, merkezi ek/değişen kod %97,15, Checkstyle/PMD 0 ihlal ve dependency gate PASS sonuçlarını kaydet.
- Belgeler: altı endpoint'in docs/api.md kayıtlarını, DB/ER/mimari/migration belgelerini, TASK 2 checklist/checkpoint/karar/test ve sorun kayıtlarını güncelle.
```

TASK 1 commit'i kullanıcı tarafından `90857ceb` olarak oluşturulmuştu; TASK 2 bunun üzerindeki
çalışma ağacı değişiklikleridir. Ajan Git yazma veya kimlik işlemi yapmadı. TASK 3'e geçilmedi.

## TASK 3 / LTZ-ALP-REC-03 — kısmi teslim

Durum: PARTIAL; REC-07/08/10/11/12 desteklenen teknik kapsamda DONE, REC-06/09 PARTIAL.
TASK 2 kullanıcı tarafından `25ee6b44` olarak commit edilmişti; bu taslak yeni TASK 3 içindir.

```text
feature: LTZ-ALP-REC-03 owner donanım değerlendirmesi ve koşullu tavsiyeleri ekle

- API: POST /api/v1/recommendation/hardware/assessments ile owner profil, oyun ve platform üzerinden stateless değerlendirme sun; doğrulanan JWT, canonical role, rıza/ACTIVE grant, ortak kota ve private/no-store uygula.
- REC-07/08: minimum ve recommended CPU/GPU/RAM/depolama kontrollerini ayır; yalnız tam model eşleşmesini kabul et, kapasite birimi belirsizliğini byte sınırlarıyla değerlendir ve eksik alanları UNKNOWN bırak.
- REC-10: minimum compute kanıtı ve bilinen engel yokluğunda yalnız koşullu LOW başlangıç öner; ayar bulunurluğu veya FPS garantisi verme.
- REC-11/12: kanıtlı RAM/disk açıklarını yükseltme gerekçesine dönüştür; bildirilen socket/DDR uyuşmazlıklarını denetle, eksik BIOS/GPU/güç/form factor bilgisinde bütün sistem uyumu iddia etme.
- Entegrasyon: Hardware owner batch ve Game tek oyun metadata RestClient tüketicilerini ekle; mevcut Auth izniyle account-bound Performance token kullan, kaynak kimliği/sürümü/sayfalama ve bounded JSON şemasını doğrula.
- REC-06/09: katalog batch gereksinimleri ve bağlamlı FPS kanıtı eksiklerini açık tut; reliable aggregate'tan tahmin üretme, INSUFFICIENT_DATA/NONE/null döndür ve owner sözleşme taleplerini belgele.
- Güvenlik: başka kullanıcı profili, yanlış kaynak kimliği, bozuk yanıt, redirect, timeout ve kesintide kapalı başarısızlık uygula; rıza geri çekilmesinde TASK 2 geçmiş temizliğini yeniden kullan.
- Testler: 21 yeni test ve 45 regresyonla 66 testi geçir; gerçek Auth issuer/JWKS, disposable PostgreSQL ve kaynak HTTP contract fixture'larını kullan; canlı çok servis E2E iddiası ekleme.
- Kalite: toplam LINE %97,48, merkezi ek/değişen kod %97,71, Checkstyle/PMD yeni ihlal 0 ve dependency gate PASS sonuçlarını kaydet; suppression, coverage policy veya baseline değiştirme.
- Belgeler: docs/api.md, mimari, integration/dependency, karar, test, ilerleme, master checklist, sorun ve manuel güvenlik notlarını güncelle; ENV/bağımlılık/migration veya üretici servis kodu değiştirme.
```

TASK 4 başlatılmadı. Final SonarQube/pre-push ve PR taslağı TASK 5'e aittir.

## TASK 4 / LTZ-ALP-REC-04 — sözleşme blokajı ve erişim sınırı testleri

Durum: **BLOCKED — 0/3 DONE**. Başlık/gövde sosyal veya etkinlik önerisi uygulandığını söylemez.
AGENTS.md'nin zorunlu `feature:` başlığı korunmuştur; gerçek kapsam test/dokümantasyondur.
TASK 3 kullanıcı commit'i `3aa01735` üzerindeki bu çalışma Git yazma işlemi içermedi.

```text
feature: LTZ-ALP-REC-04 sosyal öneri blokajlarını belgele ve kapalı erişimi test et
- REC-13: User profil kartı ile izinli aday tercihlerini ayır; Kerem için bounded aday/tercih ve işleme amacı sözleşmesi talebi hazırla.
- REC-14: Social engel sorgusunun Messaging/LFP caller sınırını ve Recommendation Auth izin eksikliğini doğrula; Umut/Kerem için friend/block/erişim batch eligibility talebini belgele.
- REC-15: Event servisinde iş API'si bulunmadığını kaydet; Nisa için açık/geçerli etkinlik, katılım, zaman ve kapasite sözleşmesi talebi hazırla.
- Güvenlik testleri: uygulanmamış player/friend/event probe yollarında A/B/ADMIN, anonim ve servis token erişimini; sahte userId/candidateIds/includePrivate girdilerini ve veri sızdırmayan ret yanıtlarını doğrula.
- Yan etki kontrolü: reddedilen probe'larda JWKS dışı kaynak iş çağrısı ve Recommendation run/observation yazısı oluşmadığını test et; mevcut production denyAll kuralını koru.
- Test kararlılığı: API test context'ine sabit Clock kullanan gerçek RecommendationRateLimiter ekle; dakika sınırında değişen kota beklentisini production limitlerini değiştirmeden gider.
- Doğrulama: disposable PostgreSQL ve gerçek Auth issuer/JWKS ile 69 testi geçir; toplam LINE %97,48, merkezi ek/değişen kod %97,71, Checkstyle/PMD yeni ihlal 0 ve dependency gate PASS sonuçlarını kaydet.
- Belgeler: docs/api.md Recommendation kapsam notunu, mimari, contracts, owner talepleri, karar, checkpoint, test, checklist, sorun ve güvenlik notlarını güncelle; REC-13/14/15 checkbox'larını açık tut.
- Kapsam: öneri endpoint'i/ranker, üretici kodu, Auth izni, ENV, bağımlılık ve migration ekleme; eksik sosyal/etkinlik özelliklerini veya canlı E2E'yi tamamlandı gösterme.
```

TASK 5 başlatılmadı. Final pre-push/SonarQube ve PR taslağı bu checkpoint'te çalıştırılmadı.

## TASK 5 / LTZ-ALP-REC-05 — gerçek Python yardımcısı ve final rapor

REC-22 **DONE (kapalı rehberlik kapsamı)**; final gate **BLOCKED / NOT READY**.
Önceki kullanıcı commit'i `985b2388`; aşağıdaki mesaj yalnız yeni TASK 5 değişiklikleridir.
AGENTS.md'nin zorunlu `feature:` başlığı korunur. Gate engelleri feature başarısı diye yazılmaz.

```text
feature: LTZ-ALP-REC-05 güvenli Python yardımcısını ve final kalite raporunu ekle
- REC-22: POST /api/v1/recommendation/assistant ile GAME, HARDWARE ve LTZ_USAGE için assistant-v1/assistant-rules-v1 sözleşmeli, tr/en sabit şablonlu gerçek Python rehberliği sun.
- Kurallar: eksik girdiyi NEEDS_INPUT olarak belirt; donanımda owner assessment durumlarını açıkla, FPS veya tam uyumluluk garantisi verme; oyun seçimi rehberini gerçek katalog önerisi gibi sunma.
- Güvenlik: mevcut JWT/rol/rıza/ACTIVE/kotayı paylaş; Python'a kimlik/token/ham profil göndermeden sınırlı stdin JSON kullan, yanıtın sürüm/bağlam/şablon/step eşleşmesini doğrula.
- Process sınırları: sabit script dosyası, temiz child environment, iki eşzamanlı process, iki saniye deadline ve input/output limitleri uygula; timeout/exit/bozuk JSON hatalarını kontrollü ProblemDetail ile kapat.
- Runtime: kullanıcı onaylı ADR 0003 ile mevcut non-root image'a Python minimal/stdlib ekle; portable Windows runtime checksum'unu doğrula, yeni ENV/pip/sidecar/migration ekleme.
- Testler: 80 Java ve 7 Python testini geçir; LINE %97,51, merkezi ek/değişen kod %97,70, Checkstyle/PMD yeni ihlal 0 ve JAR dependency gate PASS sonuçlarını kaydet.
- Dağıtım: dar secretsiz context ile image build'ini, JAR içindeki script hash'ini ve ağsız/read-only/non-root smoke testini doğrula; final image HIGH/CRITICAL taramasında 0 bulgu kaydet.
- Final gate: native pre-push/Sonar denemelerini gerçek ENV/uygulama DB korumasıyla çalıştırıp BLOCKED kaydet; doğrulanmamış Quality Gate veya A notu iddia etme.
- Belgeler: API, mimari, klasör yerleşimi, ADR/runtime yönergesi, beşinci checkpoint/test, master checklist, issues, güvenlik ve PR title/description taslaklarını güncelle; 14/23 kabul ve NOT READY kararını açık tut.
```

Beş taslak yukarıda ayrı tutuldu; kullanıcı ilk dört aşamayı kendisi commit etti. Beşinci
taslak çalışma ağacıdır. Gerçek stage/commit/push/PR/merge veya Git kimlik değişikliği yapılmadı.
