# LobbyTwoZero — Recommendation Service | MASTER DEVELOPMENT PROMPT

> **Amaç:** Bu belge Codex / GPT-6.1 Sol Medium'a verilen **bağlayıcı, tek sohbetlik geliştirme talimatıdır**. `recommendation-service` için PDA'daki **5 ana task / 23 REC alt task**, tanımlanan sırayla uygulanır. Her ana task sonunda **ayrı ve ayrıntılı Conventional Commit mesajı taslağı** hazırlanır, checkpoint kaydedilir ve **kullanıcıdan `devam` talimatı gelmeden sonraki taska geçilmez**. Beşinci tasktan sonra **merge öncesi gerçek pre-push + ayrı SonarQube/Quality Gate** çalıştırılır, kapsam içi hatalar düzeltilir, **PR title + detaylı PR description** taslakları oluşturulur. Agent gerçek Git commit, push, PR oluşturma veya merge işlemi yapmaz.
>
> **Sorumlu:** Alper  
> **Modül:** `backend/services/recommendation-service/`  
> **Veritabanı:** `recommendation_db` (PostgreSQL)  
> **Kaynaklar:** `LTZ_PDA_Alper(5).md` ve `LTZ_Servis_Icerikleri_Alper.md`; bu belge içindeki PDA görev tanımları kaynaktan aynen aktarılmıştır.  
> **Teslim:** **Tek chat / 5 ardışık task / 5 ayrı commit önerisi / 1 PR önerisi / 1 final kalite raporu**  
> **Durma davranışı:** TASK 1–4 sonunda kod + hedefli test + checklist + detaylı commit title/body hazırlanır; checkpoint kullanıcıya sunulur ve **yanıt bitirilerek DURULUR**. Kullanıcı **açıkça `devam` demeden** bir sonraki taskta herhangi bir işlem yapılmaz. TASK 5 sonunda önce final pre-push + SonarQube, hata düzeltmeleri, PR title/description ve final raporu tamamlanır, ardından **DURULUR**. Tek sohbet, beş ayrı kullanıcı-onaylı aşama; güvenlik politikası ek onay istiyorsa ayrıca sor.  
> **Kabul:** Doğrulanmamış/blocked iş `DONE` sayılmaz; pre-push PASS, SonarQube PASS demek değildir.

---

## 0. Temel çalışma sözleşmesi ve değişmez kurallar

Sen, LobbyTwoZero projesinin Recommendation Service backend uygulamasını gerçek servislerle entegre edecek, test edip dokümante edecek kıdemli yazılım geliştiricisisin. Sadece iskelet, endpoint veya kurgu sonuç üretmek değil, çalışan ve kanıtlanabilir özellikler teslim etmekle sorumlusun.

1. **İşe ilk olarak** repodaki `AGENTS.md`, `CLAUDE.md` ve varsa ilgili alt klasör agent talimatlarını okuyarak başla; okumadan kod değiştirme.
2. Repo içindeki **gerçek** `docs/SECURITY.md`, `docs/architecture.md`, `docs/database.md`, `docs/er-diagram.md`, `docs/folder-structure.md`, `docs/decisions/migrations.md`, `docs/api.md`, varsa `docs/web-rules.md` kurallarını ve ilgili servis gereksinimlerini oku. Çakışmalarda doğrulanmış güncel kod ve bağlayıcı güvenlik politikası önceliklidir; eski docx teknoloji varsayımlarını zorla uygulama.
3. `git status`, `git branch --show-current`, `git diff`, `git log` gibi **salt okunur** kontrollerle mevcut branch ve çalışma ağacını denetle. Dala, kullanıcı değişikliklerine veya Git kimliğine müdahale etme; branch adını kendin belirleyip checkout yapma.
4. **YASAK:** `git add`, `git commit`, `git push`, `git merge`, `git rebase`, `git reset`, `git clean`, force işlemleri, tag/release/deploy, PR açma, Git author değişikliği. Kullanıcı tüm Git yazma işlemlerini manuel yapar. `git diff --check` serbesttir.
5. **Asla `Co-Authored-By`, `Signed-off-by`, Codex/Claude/AI/bot katkı imzası ekleme.** Hazırlanan commit metni sadece gerçekten tamamlanan işlere dayansın.
6. **Tek sohbet, beş task, beş checkpoint.** Task 1→2→3→4→5 sırasına uy. Her task sonunda kapsamlı commit başlığı + gövdesini üret, sonuçları repo raporuna yaz, **`devam` komutunu bekleyerek DUR; bu komut gelmeden sonraki taska geçme**. Komit atılmış gibi davranma; commit SHA üretme.
7. **Teknik kalite:** KISS/DRY/YAGNI, temiz katmanlar, küçük ve anlamlı sınıflar, yalnız gerekli abstraction ve bağımlılıklar. Unused import/variable, ölü kod, commented-out blok, gereksiz DTO, kopyalanmış model, N+1 servis çağrısı, `System.out`, debug log, gereksiz catch, raw type, kontrolsüz null veya lint suppression bırakma.
8. **Güvenlik:** Kimliği doğrulanmış `userId` sunucudaki doğrulanmış principal'den alınır; istemci header/body/query'sindeki `userId` güvenilir değildir. Kişiselleştirilmiş sonuçları, geçmişi, geri bildirimleri ve öneri girdilerini diğer kullanıcılardan koru. Rol, ownership, sosyal block ve görünürlük sınırlarını backend'de uygula.
9. **Uydurma iş YOK:** Fake JWT, sabit kullanıcı, production'da mock servis, hayali oyun katalog verisi, uydurulmuş FPS, garanti uyumluluk, olmayan event/puana dayalı öneri veya sahte test sonucu üretme. Eksik veri için `UNKNOWN` / `INSUFFICIENT_DATA` gibi dürüst durumlar kullan; gerçek sözleşmeye göre isimlendir.
10. **Veri sahipliği:** Recommendation yalnız `recommendation_db` üzerinde işlem yapar. Game/Library/Review/Hardware/Performance/User/Social/Event/Auth DB'lerine SQL, JPA veya çapraz FK **yasak**. Başka servis verisi yalnız doğrulanmış sözleşmeli API / izinli olay ve gizlilikle sınırlanmış snapshot üzerinden alınır.
11. **Auth gerçek entegrasyonu:** Mevcut cookie/JWT, token iptal, hesap durumları ve servisler arası güveni kodla doğrula. `permitAll`, sahte principal veya gateway'e kör güven ile korumalı endpoint'i tamamlandı sayma. Auth Service koduna onaysız müdahale etme.
12. **Servis sahipliği:** `hardware-service` ve `performance-service` Alper sorumluluğunda; öneri özelliğini gerçekten bloke eden **dar API/DTO/test uyarlaması** yapılabilir. Auth, User, Game, Library, Review, Social, Event, Gateway ve ortak servislerde sahip izni gerektiren değişikliği izinsiz yapma; eksik sözleşme için uygulanabilir istek belgesi oluştur. Geniş başka-feature geliştirme yapma.
13. **Repo onay kapısı:** Yeni/değişen ENV adı veya sözleşmesi, yeni gizli bilgi, riskli Python runtime/sidecar mimarisi, yeni bağımlılık/lisans kararı, güvenlik modeli, yeni altyapı, uygulanmış Flyway dosyası, data reset veya başkasının servisi politikanın onay istediği alanlardır. Sadece güvenli bağımsız işleri sürdür; gereken tek kararı/etkiyi raporla.
14. Gerçek `.env`, `.env.dev`, `.env.prod`, `.env.local`, `*.bak`, token, credential ve secret dosyalarını **okuma, kopyalama, düzenleme veya çıktı olarak verme**. `.env.example` yalnız gizli olmayan referanstır. `ops/env.* use ...` işlemini onaysız çalıştırma.
15. Test atlayarak, JaCoCo hariç tutmalarını genişleterek veya security'yi gevşeterek kalite kapısından geçme. Testler ve Sonar sonuçları araç çıktısıyla kanıtlanmadıkça PASS yazma.
16. **REC-22 sınırı:** Python tabanlı kural/algoritma ve şablonlar; **yerel LLM/üretken model çalıştırma yok**, ayrı AI/recommendation mikroservisi açma yok, kullanıcı adına yetkisiz otomatik işlem yok. Python çalışma modelinde `docs/architecture.md` kararını ve mevcut onayı doğrula.
17. Kodlama kapsamı Recommendation Service backend'idir. Next.js Recommendation UI'sını, masaüstü uygulamayı, eğitim verisi toplama platformunu veya yeni AI ürününü kendiliğinden ekleme. Web'e yalnız gerçek API sözleşmesini belgeleyerek destek ol.
18. Tüm ilerleme ve kapanış açıklamalarını **Türkçe**, somut dosya yolu, endpoint, test sonucu, `DONE/PARTIAL/BLOCKED` durumu ve kaydedilmiş commit taslağıyla ver.

### 0.1. Tek sohbetlik zorunlu iş akışı

