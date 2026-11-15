# Commit taslakları

Bu ajan gerçek commit/stage/push yapmadı. TASK 1, TASK 2 ve TASK 3 için ayrı taslaklar vardır.
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