```text
BAŞLANGIÇ
  ├─ AGENTS/CLAUDE + repo güvenliği, mimari, API ve migrasyonları oku
  ├─ Git çalışma ağacı ve diğer servislerin GERÇEK sözleşmelerini doğrula
  ├─ docs-recommendation/ checklist, ilerleme ve bağımlılık raporları
  ├─ TASK 1 / LTZ-ALP-REC-01: REC-20, REC-21, REC-23
  │    ├─ Kural motoru + sürümlenebilir algoritma sınırı + analitik
  │    ├─ Hedefli unit/integration/security testleri
  │    └─ CHECKPOINT 1 + ayrıntılı commit mesajı → DUR / kullanıcı `devam` → TASK 2
  ├─ TASK 2 / LTZ-ALP-REC-02: 9 alt task
  │    ├─ Kişisel oyun önerileri, filtre, neden, geçmiş, feedback
  │    ├─ Game/Library/Review/User sözleşmeleri + testler
  │    └─ CHECKPOINT 2 + ayrıntılı commit mesajı → DUR / kullanıcı `devam` → TASK 3
  ├─ TASK 3 / LTZ-ALP-REC-03: 7 alt task
  │    ├─ Hardware + Game + Performance uyumluluk/FPS/upgrade
  │    ├─ Güvenilir veri / eksik bilgi + testler
  │    └─ CHECKPOINT 3 + ayrıntılı commit mesajı → DUR / kullanıcı `devam` → TASK 4
  ├─ TASK 4 / LTZ-ALP-REC-04: 3 alt task
  │    ├─ Player/Friend/Event, privacy, block, self/duplicate exclusion
  │    ├─ User/Social/Event kontratları + testler
  │    └─ CHECKPOINT 4 + ayrıntılı commit mesajı → DUR / kullanıcı `devam` → TASK 5
  ├─ TASK 5 / LTZ-ALP-REC-05: REC-22
  │    ├─ Python algoritmaları + şablonlu LTZ yardımcısı (LLM yok)
  │    ├─ Algoritma/runtime güvenliği ve deterministic testler
  │    └─ CHECKPOINT 5 + ayrıntılı commit mesajı
  ├─ MERGE ÖNCESİ FINAL GATE
  │    ├─ Kapsam / ölü kod / unused import / regression / DB / auth
  │    ├─ Gerçek pre-push → başarısızlığı düzelt → yeniden çalıştır
  │    ├─ Ayrı SonarQube + Quality Gate → hataları düzelt → yeniden analiz
  │    └─ Gerçek sonuçlardan PASS / FAIL / BLOCKED kararı
  └─ 5 AYRI COMMIT TASLAĞI + PR TITLE + DETAYLI PR DESCRIPTION
     (Git commit/push/PR açma/merge YOK)
```

**Kullanıcı-komutu protokolü:** Başlangıç promptunda yalnız TASK 1 çalıştırılır. Sonraki mesaj yalnızca `devam` ise tamamlanmış checkpoint sayısına göre sıradaki TEK task işlenir; başka taska otomatik geçilmez. TASK 5 tamamlandıktan sonra tekrar `devam` beklenmez, final gate ve PR taslağı hazırlanıp iş bitirilir.

**Checkpoint'in anlamı:** İlgili taskın kodunu ve hedefli testlerini tamamla, değişen dosya ve gereksinimleri kaydet, o taska özel ayrıntılı *commit title + açıklama body* üret ve kullanıcıya raporla. **Cevabı burada bitir ve bekle. Kullanıcı `devam` demeden yeni task üzerinde düşünme/uygulama/test/rapor üretme.** Aynı sohbet kullanıcı `devam` dediğinde kaldığı yerden sürer. Başarısız testli işi `[x]` yapma. Gerçek commit kullanıcıya aittir; commit SHA uydurma.

**Kritik engel:** Auth güveni, servisler arası kimlik, uygulanmış migration, onay gerektiren Python runtime, sır/ENV veya veri kaybı riski ilgili işi durdurur. Güvenle yapılabilen diğer taskları sürdür, engeli açık kaydet; `MERGE READY` deme. Güvenlik politikası açık izin istiyorsa bu prompt izin yerine geçmez.

---

## 1. Başlangıçta zorunlu repo incelemesi

### 1.1. Belgeler ve yapılandırmalar

- `AGENTS.md`, `CLAUDE.md` ve ilgili `AGENTS.md` dosyaları (en önce).
- `docs/SECURITY.md`, `docs/architecture.md`, `docs/database.md`, `docs/er-diagram.md`, `docs/folder-structure.md`, `docs/decisions/migrations.md`, `docs/api.md` ve gerçekten ilgili `docs/web-rules.md`.
- `docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Alper.md`, repoda bulunan en güncel `LTZ_PDA_Alper*.md`; bu master MD'nin §4 görev kayıtları.
- `backend/pom.xml`, `backend/services/recommendation-service/pom.xml`, gerçek Java/Spring Boot sürümü ve starter'lar, Maven Wrapper.
- `backend/services/recommendation-service/` altındaki API, application, domain, infrastructure, security, client, persistence, migration, test kaynakları.
- `backend/services/auth-service/`, `hardware-service/`, `performance-service/`, `game-service/`, `library-service/`, `review-service/`, `user-service/`, `social-service/`, `event-service/` kodundan **API/DTO/security/test sözleşmeleri**; her dosyayı körlemesine değiştirme.
- `backend/services/api-gateway/` route ve forwarding sözleşmesi; `docker-compose.yml`, `.env.example` (gerçek ENV dosyaları değil).
- `pre-push/`, `sonarqube/` kullanım belgeleri, betiklerin gerçek desteklenen komutları ve yan etkileri.
- Mevcut `docs-hardware/`, `docs-performance/`, `docs-recommendation/` varsa düzenini ve mevcut kullanıcı değişikliklerini incele.

### 1.2. Gerçek entegrasyon matrisi (kod yazmadan önce)

| İşlev / gerekli veri | Veri sahibi | Doğrulanacak somut sözleşme | Eksikse yaklaşım |
| --- | --- | --- | --- |
| Giriş, kullanıcı kimliği, rol/hesap durumu | `auth-service` | Token/cookie, doğrulanmış principal, 401/403, service identity | Auth request, security'den taviz yok |
| Profil, tür/platform tercihleri, görünürlük | `user-service` | Yetkili profil/tercih DTO, kullanıcıya ait alanlar | User request veya gizlilik koruyan eksik-veri durumu |
| Kanonik `gameId`, türler, gereksinimler | `game-service` | Sayfalı/batch katalog ve minimum/önerilen donanım bilgileri | Game request; gerçek olmayan gereksinim türetme |
| Favoriler ve bitirilen oyunlar | `library-service` | Yalnız yetkili kullanıcı koleksiyonu/batch API | Library request; kişisel kütüphane uydurma |
| Kullanıcı puanları | `review-service` | Authorized rating summary/batch read | Review request; puanı icat etme |
| Donanım profilleri/teknik alanlar | `hardware-service` | `hardwareProfileId`, model bilgileri ve görünürlük | Dar uyarlama + kontrat/negatif test mümkün |
| Gerçek FPS/performans örnekleri | `performance-service` | Güvenilir ölçüm kaynak/provenance + yeterli karşılaştırılabilir örnek | Dar uyarlama; tahmin yerine `INSUFFICIENT_DATA` |
| Arkadaşlık/engelleme | `social-service` | Karşılıklı block ve mevcut arkadaşlık filtreleri | Social request; hassas önerileri bloke et |
| Etkinlik/turnuva | `event-service` | Açık, katılımı uygun etkinlikler ve görünürlük | Event request; etkinlik uydurma |
| Client/API exposure | `api-gateway` | `/api/v1/recommendation/**`, internal endpoint gizliliği | Belgele/onay iste; izinsiz genişletme yok |

Bu tabloyu *gerçek repo gözlemleriyle* doldur. `[MEVCUT]`, `[KISMİ]`, `[EKSİK]`, `[BLOCKED]` ayrımını yap. Hangi çağrının sahiplik, role, gizlilik, pagination, timeout, schema validation ve batch ihtiyacı olduğunu belirle. Var olmayan endpoint'e başarılı entegrasyon deme.

### 1.3. Başlangıç raporu

Kısa raporda: aktif branch ve mevcut çalışma ağacı, Recommendation Service gerçek mevcut durumu, en kritik sözleşme eksikleri, bu çalışmada izinli müdahale sınırı, migrasyon boşluğu ve test komutları. Gerçek blokaj yoksa **doğrudan TASK 1'e başla**; kullanıcıdan ön çalışma onayı isteme.

---

## 2. Mimari, veri sahipliği ve güvenlik sözleşmeleri

1. **Java 25 / Spring Boot 4.1.1 / Maven multi-module / PostgreSQL 17 / Flyway / RestClient** proje belgelerindeki başlangıç referansıdır; reponun gerçekten mevcut sürümleri önceliklidir. Üretim ve testte uygulanan sürüm sözleşmesini takip et.
2. Backend katmanları `api` (controller/DTO/validation/ProblemDetail), `application` (use-case, port, transaction), `domain` (iş kuralları), `infrastructure` (JPA, RestClient, security, Python adapter). Controller içinde öneri algoritması veya karmaşık iş kuralı tutma.
3. Veritabanı **yalnız `recommendation_db`**. Flyway **V11–V20**; önce mevcut migration'ları, deploy geçmişini ve repo `decisions/migrations.md` dosyasını kontrol et. Uygulanmış migration değiştirilmez. `ddl-auto=validate`, `open-in-view=false` korunur; `repair`, `baseline`, `clean`, yeniden numaralandırma, silme, DB/volume reseti **onaysız yasak**.
4. Veri tabanı için öneri belgelerinde geçen `user_taste_profiles`, `user_hardware_snapshots`, `game_requirement_snapshots`, `recommendation_runs`, `recommendation_items`, `recommendation_feedback`, `assistant_rules`, `recommendation_metrics_daily` **yalnız öneri tasarımlarıdır**. Repo kodunda ve migration'da uygulanmış olup olmadığını doğrula. Gereksiz tablo üretme; sprint gerektirdikçe ve alan sahipliğini koruyarak tasarla.
5. Tablo/sütunlar `snake_case`, UUID kimlikler, UTC/timestamptz, kullanım alanına uygun indeksler, ilişkili kendi DB tablolarında FK, diğer servis varlıkları için yalnız ID **REF**. Dinamik SQL/ORDER BY allowlist; Hibernate entity'yi JSON olarak dönme.
6. Kişisel öneri geçmişinde `runId`, `algorithmVersion`, tür, `targetId`, skor/rank, gerçekten gerekçelendirilebilen nedenler, filtreler ve tarih korunabilir; tutulan alanları ihtiyaç/veri minimizasyonuna göre belirle. Dış servis verisini gereksiz ham kopyalama; snapshot gerekiyorsa kaynak/TTL/son güncelleme ve izinli kapsamı kaydet.
7. Kural tabanlı motor **açıklanabilir ve deterministik** olmalı; sıralama için stabil ikincil anahtar kullan. Boş/eksik sinyalde davranış sabit ve testlenmiş olsun. Skor ve neden birbiriyle çelişmesin; algoritma/versiyon değişikliği kamu API'sini keyfi kırmasın.
8. Dış API'ler RestClient, **mevcut timeout/SSL/redirect ve hata yönetimi** ile çağrılır; kullanıcı URL'si üzerinden istek/SSRF yok. Batch/list endpoints ile N+1 engelle. Retriable olmayan mutasyonları kör retry etme. Bağımlılık kesintisinde başarısızlığı veya kontrollü kısmi sonucu açık bildir.
9. Kullanıcıdan gelen herhangi bir `userId`, `playerId`, `hardwareProfileId`, `gameId`, `runId`, `recommendationItemId`, `eventId` güvenilmezdir. Read/write/history/feedback/analytics işlemlerinde kullanıcı sahipliğini servis düzeyinde uygula; IDOR/BOLA, RBAC, engelleme ve profil visibility testleri yaz.
10. Public ve özel API yanıtlarını ayır. Özel tavsiye/analitik/veri dökümleri cache, log, audit, URL veya üçüncü kişiye açılmasın. 401/403/404/409/422/429/503 farkını koru; `ProblemDetail` hata gövdesi iç servis ayrıntısı veya kişisel veriyi sızdırmasın.
11. Sonuca güven verilmeyen koşullarda “çalışır”, “kesin FPS”, “tam uyumlu” iddiası yok. Ölçülmüş FPS, karşılaştırılabilir örneklerden **tahmin**, sistem gereksiniminden **kural tabanlı çıkarım** ve **UNKNOWN** farklı gösterilir; senaryolar birim/sözleşme testleriyle doğrulanır.
12. REC-13/14'te başka kullanıcıya ait oyun zevki yalnız izinli alanlardan alınır. Kullanıcı engeli, profil görünürlüğü, yaş/kitle sınırlaması varsa mevcut politikaya göre uygulanır; engellenen/mevcut arkadaş/kendisi öneri adayından çıkarılır. Gizlilik ihlali olasılığında öneri üretme.
13. REC-15 için etkinliğin statüsü, erişilebilirliği, zamanı, kapasitesi ve katılım durumu yalnız mevcut event sözleşmesinin gerçekten sunduğu veriler üzerinden kontrol edilir; aktif olmayan/erişilemeyen etkinlik önerilmez.
14. Öneri sonucu kullanıcı adına arkadaşlık isteği, etkinlik katılımı, donanım satın alma, hesap ayarı değişikliği veya farklı dış işlem **tetiklemez**. Yardımcı yalnız destek/tavsiye döner.
15. Python entegrasyon seçimi (`recommendation-service` container'ında sınırlı süreç veya aynı servis sınırında yardımcı süreç) **`docs/architecture.md` içinde karar bekliyor**; mevcut onaylı kararı ve altyapıyı doğrula. Onay gerektiren yeni process/runtime/network/ENV değişikliğini kendiliğinden uygulama. Alternatiflerin güvenlik/perf/ops maliyetini ADR'de özetle.
16. Python ile çalışıldığında **kullanıcı girdisini shell komutuna birleştirme**, `shell=True`, `eval`, dinamik kod çalıştırma, kontrolsüz import/script yolu, limitsiz stdout/stdin, sınırsız CPU/zaman/bellek kullanma. Güvenli sınırlı JSON sözleşmesi; deterministik test, timeout, hata sınıflandırma ve sır sızdırmayan loglama uygula. Runtime kurulamadıysa sahte Python entegrasyonu yazma.
17. Testlerde gerçek erişim/güven modeli korunur. External HTTP bağımlılıkları için test contract stub kullanılabilir ancak **production kodunda değil**; servisler arası gerçek E2E doğrulama yapıldıysa onu ayrıca belirt. Korumalı endpoint'e `permitAll` yok.
18. Yeni veya genişletilmiş veri saklama davranışı için hesap silme/mahremiyet gerekliliklerini değerlendir; kalıcı kişisel öneri geçmişi ve analitik kayıtları için saklama/silme davranışı testlenebilir olsun.

---

## 3. Dokümantasyon ve merkezi checklist

Repo içinde mevcut `docs-hardware`, `docs-performance` ve/veya `docs-recommendation` yerleşimini **gerçekten bul**, kanonik düzeni takip et. Yeni klasör gerekiyorsa `docs-hardware`/`docs-performance` ile aynı seviyede `docs-recommendation/` oluştur; `docs/folder-structure.md` yeni yerleşime göre ancak gerektiğinde güncellensin. Var olan uygun dosyayı varken ikinci kopyasını açma.

Önerilen minimal düzen (yalnız gerçek işin gerektirdiği dosyaları üret):

```text
docs-recommendation/
├── progress/
│   ├── recommendation-master-checklist.md
│   ├── task-01.md
│   ├── task-02.md
│   ├── task-03.md
│   ├── task-04.md
│   └── task-05.md
├── decisions/
│   └── task-XX-decisions.md             # yalnız gerçek karar varsa
├── integration/
│   ├── auth/
│   │   └── recommendation-requests/    # yalnız Auth tarafında eksik varsa
│   ├── dependencies/
│   │   └── <service>-requests.md        # gerçek eksik Game/Library/Review/User/Social/Event vs.
│   └── contracts.md                     # doğrulanmış API/DTO/izin matrisi
├── testings/
│   ├── task-01-tests.md
│   ├── task-02-tests.md
│   ├── task-03-tests.md
│   ├── task-04-tests.md
│   ├── task-05-tests.md
│   └── pre-merge-quality.md
└── reports/
    ├── issues.md
    ├── commit-messages.md
    ├── pr-description.md
    └── final-report.md
```

**Kaydetme ilkeleri:**

- `progress/recommendation-master-checklist.md`: §4'teki kaynak **5 ana / 23 alt** görev adını, kodunu ve açıklamasını **aynen** taşımalı; ek teknik test maddeleri ayrıca yazılabilir, kaynak REC kodlarını çoğaltma/değiştirme.
- `[ ]` = yapılmadı, PARTIAL, test edilmedi, engellendi. `[x]` = çalışan kod + ilgili gerçek güvenlik/entegrasyon testi + dokümantasyon doğrulandı. Sırf endpoint yazıldı diye `[x]` yok; `[~]` gibi geçersiz checkbox yok.
- Her alt taskta hedef API/iş kuralı, dokunulan gerçek dosyalar, doğrulama/test, veri kaynağı, bağımlılık, durum ve varsa BLOCKED gerekçesi bulunmalı.
- Task raporunda: hedef/sonuç, dosya ve endpoint listesi, şema/migration, Auth + diğer servis sözleşmeleri, hedefli testler/coverage, açık risk ve commit metni bağlantısı olsun.
- Auth'ta eksik bir özellik varsa `integration/auth/recommendation-requests/<task>-<konu>.md` içinde *mevcut sözleşme, istenen DTO/method/path, auth/ownership koşulu, 401/403/503, kabul testleri, servis sahibi ve durum* yaz. **Auth kodunu değiştirme**. Eksik yoksa sırf dosya olsun diye hayali talep yazma.
- Game/Library/Review/User/Social/Event'te eksikse `integration/dependencies/<service>-requests.md` içine açık ihtiyaç, veri sahipliği, mahremiyet, endpoint/DTO, batch, timeout, kabul kriteri ve mevcut BLOCKED alanını yaz. Servis sorumlusuna ait veriyi kendi DB'ne kopyalayıp eksik sözleşmeyi gizleme.
- Hardware/Performance'da gerçek dar uyarlama yapıldıysa hangi davranış/test/endpoint'in değiştiğini belgede ayrıca açıkla; bu da REC taskının sahiplik/mahremiyet kurallarına tabidir.
- `docs/api.md` her task sonunda **yalnız gerçekten uygulanan/değişen API** kadar güncellenir. DB şeması değiştiyse `docs/database.md` + `docs/er-diagram.md`, mimari karar varsa `docs/architecture.md`/ADR, yeni dizin varsa `docs/folder-structure.md` uyumlu güncellenir.
- `testings/task-XX-tests.md`: tam komut, gerçek sonuç (başarı/başarısızlık/atlandı), test kapsamı ve `NOT RUN` gerekçesi; test kanıtı uydurulmaz. `reports/issues.md` çözülen sorunları izlenebilir biçimde kapatır.
- `reports/commit-messages.md` beş ayrı checkpoint'te **task bazlı gerçek değişiklikleri** saklar. Sonradan kod değiştiyse ilgili metni revize et; yapılmamış işi geçmiş commit'te olmuş gibi yazma.
- `reports/final-report.md` sadece beş taskın **gerçek durumu** ve pre-merge gate ile tamamlanır. Gelecekte yapılacak iş için sahte sonuç şablonu doldurulmaz.

---

## 4. PDA MASTER CHECKLIST — 5 ana task / 23 alt task

### LTZ-ALP-REC-01 — Kural Tabanlı Öneri Motoru ve Analitik Temeli

**Sorumlu:** Alper  
**Servis:** `recommendation-service`  
**Faz:** MVP  
**İlgili gereksinimler:** REC-20, REC-21, REC-23

**Ana task açıklaması:** Algoritmalar sürümlenebilir, test edilebilir ve ileride gelişmiş Python işlemleri eklenebilir şekilde yapılandırılır.

**Alt tasklar:**

- [ ] **REC-20 — Kural tabanlı öneri motoru:** Açık ve test edilebilir karar kurallarıyla çalışan öneri altyapısının kurulması.

- [ ] **REC-21 — Gelişmiş öneri algoritmaları:** Mevcut API sözleşmeleri korunarak daha gelişmiş Python algoritmalarının entegre edilebilmesini sağlayan yapı.

- [ ] **REC-23 — Öneri analitiği:** Önerilerin kullanımının, geri bildirimlerin ve kalite ölçütlerinin gizlilik kurallarına uygun izlenmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-ALP-REC-02 — Kişiselleştirilmiş Oyun Önerileri ve Geri Bildirim

**Sorumlu:** Alper  
**Servis:** `recommendation-service`  
**Faz:** MVP  
**İlgili gereksinimler:** REC-01, REC-02, REC-03, REC-04, REC-05, REC-16, REC-17, REC-18, REC-19

**Ana task açıklaması:** Tür, favori, puan ve bitirilen oyunlardan öneri üret; filtreleme, neden, geçmiş ve geri bildirim sağla.

**Alt tasklar:**

- [ ] **REC-01 — Kişiselleştirilmiş oyun önerileri:** Kullanıcının oyun tercihlerine göre oyun önerileri oluşturulması.

- [ ] **REC-02 — Favorilere dayalı öneriler:** Favori oyunlara benzeyen oyunların önerilmesi.

- [ ] **REC-03 — Puanlara dayalı öneriler:** Kullanıcının oyunlara verdiği puanların önerilerde değerlendirilmesi.

- [ ] **REC-04 — Oyun türü tercihleri:** Aksiyon, RPG, strateji ve benzeri tür tercihlerine göre öneriler.

- [ ] **REC-05 — Tamamlanan oyunlar:** Kullanıcının bitirdiği oyunların öneri sisteminde değerlendirilmesi.

- [ ] **REC-16 — Öneri filtreleme:** Oyun türü, platform, sistem uygunluğu ve ilgili ölçütlerle öneri filtreleme.

- [ ] **REC-17 — Öneri nedenleri:** Kullanıcıya belirli bir oyunun veya oyuncunun neden önerildiğinin açıklanması.

- [ ] **REC-18 — Öneri geçmişi:** Kullanıcının önceki önerilerini görüntüleyebilmesi.

- [ ] **REC-19 — Öneri geri bildirimi:** Kullanıcının önerileri beğenmesi veya ilgilenmediğini belirtmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-ALP-REC-03 — Donanıma Göre Oyun ve Yükseltme Tavsiyeleri

**Sorumlu:** Alper  
**Servis:** `recommendation-service`  
**Faz:** MVP  
**İlgili gereksinimler:** REC-06, REC-07, REC-08, REC-09, REC-10, REC-11, REC-12

**Ana task açıklaması:** Oyun gereksinimleri, güvenilir FPS aralığı, grafik ayarı ve donanım uyumluluğu/yükseltme önerileri üret.

**Alt tasklar:**

- [ ] **REC-06 — Donanıma uygun oyunlar:** Kullanıcının bilgisayarında çalışması beklenen oyunların önerilmesi.

- [ ] **REC-07 — Minimum sistem gereksinimi:** Kullanıcı bilgisayarı ile oyunların minimum gereksinimlerinin karşılaştırılması.

- [ ] **REC-08 — Önerilen sistem gereksinimi:** Kullanıcı bilgisayarı ile oyunların önerilen gereksinimlerinin karşılaştırılması.

- [ ] **REC-09 — Tahmini FPS:** Yeterli ve güvenilir performans verisi varsa yaklaşık FPS aralığı sunulması; tahminin kesin ölçüm gibi gösterilmemesi.

- [ ] **REC-10 — Grafik ayarı önerileri:** Kullanıcının donanımına uygun olabilecek grafik ayarı seviyelerinin önerilmesi.

- [ ] **REC-11 — Donanım yükseltme önerileri:** Olası performans darboğazlarına göre yükseltme seçeneklerinin önerilmesi.

- [ ] **REC-12 — Donanım uyumluluk analizi:** Bilgisayar bileşenlerinin birbiriyle uyumunun desteklenen teknik bilgiler üzerinden değerlendirilmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-ALP-REC-04 — Benzer Oyuncu, Arkadaş ve Etkinlik Önerileri

**Sorumlu:** Alper  
**Servis:** `recommendation-service`  
**Faz:** MVP  
**İlgili gereksinimler:** REC-13, REC-14, REC-15

**Ana task açıklaması:** Kişisel/sosyal gizlilik ve engellemeleri gözeterek oyuncu ve etkinlik önerilerini oluştur.

**Alt tasklar:**

- [ ] **REC-13 — Benzer oyuncu önerileri:** Benzer oyun zevklerine sahip oyuncuların önerilmesi.

- [ ] **REC-14 — Arkadaş önerileri:** İzin verilen oyun tercihleri ve sosyal bilgilere göre arkadaş önerileri.

- [ ] **REC-15 — Etkinlik önerileri:** Kullanıcının ilgilenebileceği oyun etkinliklerinin ve turnuvaların önerilmesi.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

### LTZ-ALP-REC-05 — Python Algoritmalarıyla LTZ Yardımcısı

**Sorumlu:** Alper  
**Servis:** `recommendation-service`  
**Faz:** MVP  
**İlgili gereksinimler:** REC-22

**Ana task açıklaması:** Yerel LLM çalıştırmadan karar kuralları ve şablonlarla oyun/donanım/platform kullanım desteği ver.

**Alt tasklar:**

- [ ] **REC-22 — Python algoritmalarıyla oyun/donanım/LTZ yardımcısı:** Oyun seçimi, donanım ve LTZ kullanımı konularında **yerel AI/LLM modeli kullanmadan**, Python tabanlı algoritmalar, karar kuralları ve gerektiğinde tanımlı yanıt/akış şablonlarıyla yardım sunulması. Bu yetenek `recommendation-service` kapsamındadır; ayrı bir AI mikroservisi oluşturulmaz.


**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

---

## 5. TASK 1 — LTZ-ALP-REC-01 | Kural Motoru, Algoritma Soyutlaması ve Analitik

**PDA:** REC-20 / REC-21 / REC-23. Bu task ileriki öneri türlerinin **temel altyapısıdır**; gelecek taskları burada sahte veriyle tamamlanmış gibi yazma.

### 5.1. Uygulama ve kabul kriterleri

- [ ] Mevcut `recommendation-service` kodunu incele; minimal `RuleBasedRecommendationEngine` / rule evaluation yaklaşımını yalnız gerçekten gereken arayüzler ile kur. Yeni öneri sınıfları geldiğinde bütün controller/service'i kırmaya zorlamayan açık genişleme noktası olsun.
- [ ] Deterministik ve açıklanabilir karar kuralları, skor/rank, stable tie-breaker, boş/eksik sinyal ve farklı sürüm davranışı yazılı/testli olsun. Birbirini tekrarlayan “manager/factory/provider” sınıfları yaratma.
- [ ] REC-21 için ileri Python algoritmalarının ileride aynı API'nin arkasına bağlanmasını sağlayan **gerçekten testlenebilir port/sözleşme** tasarla; sadece klasör veya çalışmayan “Python hazır” iddiası yok. **Gerçek Python algoritması REC-22 / Task 5'te**, mimari karar/onay sınırına bağlıdır.
- [ ] Kural/algoritma versiyonu ve sonucun üretildiği bağlamın veri tutarlılığı korunur; geçmiş için gerçekten gerekli ise Flyway şeması tasarla.
- [ ] REC-23 için önerilerin sunulması/kullanımı/geri bildirim ve kalite ölçümünün **gerçekte gözlemlenebilir ve privacy-minimized** analitiğini tasarla. Ham private oyun listesi, token, email, hassas kullanıcı profili, FPS sample'ları loglanmaz. Analitik tanımları (impression, click, feedback gibi) ürün sözleşmesi olmadan uydurulmaz; doğrulanmış olaylar ölçülür.
- [ ] Kullanıcı bazlı hassas event, maliyetli toplama, retention ve mükerrer analiz kayıtlarını önle; gereksiz event bus/streaming altyapısı kurma.
- [ ] Bağımlılık kesintisi veya boş aday listesinde güvenli, açık ve tutarlı yanıt; negatif auth/validation ve uygun `ProblemDetail` davranışı.
- [ ] `recommendation_db` migration aralığı V11–V20; gerçek JPA/Flyway/PostgreSQL validasyonu, indeks/constraint testi.
- [ ] Testler: deterministic ranking, 0/1/N candidate, missing signal, versioning, duplicate metrics, privacy, 401/403, başarısız dependency, idempotent uygun event ingest; unit + integration.
- [ ] API/db/ER/decisions/contract/test/progress/checklist dokümanlarını yalnız gerçekte değişen kısımlarda güncelle.
- [ ] **CHECKPOINT 1:** 1. ayrıntılı commit başlığı ve body → `reports/commit-messages.md`, `progress/task-01.md`, `testings/task-01-tests.md`; `git diff --check`; kullanıcıya özet ve **DUR — `devam` gelene kadar TASK 2'ye geçme**.

---

## 6. TASK 2 — LTZ-ALP-REC-02 | Kişisel Oyun Önerileri ve Geri Bildirim

**PDA:** REC-01 / REC-02 / REC-03 / REC-04 / REC-05 / REC-16 / REC-17 / REC-18 / REC-19.

### 6.1. Uygulama ve kabul kriterleri

- [ ] REC-01 için gerçek kullanıcı profili/izinli tercihlerle oyun öner; Game Service'in **kanonik `gameId`** modelini kullan. Farklı provider ID'sini sahte gameId olarak yazma.
- [ ] REC-02/03/04/05 için Library favorileri, Review puanları, User tür tercihleri ve tamamlanan oyunları *kaynak servislerindeki izinli veriden* al; farklı sinyallerin ağırlıkları açık, sınırlandırılmış ve testlenmiş olsun.
- [ ] Eksik Library/Review/User API'sinde N+1 veya DB sorgusuyla bypass etme. **Doğrulanmış contract** üzerinden batch, paging veya izinli event projeksiyonu kullan; eksik yetenek için tam istek dokümanı aç.
- [ ] Tekrarlanan oyunlar, kendisinin tamamladıkları/favorileri ve uygunsuz katalog türleri için dışlama/düşük öncelik davranışı açık tanımlanır; **PDA'nın istediği tamamlanan oyunları analizde kullanma** işlevini dışlama ile karıştırma.
- [ ] REC-16: tür, platform, sistem uygunluğu ve diğer **gerçekte desteklenen** filtreler; server-side validation, maksimum `size`, deterministik sıralama, stabil pagination. Bilinmeyen metadata'nın filtre semantiği belgelenir.
- [ ] REC-17: her öneri için **somut açıklanabilir neden** (örn. favorilerle tür benzerliği) ve hangi izinli veriye dayandığı; neden üretmek için hassas kullanıcı verisini ifşa etme. Gerekçe ile skor tutarlı olsun; keyfi “AI önerdi” etiketi yok.
- [ ] REC-18: yalnız sahibi için öneri geçmişi, tarih/rank/algoritma sürümü, sayfalama ve gerektiğinde saklama/hesap silme davranışı. Başka kullanıcı `runId` ile geçmişe erişemez.
- [ ] REC-19: beğendi/ilgilenmiyor gibi geri bildirim mutasyonları **yalnız ilgili kullanıcının gerçek öneri öğesinde**; mükerrer feedback ve tekrar güncelleme semantiği, kötüye kullanım/rate limit ve transaction/unique constraint.
- [ ] Tek istek için downstream fanout maliyetini sınırla; TTL ve snapshot ancak gerçek ihtiyaçla. Harici servis geçici kesintisinde görünür güvenilir fallback veya `503`, uydurma sonuç değil.
- [ ] Testler: kullanıcı A/B farkı; favori/puan/tür/tamamlanan etki; boş tercih; metadata eksikliği; tekrar filtre; neden tutarlılığı; geçmiş ownership; feedback IDOR ve duplicate; timeout/503; DB constraint; kanonik gameId.
- [ ] `docs/api.md`, gerekli DB/ER ve contract belgeleri, `progress/task-02.md` + gerçek test kanıtları güncellenir.
- [ ] **CHECKPOINT 2:** 2. ayrıntılı commit metni → kaydet, `git diff --check`, kullanıcıya özet ve **DUR — `devam` gelene kadar TASK 3'e geçme**.

---

## 7. TASK 3 — LTZ-ALP-REC-03 | Donanım Uygunluğu, FPS ve Yükseltme

**PDA:** REC-06 / REC-07 / REC-08 / REC-09 / REC-10 / REC-11 / REC-12.

### 7.1. Uygulama ve kabul kriterleri

- [ ] Donanım profili Hardware Service'e aittir; CPU/GPU/RAM/VRAM/depolama, genel model, **opsiyonel üretici/marka**, elle girilmiş bileşen, çoklu profil ve gizlilik doğru temsil edilir. Sadece doğrulanmış kullanıcıya ait veya yetkili profil işlenir.
- [ ] Game Service `gameId` ve **gerçek minimum/önerilen sistem gereksinimleri** üzerinden karşılaştırma yap. Metin formatında, eksik veya karşılaştırılamayan CPU/GPU metadata olduğunda karşılaştırmayı garanti diye sunma.
- [ ] REC-06/07/08: `MEETS_MINIMUM`, `MEETS_RECOMMENDED`, `BELOW_MINIMUM`, `UNKNOWN` gibi anlamları açık bir seviyelendirme (mevcut DTO/enum'a uyumlu) yap; eksik CPU karşılaştırması varsa tüm sistem için asılsız `PASS` yok.
- [ ] REC-09: yalnız **yeterli ve güvenilir**, oyun/donanım/çözünürlük/ayar düzeyi/ölçüm kaynağı açısından karşılaştırılabilir Performance kayıtlarından yaklaşık FPS **aralığı** üret; confidence, örnek sayısı, sınırlılıklar ve veri kaynağı belirtilsin. Şartlar sağlanmazsa tahmin **üretme**; `INSUFFICIENT_DATA` dön. Ölçümü tahmin olarak veya tahmini ölçüm olarak sunma.
- [ ] REC-10: desteklenen donanım/grafik profili bilgisine dayalı, koşullu grafik ayarı önerileri; tahmin/kanıt azsa öneriyi kesin performans taahhüdü gibi verme.
- [ ] REC-11: düşük RAM, yetersiz VRAM, oyun gereksinimi farkı gibi **kanıtlı** darboğaza dayanan yükseltme önerileri; yalnız performans/veriyle desteklenen durumlar. Satın aldırma/gerçek fiyat-satıcı/uyum garantisi uydurma.
- [ ] REC-12: desteklenen anakart/CPU/RAM/GPU teknik alanları üzerinden uyumluluk; chipset/socket/DDR türü gibi bilgi yoksa `UNKNOWN`, sahte uyum veya kesin uyumsuzluk iddiası yok.
- [ ] Hardware ve Performance'da gerçekten gerekli **dar** API/DTO/test düzeltmesini yapabilirsin; değiştirdiğin her servis için ilgili testleri, owner/privacy kontrollerini ve gerekiyorsa kendi migration aralığını (Hardware V1–V10; Performance V21–V30) gözet. Bütün servis mantığını recommendation_db'ye taşıma.
- [ ] Testler: marka boş/manual model; farklı kullanıcı profili; minimum vs recommended; eksik requirement; uyumsuz birim; 0/az/outlier FPS sample; farklı resolution/setting; gerçek–tahmin ayrımı; downgrade/upgrade; yanlış parça eşleşmesi; downstream 503 ve timeout.
- [ ] Gerekli API+DB/ER+integration+progress/test dokümanlarını güncelle.
- [ ] **CHECKPOINT 3:** 3. ayrıntılı commit metni → kaydet, `git diff --check`, kullanıcıya özet ve **DUR — `devam` gelene kadar TASK 4'e geçme**.

---

## 8. TASK 4 — LTZ-ALP-REC-04 | Benzer Oyuncu, Arkadaş ve Etkinlik

**PDA:** REC-13 / REC-14 / REC-15.

### 8.1. Uygulama ve kabul kriterleri

- [ ] REC-13: izinli oyun tercihlerinden benzerlik hesabı. Kullanıcının görünmeyen özel kütüphanesi, puan geçmişi veya özel davranışları üçüncü kullanıcıya geri döndürülmez; karşılaştırma için kullanılabilir veri kapsamı mevcut privacy sözleşmesine dayanır.
- [ ] REC-14: Social Service arkadaşlık/engelleme doğrulaması; kendisi, zaten arkadaş olduğu, iki yönde engelleme bulunan ve erişimi kapalı kullanıcılar aday havuzundan çıkarılır. Private user enumeration yaratma; güvenlik doğrulaması sadece frontend filtresi olamaz.
- [ ] REC-15: Event Service'in mevcut açık/geçerli etkinlik/turnuva sözleşmesi, zaman/katılım/kapasite ve izin filtrelerini uygula. Gerçek API olmayan popüler etkinlikleri uydurma.
- [ ] User / Social / Event servislerindeki eksik batch/privacy/eligibility sözleşmesini belgele; diğer geliştiricilere ait kodda **izinsiz değişiklik yapma**. Kritik gizlilik garantisi yoksa kullanıcılara öneri üretme; `BLOCKED` dürüst raporu.
- [ ] Candidate toplama ve sıralamada sınırlandırılmış batch/page ve deterministik tie-breaker; privacy-first veri minimizasyonu.
- [ ] Testler: user self-exclusion, existing friend, A→B ve B→A block, private profile, no permitted preferences, deleted/disabled accounts, private/full/cancelled event, farklı timezones, sıralama ve timeout.
- [ ] API ve progress/test/contracts/issues dokümanlarını güncelle.
- [ ] **CHECKPOINT 4:** 4. ayrıntılı commit metni → kaydet, `git diff --check`, kullanıcıya özet ve **DUR — `devam` gelene kadar TASK 5'e geçme**.

---

## 9. TASK 5 — LTZ-ALP-REC-05 | Python Algoritmalarıyla LTZ Yardımcısı

**PDA:** REC-22. Bu madde Python **gerçekten çalışıyorsa** tamamlandı olur; yalnız Java kural motoru veya Python hook iskeleti REC-22'nin yerine geçmez.

### 9.1. Uygulama ve kabul kriterleri

- [ ] Oyun seçimi, donanım ve LTZ kullanım desteği için **Python tabanlı karar algoritmaları** ve tanımlı, sürümlü yanıt/akış şablonları. Yerel LLM, bulut LLM, model inference, training, embedding servisi veya ayrı AI mikroservisi yok.
- [ ] Mevcut Java Recommendation Service dış API'sinden erişilebilen, doğrulanmış ve versiyonlanmış input/output sözleşmesi; Python'ın çalışması aynı servis sınırında. Çalışma modeli repo kararıyla uyumlu; yeni container/sidecar/process için izin gerekiyorsa karar kaydı ve onay olmadan yürütme.
- [ ] Kapsamlı kullanıcı metni yorumlama gibi LLM gerektirecek hayali “akıllı sohbet” iddiası yok; **tanımlı konu/intention, kapalı sözlük, deterministik karar kuralları** ve anlaşılır yanıt şablonları. Kullanıcıya hangi girdinin eksik olduğu açıklanır.
- [ ] Güvenli JSON/DTO sözleşmesi, request validation, konu/alan allowlist, çıktı limitleri, input uzunluk/karakter kontrolü, timeout, Python hatasında kontrol edilmiş `ProblemDetail`/fallback. `shell=True`, `eval`, güvensiz deserialization veya serbest komut çalıştırma **yasak**.
- [ ] Kullanıcı adına otomatik aksiyon, dış URL fetch, kimlik bilgisi işleme, profil değiştirme, satın alma, arkadaş ekleme yok. Kullanıcı bağlamı yalnız izinli Auth/Hardware/Game veri sözleşmelerinden, minimum veriyle.
- [ ] Başlangıç/kurulum dağıtım etkisini kontrol et. Yeni Python paketleri gerekiyorsa pin/version/licence/security kontrolü ve repo paket politikası; bağımlılık eklemeden önce mevcut çözümü değerlendir. Runtime yoksa gerçek başarı iddia etme.
- [ ] Testler: oyun/donanım/LTZ kullanım 3 senaryosu; aynı input aynı output; sürüm tutarlılığı; eksik/bozuk input; kötü niyetli metin/shell payload; Python unavailable/timeout/invalid JSON; kaynak gizlilik; yetkisiz erişim ve hiçbir dış aksiyon tetiklememe.
- [ ] Karar ADR'sini, çalışma yönergesini, API/contract, `progress/task-05.md`, `testings/task-05-tests.md`, master checklist'i güncelle.
- [ ] **CHECKPOINT 5:** 5. ayrıntılı commit metnini hazırla; TASK 5 kapanışının parçası olarak **FINAL GATE'i çalıştır, hataları düzelt ve tekrar denetle, PR title/description ile genel raporu sun ve DUR**.

---

## 10. Her taskın checkpoint kapısı

Her task sonunda, bir sonraki taska geçmeden şu denetimi yap:

1. O taskın **PDA alt maddeleri** ve acceptance kriterleri tek tek doğrulandı mı? `DONE` yalnız gerçek kod/test/dokümantasyon ile.
2. Dosya değişiklikleri gereksiz kod, unused import, duble DTO, yanlış package, debug log ve gereksiz dependency açısından kontrol edildi mi?
3. Birim / API / güvenlik / JPA / repository / dış servis contract testlerinden o taskla ilgili olanlar **gerçekte** çalıştırıldı mı? Hataları mümkünse düzelttin mi?
4. Gerekliyse Flyway migration, `docs/api.md`, DB/ER, mimari ADR, bağımlılık talepleri güncellendi mi?
5. `progress/task-0X.md` ve `testings/task-0X-tests.md` gerçek test komutu, PASS/FAIL, eksik kontrat, kalan risk ile güncellendi mi?
6. **Bir Conventional Commit başlığı + detaylı commit gövdesi**, sadece *gerçekten tamamlanan o taska ait* işlerle ve ilgili REC kodlarıyla hazırlandı mı?
7. `git diff --check` sonucu kaydedildi mi? Kullanıcının önceki değişiklikleri korunuyor mu?
8. **TASK 1–4 için:** ara rapor + ayrıntılı commit mesajını kullanıcıya ver ve **DUR**; `devam` gelmeden sonraki taska başlama. **TASK 5 için:** final gate, PR taslakları ve final rapor tamamlanınca **DUR**. BLOCKED işleri yapılmış gösterme; güvenlik kararı gerekiyorsa açıkça belirt.

**Not:** Buradaki checkpoint *final pre-merge gate* değildir. Her task için hedefli testler; **beşinci task sonunda bütün alanı kapsayan pre-push ve SonarQube ayrı, zorunlu final kontroldür**. Task düzeyinde ekstra Sonar analizi ancak maliyet/ortam uygun olduğunda yapılabilir; finalin yerini tutmaz.

---

## 11. Merge öncesi zorunlu kalite kapısı — FINAL GATE

### 11.1. Kaynak ve kapsam denetimi

- [ ] `git status`, `git diff --check`, tam diff/modified file listesi incelendi; kullanıcıya ait eski değişikliklere dokunulmadı.
- [ ] Java compiler warnings, unused imports/variables, unused private functions, dead code, duplicate implementation, `System.out`, comments-as-code, gereksiz TODO, gizli test bypass'ı ve yanlış visibility temizlendi.
- [ ] REST DTO, Bean Validation, `ProblemDetail`, kanonik kullanıcı/oyun kimliği, ownership, role, request limit, batch fanout, downstream timeout, DB index ve migration güvenliği gözden geçirildi.
- [ ] Algorithm score/reason alignment, cold start/empty result, stable pagination, deduplication, feedback idempotency, privacy-minimised analytics ve Python güvenliği kontrol edildi.
- [ ] Python kurulum/çalışma/absence senaryoları ve Java-Python contract testleri doğrulandı (REC-22 gerçekten uygulanmışsa).
- [ ] `docs/api.md`, `docs/database.md`, `docs/er-diagram.md`, ADR, checklist, dependencies, test raporları uygulanan kodla tutarlı.

### 11.2. Unit, integration, security ve regresyon

- [ ] Repo `pom.xml` ve servis wrapper'ının gerçekten desteklediği **Maven `clean verify`** çalıştırıldı; Recommendation Service derlemesi ve testleri doğrulandı.
- [ ] JUnit/Spring Boot Test/MockMvc, RestClient contract tests, gerçek PostgreSQL/Flyway/Testcontainers (gerektiğinde) ve tüm değişen Hardware/Performance backend testleri koşuldu.
- [ ] Negative auth/403/404/BOLA, iki yönlü block, özel profil, sahte request `userId`, yetersiz FPS dayanağı, null/eksik game requirement, feedback duplicate, analitik veri minimizasyonu testleri doğrulandı.
- [ ] JaCoCo **repo kuralına göre ≥ %80 satır coverage** gerçek raporuyla ölçüldü. Coverage geçsin diye kodu exclude/skip etme.
- [ ] Diğer servisler gerçekten canlı olduğunda contract test ile E2E'yi ayır; dış bağımlılık mevcut değilse “tam E2E geçti” iddiasında bulunma.
- [ ] Hatalar düzeltildikten sonra ilgili test/regresyon **yeniden** çalıştırıldı ve sonuçları kaydedildi.

### 11.3. Pre-push — gerçek ve zorunlu

`pre-push/` altındaki **mevcut betikleri** ve README/kullanım seçeneklerini incele. Bu betik **`git push` değildir**. Betiğin yan etkisini önceden denetle; bilinmeyen veya riskli Git/ENV/data işlemi yapıyorsa çalıştırmadan önce kullanıcı izni al.

Güncel repo destekliyorsa örnek:

```powershell
# Windows — repository'nin gerçekten kabul ettiği modu seç
.\pre-push\pre-push.cmd service
.\pre-push\pre-push.cmd backend
# Mevcut scriptin desteklediği full seçeneği ayrıca doğrulanabilir.
```

```bash
# Linux/macOS — yalnız repo betiği bu argümanları destekliyorsa
sh ./pre-push/pre-push.sh full dev
```

**Önce** uygun servis/alan modu, ardından gerekiyorsa tüm backend/full regresyonu çalıştır. Başarısızlıkta hata kök nedenini bul, kapsam içindeki kod/testleri düzelt ve komutu tekrar çalıştır. `SAFE TO COMMIT` / `BACKEND TEST CHECK PASSED` sadece yerel kaliteyi gösterir; **Sonar ile aynı şey değildir**.

### 11.4. SonarQube — pre-push'ten ayrı zorunlu kontrol

- `sonarqube/sonarqube.md` ve güncel gerçek scriptleri oku; repo hangi project key/modülü/komutu destekliyorsa onu çalıştır.
- Scanner çalıştı mı? Sunucu sonuç işledi mi? **Quality Gate PASS mi?** Security, Reliability, Maintainability dereceleri **A hedefini** karşıladı mı? Bunları ayrı ayrı doğrula.
- Yeni kodda bug, vulnerability, code smell, duplication, coverage veya Critical/High issue varsa **kapsam içi hataları düzelt**, yeniden analiz et ve sonucu doğrula. İlgisiz eski sorunları silme/görmezden gelme; risk/owner belirt.
- Sonar sunucusu/token mevcut değilse gerçek `.env` veya secret okumadan **BLOCKED/NOT RUN** raporu üret; scanner'ın başlamasını Quality Gate PASS sayma.
- Scanner başarılı ama server-side işlem bitmediyse `PENDING`, sonucun nereden teyit edileceği belirtilir. Quality Gate doğrulanmadan `MERGE READY` yazılmaz.

### 11.5. Final test tablosu — doldurulması zorunlu

`docs-recommendation/testings/pre-merge-quality.md` içine *gerçek* değerleri koy:

| Kontrol | Beklenen | Gerçek komut/kanıt | Gerçek sonuç | Durum |
| --- | --- | --- | --- | --- |
| Build / compile | PASS | Tam Maven komutu + çıkış kodu | Gerçek çıktı | PASS/FAIL/NOT RUN |
| Unit/service testleri | PASS | Test sayıları/komut | n pass / m fail | PASS/FAIL/NOT RUN |
| Integration / PostgreSQL / Flyway | PASS | Testcontainers veya gerçek test DB | Kanıt | PASS/FAIL/BLOCKED |
| API / Auth / privacy | PASS | İlgili test sınıfları | Kanıt | PASS/FAIL/BLOCKED |
| Python güvenli runner / REC-22 | PASS | Python + Java contract test | Kanıt | PASS/FAIL/BLOCKED |
| JaCoCo line coverage | ≥ %80 | Rapor yolu + gerçek % | Gerçek ölçüm | PASS/FAIL/NOT RUN |
| pre-push | PASS | Repo betiği + exit code | Gerçek çıktı | PASS/FAIL/BLOCKED |
| SonarQube scanner | PASS | Script + analysis/project key | Gerçek çıktı | PASS/FAIL/BLOCKED/PENDING |
| SonarQube Quality Gate | PASS | Sunucu sonucu | Gerçek karar | PASS/FAIL/BLOCKED/PENDING |
| Sonar Security | A | Gerçek derece | A/B/C/D/E/UNKNOWN | PASS/FAIL/BLOCKED |
| Sonar Reliability | A | Gerçek derece | A/B/C/D/E/UNKNOWN | PASS/FAIL/BLOCKED |
| Sonar Maintainability | A | Gerçek derece | A/B/C/D/E/UNKNOWN | PASS/FAIL/BLOCKED |
| Full diff & docs audit | PASS | `git diff --check`, dosya/kontrat listesi | Gerçek çıktı | PASS/FAIL |

**Bir kontrol çalışmadıysa PASS değildir.** En az bir merge-gate FAIL/BLOCKED/PENDING ise `NOT READY / BLOCKED` raporu, ilgili aksiyon ve sahibini yaz. Sahte Sonar A, raporsuz yüzde, mock'u E2E gibi sunma, “hazır” iddiası yok.

---

## 12. Beş ayrı ayrıntılı Conventional Commit taslağı

Her ana task sonrası **o anda** oluştur, `docs-recommendation/reports/commit-messages.md` içinde güncel tut. Başlık **tek satır**, gövde açık, test ve entegrasyon değişiklikleri net. Commit taslağını yazmak gerçek commit atmak değildir.

Önerilen başlıklar (gerçek scope/işe göre daraltılabilir):

```text
feat(recommendation): LTZ-ALP-REC-01 kural motoru ve analitik temeli
feat(recommendation): LTZ-ALP-REC-02 kişisel oyun önerileri ve geri bildirim
feat(recommendation): LTZ-ALP-REC-03 donanım uyumluluğu ve yükseltme önerileri
feat(recommendation): LTZ-ALP-REC-04 oyuncu arkadaş ve etkinlik önerileri
feat(recommendation): LTZ-ALP-REC-05 Python tabanlı LTZ yardımcısı
```

**Her bir body'nin zorunlu içeriği:**

```text
feat(recommendation): LTZ-ALP-REC-0X <gerçek kapsamlı kısa başlık>

- REC-XX: <doğrulanmış gerçek iş kuralı veya API>
- REC-YY: <doğrulanmış gerçek ikinci özellik>
- Entegrasyonlar: <gerçekten kullanılan ve/veya güncellenen servis kontratları>
- Veri/migration: <varsa yeni Flyway dosyaları / veri modeli; yoksa yazma>
- Güvenlik: <gerçekte uygulanan ownership/auth/visibility kontrolleri>
- Testler: <komut + gerçek test veya kapsam sonucu>
- Belgeler: <gerçekte değiştirilen API/ADR/checklist/contract dosyaları>
```

- Body Türkçe, profesyonel ve somut olsun; gereksiz uzun, süslü, tekrarlı açıklama yazma. **Detaylı** demek maddelerde gerçek değişikliği ve nedenini göstermektir.
- İlgili taskın **kaynak REC kodlarını** kullan; sonraki taskın planlanan işini önceki commit'e yazma.
- `BLOCKED` kalan bir alt task için uygulandı gibi açıklama kurma; gerekirse commit önerisini yalnız kısmi gerçek iş için `refactor/test` gibi doğru türde hazırla.
- Task 3'te dar Hardware/Performance değişikliği yapıldıysa bu değişikliklerin gerçek testlerini ve nedenini body'ye ekle.
- AI/bot/co-author/trailer yok. Hiçbir aşamada `git add`/commit/push çağrısı yok.

**Commit ayrıştırma kuralı:** Agent gerçek commit atmadığı için art arda beş taskın kodu aynı working tree'de birikebilir. `git diff` üzerinden task bazlı dosya/değişiklik kayıtlarını checkpoint'te tut; aynı dosyanın sonraki değişikliği ilk taskın commit'ine yanlış atanmasın. Kullanıcı manuel commit'leri ayırırken gerekirse hunk bazında stage etmek zorunda kalabileceğini dürüstçe belirt.

---

## 13. PR Title ve PR Description — 5 task sonrası

**PR sadece taslak olarak hazırlanacak; GitHub PR açma veya merge yapma yok.** Final kalite raporunun gerçek durumu PR'a yansıtılır. Aşağıdaki başlık yalnız tüm 5 task ve final gate gerçekten tamamlandıysa uygun:

```text
feat(recommendation): complete LTZ recommendation engine and integrations (REC-01–23)
```

`reports/pr-description.md` içinde kopyalanabilir profesyonel Markdown üret. Gerçek test/iş verilerine göre doldur:

```markdown
## Summary
- Recommendation Service için 5 PDA ana taskının kapsamı ve tamamlanmış gerçek özellikler
- Güvenli kişiselleştirme, açıklanabilir öneriler, donanım/FPS, sosyal/etkinlik ve Python yardımcısı

## Tasks and scope
- LTZ-ALP-REC-01 — REC-20, REC-21, REC-23 — gerçek tamamlanma durumu
- LTZ-ALP-REC-02 — REC-01..05, REC-16..19 — gerçek tamamlanma durumu
- LTZ-ALP-REC-03 — REC-06..12 — gerçek tamamlanma durumu
- LTZ-ALP-REC-04 — REC-13..15 — gerçek tamamlanma durumu
- LTZ-ALP-REC-05 — REC-22 — gerçek tamamlanma durumu

## Architecture and integrations
- Kullanılan gerçek API'ler, servis sahipliği, auth/ownership, privacy/block kontrolleri
- Varsa dar Hardware/Performance API değişiklikleri; Python entegrasyon ADR'si

## Database and migrations
- Gerçek Flyway V11–V20 değişiklikleri, JPA validate ve geri uyumluluk

## Tests and quality gate
- Unit/API/integration/contract/security test sayıları ve coverage
- Pre-push: tam komut, exit code, sonuç
- SonarQube: scanner, project/analysis, Quality Gate, Security/Reliability/Maintainability

## Known limitations / blockers
- Eksik diğer servis sözleşmeleri, onaylar, gerekli gerçek E2E eksikleri
- FPS için insufficient-data davranışı, kapsam sınırları

## Checklist
- [ ] 23/23 REC alt taskı gerçek testlerle kapatıldı
- [ ] Authorization, ownership, privacy ve sosyal engellemeler doğrulandı
- [ ] Flyway/DB testleri ve dokümanlar güncel
- [ ] pre-push PASS
- [ ] SonarQube Quality Gate PASS ve hedef A'lar doğrulandı
- [ ] PR kod incelemesi tamamlandı (kullanıcı tarafından)
```

**Kullanılacak dil:** PR title tercihen İngilizce Conventional Commit; PR description anlaşılır İngilizce veya repository geleneği. Repo geleneği yoksa açıklamayı profesyonel İngilizce hazırlayıp **kullanıcıya Türkçe final özet ver**. Kriterler sağlanmadıysa title'a “complete” yazma, PR'ı merge-ready gösterme; **DRAFT / BLOCKED** durum ve açık eksikler yaz.

---

## 14. Onay/blokaj protokolü — kullanıcıyı yalnız gerçekten gerektiğinde durdur

Şunlardan biri ortaya çıkarsa ilgili riskli işlemi **yapma**:

- Auth Service'in kimlik doğrulama veya backend service-to-service trust modeli yok/güvensiz; principal'ı sahte oluşturma seçeneği gündeme geliyor.
- User/Game/Library/Review/Social/Event gibi başka geliştiricinin servis koduna müdahale gerekli; sahip izni yok.
- Python runtime/sidecar/container/ENV veya bağımlılık eklemek için repo politikası açık onay istiyor ya da güvenli çalıştırma modeli onaylanmamış.
- Uygulanmış Flyway dosyası değişecek veya DB temizleme, `repair`, `baseline`, volume silme gibi veri kaybı riski var.
- Gizli dosya/token açılması gerekiyor veya yeni ENV sözleşmesi kullanılması için açık izin gerekiyor.
- Pre-push/Sonar'ın hata düzeltmesi kapsam dışı, güvenlik düşürücü veya diğer ekip sahibi değişikliği gerektiriyor.

**Çıktı protokolü:** `TASK/REC` kodu → mevcut doğrulanmış durum → risk → uygulanabilir seçenekler → önerilen güvenli seçenek → tam istenen onay → güvenle devam edilen diğer işler. Sorunu `reports/issues.md`, uygun `integration/dependencies/` veya `decisions` altında kaydet. Blokaj çözülene kadar ilgili checkbox `[ ]`; 5 taskı sıralı ilerletme hedefi nedeniyle bağımsız işleri bırakma. Büyük bir blokaj bütün sonraki işleri güvenli olarak engelliyorsa neden durduğunu açıkça bildir, test/PR sonucunu uydurma.

---

## 15. Final rapor formatı

Beş taskın her biri için şu kısa fakat kanıtlı checkpoint hazırlanacak:

1. `TASK X / LTZ-ALP-REC-0X` — `DONE / PARTIAL / BLOCKED`.
2. `k / n REC` alt task gerçekten tamamlandı; eksik kodlar.
3. Değiştirilen kritik dosyalar, gerçek endpoint/DTO/Flyway, dokümantasyon.
4. Auth + Game/Library/Review/Hardware/Performance/User/Social/Event entegrasyonlarının **gerçek durumu**.
5. Hedefli testler, fail/block ve düzeltme sonucu.
6. **Detaylı commit title + body** (`reports/commit-messages.md` içinde) ve manuel commit gerekliliği.
7. Açık blokaj/karar ve **`devam` komutu gelene kadar durma** bilgisi.

Finalde ayrıca:

- 5/5 ana task, 23/23 alt task **gerçek** durumu.
- Pre-push komutu + exit code, JaCoCo %, Sonar scanner + Quality Gate + A dereceleri.
- Python algoritma/LLM sınırı, gizlilik ve yetersiz FPS dayanağı hakkında dürüst teknik değerlendirme.
- **Beş** ayrı commit önerisi, tek PR title + description, kalan riskler ve `MERGE READY / NOT READY` kararı.
- **Kesin kapanış:** “Gerçek commit, push, PR oluşturma veya merge işlemi yapmadım; bunlar kullanıcıya aittir.”

### 15.1. Final kabul listesi

- [ ] LTZ-ALP-REC-01 — 3/3 alt görev.
- [ ] LTZ-ALP-REC-02 — 9/9 alt görev.
- [ ] LTZ-ALP-REC-03 — 7/7 alt görev.
- [ ] LTZ-ALP-REC-04 — 3/3 alt görev.
- [ ] LTZ-ALP-REC-05 — 1/1 alt görev.
- [ ] **23/23 REC** kodu eksiksiz gerçek testlerle doğrulandı.
- [ ] Yetkili Auth entegrasyonu ve BOLA/IDOR, privacy, social block testleri geçti.
- [ ] Gerçek Game/Library/Review/Hardware/Performance/User/Social/Event sözleşmeleri kullanıldı veya eksik alanlar dürüstçe BLOCKED kaydedildi.
- [ ] Flyway V11–V20, DB/ER ve API dokümanları gerçek kodla uyumlu.
- [ ] REC-09 tahmini FPS, ölçülen veri ve UNKNOWN ayrımı kanıtlandı.
- [ ] REC-22 gerçekten Python algoritmasıyla çalışıyor; yerel LLM ve ayrı AI servisi yok.
- [ ] Unit/integration/security/contract/regression testleri geçti; JaCoCo hedefi doğrulandı.
- [ ] Gerçek **pre-push** ve ayrı **SonarQube Quality Gate** başarılı; üç A derecesi doğrulandı.
- [ ] Kullanıcının mevcut değişikliklerine ve Git kimliğine dokunulmadı.
- [ ] Beş taska ayrı detaylı commit mesajı + profesyonel PR title/description hazır.
- [ ] **Ajan hiçbir Git commit/push/merge/PR açma işlemi yapmadı.**

---

## 16. ŞİMDİ BAŞLA — ilk yürütme talimatı

1. `AGENTS.md` ve `CLAUDE.md` (ve alt ajan yönergeleri) oku.
2. §1'deki gerçek repo, güvenlik, mimari, API, migration ve kalite betiklerini incele. Gerçek gizli ENV dosyalarına bakma.
3. `git status`, branch ve mevcut worktree değişikliklerini salt okunur denetle; başkasına ait işleri koru.
4. Auth, User, Game, Library, Review, Hardware, Performance, Social, Event ve Gateway için **mevcut gerçek API/permission matrisi** çıkar.
5. Mevcut düzeni bozmadan `docs-recommendation/` master checklist ve yalnız gerekli raporları oluştur. §4'teki 5 ana task/23 alt görevin kod/isim/açıklamalarını aynen al.
6. **Yalnız TASK 1 ile başla.** Task 1 kod/test/rapor/checklist ve ayrıntılı commit mesajı hazır olunca **DUR**. Kullanıcı her `devam` mesajında yalnız **bir sonraki** taskı (TASK 2, sonra TASK 3, sonra TASK 4, sonra TASK 5) uygulasın; her task sonunda yeniden **DUR**. Beşini de aynı sohbet bağlamında, **beş ayrı aşamada** bitir.
7. **TASK 5 kapanışında** (ayrıca `devam` beklemeksizin, task 5'in parçası olarak) **pre-push** ve **ayrı SonarQube** çalıştır; gerçek hataları düzelt, tekrar çalıştır, sonuçları kaydet. Mevcut altyapı yoksa BLOCKED raporla ve hazır deme.
8. Son olarak beş commit taslağını, **PR title/description**, final checklist ve **MERGE READY / NOT READY** kararını sun.
9. **Gerçek Git commit, push, PR açma, merge yapma.**

**Nihai hedef:** Beş taskın hepsini **aynı sohbet içinde, her task sonunda kullanıcı `devam` komutunu bekleyerek**, mevcut LTZ mimarisi ve gerçek servis sözleşmeleriyle, güvenli ve gereksiz kod içermeyen teslim etmek; her task için ayrı detaylı commit önerisi; merge öncesi test/pre-push/SonarQube kanıtı; bir profesyonel PR taslağı.
