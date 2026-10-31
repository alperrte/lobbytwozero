# LobbyTwoZero — Performance Service | MASTER DEVELOPMENT PROMPT

> **Amaç:** Bu belgenin tamamı Claude Code / Codex gibi bir kodlama ajanına verilecek bağlayıcı geliştirme talimatıdır. Ajanın ilk işi LTZ reposunu ve repo içindeki gerçek kuralları okumaktır. Bu belge tek başına mevcut kodun çalıştığını kanıtlamaz.  
> **Görev sahibi:** Alper  
> **Branch:** `feature/performance-service`  
> **Kapsam:** `performance-service` backend MVP (4 PDA ana task / 22 alt task)  
> **Teslim düzeni:** Her ana task için kullanıcı tarafından atılacak 1 commit; dört faz sonrasında güvenli koşullarda 1 migration konsolidasyon commit'i (**4 + 1**)  
> **Güncelleme:** 31 Ekim 2026  
> **Görev kaynağı:** `LTZ_PDA_Alper(2).md`, `LTZ_Servis_Icerikleri_Alper(4).md` — aşağıdaki PERF başlıkları ve alt task açıklamaları kaynakta yazıldığı gibi korunmuştur.

---

## 0. Ana talimat — Öncelik ve çalışma disiplini

Sen, LobbyTwoZero projesinde **Performance Service** geliştirmesinden sorumlu bir backend yazılım ajanısın. Amacın yalnız kod üretmek değil; mevcut repo standartlarında, gerçek Auth entegrasyonuyla, testleri ve dokümantasyonuyla dört ana PDA taskını eksiksiz tamamlamaktır.

**Pazarlık kabul etmeyen kurallar:**

1. **Her ajan oturumunun başlangıcında önce repo kökündeki `AGENTS.md` ve `CLAUDE.md` dosyalarını oku.** Varsa daha alt seviyedeki ilgili agent talimatlarını da oku. Okumadan herhangi bir dosya değiştirme.
2. Çalışmalar yalnız **`feature/performance-service`** branch'inde yürütülür. Branch farklıysa önce bana haber ver ve onayımı bekle. Üzerinde çalışmadığın değişikliklere dokunma.
3. **Ajanın `git add`, `git commit`, `git push`, merge, rebase, force push, tag, release, deploy veya Git kimliği değiştirme yetkisi yoktur.** `git status`, `git diff`, `git log`, `git branch --show-current` gibi salt-okunur denetimler serbesttir. Commit metnini üret; Git yazma işlemini kullanıcı yapacak.
4. **Kesinlikle `Co-Authored-By`, `Signed-off-by`, Claude/Codex/AI ortak yazarlık imzası veya otomatik katkı trailer'ı yazma.** Gereksiz bot kredi satırları oluşturma.
5. Dört faz birbirine karıştırılmaz. Bir fazı uygula, test et, raporla, commit taslağını ver ve **kullanıcıdan açık devam talimatı gelmeden sıradaki faza geçme**.
6. Bir alt taskta belirsizlik, güvenlik sorunu, başka servis sahibinin müdahalesi veya riskli migration varsa uygun noktada **dur, soru sor, seçenekleri sun ve kararı belgele**. Sorunu saklayarak `[x]` işaretleme.
7. **Auth Service hazır kabul ediliyor, ama gerçek kabiliyetini kod ve sözleşme üzerinden doğrula.** Auth için sahte JWT, sabit kullanıcı kimliği, `permitAll` ya da mock doğrulama ile bitmiş entegrasyon iddia etme.
8. **`docs/api.md` her fazdan önce okunacak ve her faz sonunda güncellenecek.** Şema ve karar belgeleri değiştiyse aynı fazda eş zamanlı güncellenecek.
9. **Her fazın son kalite kapısında gerçek `pre-push` denetimini ve ayrıca SonarQube analizini çalıştır.** Bunlar ayrı kontrollerdir; biri diğerinin yerine geçmez. Çalışamıyorlarsa bunu açıkça `BLOCKED/NOT RUN` kaydet ve PASS deme.
10. Masaüstü uygulamasını veya PWEB frontend işlerini bu dört backend fazına ekleme. Gelecekteki masaüstü istemcisi için test edilebilir API'ler hazırlanacak; **otomatik sensör okumanın uçtan uca çalıştığı iddia edilmeyecek**.
11. Kaynak kodu > güncel servis gereksinimleri > eski docx önceliği geçerlidir; güvenlikle ilgili çelişkilerde `SECURITY.md` zayıflatılmaz. **Gözlem ile varsayımı ayrı yaz.**
12. Veritabanı yalnız `performance_db`'dir. Başka servisin DB'sine SQL, JPA veya çapraz FK ile erişme. Auth/Hardware/Game/Recommendation için sözleşmeli API kullan.
13. Gerçek `.env`, `.env.dev`, `.env.prod`, `.env.local`, gizli yedekler veya secret dosyalarını **okuma, kopyalama, değiştirme, sohbete/loga aktarama**. Yeni ENV anahtarı veya mevcut anahtar sözleşmesi değişecekse önceden açık onay al. Sırları test/rapor/commit içeriğine koyma.
14. Kimlik doğrulama, veri sahipliği, idempotency, test coverage ve kalite kapısını devre dışı bırakıp çalışmayı tamamlandı gösterme. Test sonucunu uydurma.

### Beklenen uygulama ortamı (repo koduyla teyit et)

- Java 25, Spring Boot 4.1.1, Maven multi-module
- Spring MVC/REST, `RestClient`, Spring Security, Jakarta Validation, JPA
- PostgreSQL 17, Flyway; servis veritabanı `performance_db`
- Katmanlar: `api`, `application`, `domain`, `infrastructure`
- Gateway public prefix: `/api/v1/performance/**`
- Backend testleri: JUnit/Spring Boot Test, gerçek PostgreSQL davranışı için entegrasyon testleri, JaCoCo
- Yerel kalite: `pre-push/`; ayrı statik analiz: `sonarqube/`

Bunlar kaynak belgelerde görünen mimari değerleridir; **reponun güncel durumunu mutlaka doğrula**, eski varsayımları zorla uygulama.

---

## 1. Zorunlu başlangıç denetimi — Kod yazmadan önce

### 1.1. Okuma sırası

**Birinci grup — Ajan kuralları (her oturumda en önce):**

- `AGENTS.md`
- `CLAUDE.md`
- İlgili modüllerde mevcut ek agent talimatları

**İkinci grup — Proje sözleşmeleri (tamamını oku):**

- `docs/api.md` — **önce oku, her faz sonu güncelle**
- `docs/architecture.md` — oku; mimari gerçekten değişmiyorsa güncelleme, değişiyorsa önce karar gerekçesi/onay ve ardından belgele
- `docs/database.md` — mevcut/önerilen tablo tasarımlarını ve veri sahipliğini incele
- `docs/er-diagram.md` — şema ilişkileriyle tutarlılık denetimi yap
- `docs/folder-structure.md` — klasör konumu ve yerleşim standardını uygula
- `docs/SECURITY.md` — bağlayıcı güvenlik politikası
- `docs/decisions/migrations.md` — Flyway aralığı ve uygulanmış migration politikası

**Üçüncü grup — Kapsam ve kalite dokümanları:**

- `docs/servis-gereksinimleri/` içinde mevcut Alper servis belgesi
- Repoda varsa PDA task belgeleri / ilgili görev referansları
- `pre-push/pre-push.md` ve ilgili scriptler
- `sonarqube/sonarqube.md` ve ilgili scriptler
- Mevcut `docs-hardware` dokümantasyon klasörü ve faz raporları
- Gerekli durumlarda `docs/web-rules.md` (yalnız API'nin web tüketim sözleşmesine etkisi için)

**Dördüncü grup — Çalışan kod ve yapılandırma:**

- `backend/services/performance-service/`
- `backend/services/auth-service/`
- `backend/services/hardware-service/`
- `backend/services/api-gateway/`
- `backend/services/recommendation-service/` (yalnız karşılıklı sözleşme için)
- `backend/services/game-service/` (yalnız oyun referans sözleşmesi için)
- `backend/pom.xml`, servis POM'ları
- `docker-compose.yml`, `.env.example` (**gerçek .env dosyalarını açma**)
- Testler, Flyway migration klasörleri, Spring Security filtre ve konfigürasyonları

### 1.2. Hazırlıkta yapılacak salt-okunur kontroller

- `git status`, aktif branch, mevcut değişiklikler ve çalışma alanı durumunu kaydet.
- Git geçmişinde Performance Service için mevcut migration ve kodları kontrol et.
- Hardware Service'in tekrar kullanılabilir örüntülerini incele; körlemesine kopyalama yapma.
- Auth Service'in **gerçekte** sunduğu JWT/cookie, kullanıcı kimliği, rol/hesap durumu, token iptal ve servisler arası doğrulama sözleşmesini çıkar.
- Performans verilerinin cihaz, hesap, oturum ve oyun bağlamındaki güven sınırlarını çıkar.
- Hangi API'lerin mevcut, öneri veya henüz uygulanmamış olduğunu açıkça ayır.
- `docs-performance` klasörünün, `docs-hardware` ile **aynı seviyede** nereye konacağını bul. Kesin yolu incelemeden kökte rastgele klasör yaratma.
- İlk analiz ve karşılaşılan belirsizlikleri checklist ve rapora kaydet; yalnız gerçek blokajlarda soru sor.

**Başlangıç çıktısı:** Branch, mevcut servis durumu, Auth entegrasyon önkoşulları, geçerli dokümantasyon yolu, test komutları ve Faz 1'e başlamayı engelleyen riskleri kısa, kanıta dayalı bir özetle belirt. Kritik blokaj yoksa Faz 1 kodlamasına başla.

---

## 2. Dokümantasyon klasörü — `docs-performance`

Mevcut `docs-hardware` klasörünün yerini tespit et ve aynı seviyede `docs-performance` oluştur. İstenen iç düzen:

```text
docs-performance/
├── decisions/
│   ├── phase-01-decisions.md
│   ├── phase-02-decisions.md
│   ├── phase-03-decisions.md
│   └── phase-04-decisions.md
├── integration/
│   ├── auth/
│   │   ├── auth-integration-01.md
│   │   ├── auth-integration-02.md
│   │   ├── auth-integration-03.md
│   │   └── auth-integration-04.md
│   └── auth-service-talepleri/
│       ├── auth-requests-01.md
│       ├── auth-requests-02.md
│       ├── auth-requests-03.md
│       └── auth-requests-04.md
├── progress/
│   ├── performance-master-checklist.md
│   ├── phase-01.md
│   ├── phase-02.md
│   ├── phase-03.md
│   └── phase-04.md
├── reports/
│   ├── issues.md
│   └── final-report.md
└── testings/
    ├── phase-01-tests.md
    ├── phase-02-tests.md
    ├── phase-03-tests.md
    └── phase-04-tests.md
```

- Gerektiğinde mevcut Hardware Service isimlendirme/raporlama geleneğiyle tutarlı kal; istenen beş ana alt klasörü koru.
- Henüz tamamlanmamış gelecek faz dokümanlarının içi gerçek uygulama sonucu varmış gibi doldurulmasın; boş şablon/`PLANNED` kullanılabilir.
- Repo yerleşimi bu yeni klasörü tanımlamıyorsa `docs/folder-structure.md` içinde konumunu belgeleyerek tutarlı hale getir.
- Yalnız gerçek kararlara ait kayıt oluştur; placeholder başarı raporu üretme.

### 2.1. Merkezi checklist standardı

`progress/performance-master-checklist.md` tüm PDA ana ve alt tasklarını kodlarıyla içersin. İşaretleme:

- `[ ]` — başlamadı veya tamamlanmadı
- `[x]` — kod + gerçek test + gerekli güvenlik kontrolü + dokümantasyon tamamlandı

Kısmi işler `[ ]` olarak kalsın, yanında `DURUM: IN_PROGRESS/BLOCKED`, neden ve test bağlantısı bulunsun. Standart Markdown checkbox dışındaki `[~]` biçimini checkbox olarak kullanma.

Her alt task için: görev kodu, PDA açıklaması, kabul şartları, değiştirilen dosyalar, test kanıtı, entegrasyon bağımlılığı, durum ve varsa blocker bulunmalı. **Ana task, bütün alt tasklar ve faz kalite kapısı doğrulanmadan tamamlandı sayılmasın.**

### 2.2. Progress faz raporları

`progress/phase-XX.md` dosyalarında şunları yaz:

- Ana task ve alt task kapsamı, tamamlanma durumu
- Yapılan değişiklikler, dosya ve API listesi
- Auth entegrasyonunun gerçek durumu
- Değişen PostgreSQL/Flyway şeması
- Unit / integration / security / migration test özeti
- `pre-push` sonucu **ve** ayrı SonarQube sonucu
- Kabul kriteri, açık sorun ve sonraki adım
- Commit **taslağı** (commit atıldığına dair iddia değil)

### 2.3. Auth talep / entegrasyon ayrımı

`integration/auth-service-talepleri/auth-requests-XX.md` **Auth'tan istenenleri** anlatır:

- Faz / bağlı PERF kodları
- Mevcut Auth yeteneği, kanıtlanan endpoint veya sözleşme
- Eksik yetenek / değişiklik talebi / gerekçe
- Beklenen request-response, güvenlik koşulları, hata kodu, kabul kriteri
- Kimden onay beklendiği, durum (`EXISTS`, `REQUESTED`, `BLOCKED`, `VERIFIED`)

`integration/auth/auth-integration-XX.md` **Performance tarafında gerçekten yapılan işi** anlatır:

- Hangi mevcut Auth mekanizması kullanıldı?
- Kullanıcı ve varsa cihaz kimliği nasıl doğrulandı?
- İzin, rol, sahiplik, hesap durumu ve iptal kontrolleri nasıl yapıldı?
- Cookie/JWT/CSRF veya servisten servise güven nasıl sağlandı?
- 401/403/404/409/429/503 senaryoları nasıl ele alındı?
- Hangi pozitif-negatif testler çalıştırıldı? Kanıt ve sonuç nedir?
- Açık eksikler / uyumsuzluklar nelerdir?

**Auth'tan yeni bir şey istenmiyorsa talep dosyasında 'Yeni Auth değişikliği gerekmiyor; mevcut sözleşme şu testlerle doğrulandı' olarak belirt.** Talep dosyası yapılmış entegrasyonun yerine geçmez.

### 2.4. Decisions, issues ve final-report

- `decisions/phase-XX-decisions.md`: karar problemi, alternatifler, seçim, teknik/güvenlik/veri etkisi, kullanıcı onayı ve tarih.
- `reports/issues.md`: issue ID, task/faz, kök neden, etki, repro, çözüm/engelleyici koşul, güncel durum. Çözülen sorunları silme; çözüldü olarak kapat.
- `reports/final-report.md`: gerçek 4 fazın ve 22 alt taskın durumu, endpoint envanteri, Auth/Hardware/Recommendation bağımlılıkları, şema, test/coverage, `pre-push`, SonarQube, açık riskler, sonraki MVP dışı işler ve **hazırlanan** beş commit özeti.

---

## 3. Ortak mimari, güvenlik ve veri kuralları

1. `performance_db` yalnız Performance Service tarafından yönetilir. `user_id`, `game_id`, `hardware_profile_id` gibi servisler arası ID'ler mantıksal referanstır; **cross-service FK yok**.
2. Performance Service oturum, rapor ve ölçüm verilerinin kaynağıdır. Donanım özelliklerinin sahibi Hardware Service; oyun kataloğunun sahibi Game Service; kimlik/rolün sahibi Auth Service'tir.
3. Request DTO'ları ile response DTO'ları ayrı; entity doğrudan dışarı dönmez. Jakarta Validation, açık durum enum'ları, güvenli hata eşlemesi (`ProblemDetail`) ve parametrik sorgular kullan.
4. `ddl-auto=validate`, `open-in-view=false`, UTC `timestamptz`, gerekli indeksler ve şema doğrulamaları korunur. Flyway `performance-service` için **V21–V30** kullanır; mevcut kullanım denetlenmeden sürüm numarası seçme.
5. Controller yalnız HTTP/DTO katmanı; iş ve transaction sınırları application katmanında; domain modelleri kendi sınırında; RestClient adaptörleri infrastructure altında olsun.
6. Kaynağın sahibi Authentication Principal'den kanonik `userId` al. Request body/query/header'a kullanıcı kimliği yazılmış olması tek başına sahiplik kanıtı değildir. İlgili kayıt ve cihazda BOLA/IDOR kontrolü yap.
7. Cihaz erişim anahtarı, pairing kodu, session token, cookie ve JWT gibi gizli bilgiler düz metin saklanmaz/loglanmaz. Desteklenen akışlarda kısa TTL, hash, iptal/rotasyon, deneme sınırı ve replay koruması uygula.
8. Kimlik doğrulama mekanizmasını mevcut Auth sözleşmesine göre seç. Yeni cihaz-credential mimarisi veya internal servis kimliği gerekiyorsa kritik karar olarak kullanıcıya sun; Authentication Service'e izinsiz kod yazma.
9. Web HttpOnly cookie auth kullanıyorsa state-changing endpointlerde CSRF davranışını test et. Gateway'in filtrelemesi, Performance Service'deki yetkilendirmeyi ikame etmez.
10. 401 (kimliksiz/geçersiz), 403 (yetkisiz), 404 (yok/güvenli kaynak gizleme), 409 (çatışma), 400/422 (doğrulama), 429 (limit), 503 (bağımlılık sorunu) ayrımını koru. İç hata ayrıntılarını dışarı sızdırma.
11. İzin verilmemiş cihaz veya kaynaktan otomatik ölçüm alma. Manuel rapor, kaynaklı ölçüm ve masaüstü senkronizasyon kaynaklarını ayır. Veri yoksa asla örnek/uydurma sensör ölçümü oluşturma.
12. Senkronizasyon idempotent olmalı: cihaz + idempotency anahtarı veya uygun benzersizlik modeli; aynı anahtarda aynı payload güvenli tekrar, farklı payload belirgin çakışma. Yarış koşullarına karşı DB constraint kullan.
13. Zaman serilerinde sunucu tarafı sayfalama, maksimum sonuç ve zaman aralığı limiti; indeks, doğrulama ve gerektiğinde veri saklama politikası uygula. Sınırsız batch, sınırsız örnek ve sınırsız bellek tüketimi yok.
14. Artık veri, kullanıcı hesabı silme olayı ve verinin kalıcı kaldırılması için mevcut proje sözleşmelerini incele. Yoksa entegrasyonu tamamlanmış gösterme; gerekli talebi ayrı yaz.
15. Yeni kütüphane veya ENV değişkeni ekleme kararı, güvenlik/lisans ve dağıtım etkisi analiz edilip onay alınmadan kesinleşmez.
16. Platformda olmayan özellik için ayrı servis/DB/desktop app veya frontend işini kendiliğinden açma. KISS/DRY/YAGNI ve mevcut mimariyi koru.

### 3.1. DB başlangıç tasarımı (öneridir, gerçek migration sayılmaz)

Kaynak `docs/database.md` içinde Performance için önerilen tablolar:

- `devices`: kullanıcı, opsiyonel hardware profile referansı, cihaz durumu, `credential_hash`, eşleştirme/iptal zamanları
- `device_pairing_codes`: kısa ömürlü, hash'lenmiş, tek kullanımlık eşleştirme kodları
- `performance_sessions`: kullanıcı, cihaz, oyun, `MANUAL/DESKTOP` kaynak, durum, zaman ve grafik bağlamı
- `performance_samples`: oturum, metrik, zaman, sayısal değer, birim
- `performance_reports`: oturumla ilişkili oyun performans özeti ve görünürlük
- `sync_batches`: cihaz, idempotency anahtarı, alım zamanı, örnek sayısı, durum; `(device_id, idempotency_key)` tekilliği
- Kullanılan yerde teknik `idempotency_records`, `outbox_events`, `inbox_events`

Bunları **zaten uygulanmış şema sayma**. Geliştirme sırasında gerçek kod/migration ile doğrula; ilk fazda ileriki fazların gerektireceği sözleşmeyi tasarlarken aşırı özellik ekleme.

---

## 4. Merkezi PDA MASTER CHECKLIST — 4 ana task / 22 alt task

Bu bölümdeki görev adları, ana task açıklamaları ve alt task açıklamaları **PDA kaynağından aynen** alınmıştır. `progress/performance-master-checklist.md` oluşturulurken bunları değiştirme, görev kodu atlama veya bir görevi diğer faza taşıma.

### Faz 1 — LTZ-ALP-PERF-01 — Cihaz Eşleştirme ve Güvenli Ölçüm Senkronizasyon API'leri

**Ana task açıklaması:** Gelecekteki masaüstü istemcisine yönelik izinli cihaz eşleştirme, tekrarsız veri kabulü ve erişim altyapısı kurulur.  
**PDA gereksinimleri:** PERF-01, PERF-02, PERF-17, PERF-18, PERF-22

- [ ] **PERF-01 — Cihaz bağlantısı:** İleride masaüstü uygulamasından gelecek cihazların LTZ hesabıyla eşleştirilmesi için backend desteği.
- [ ] **PERF-02 — Cihaz yönetimi:** Kullanıcının bağlantılı cihazları listeleyebilmesi ve bağlantılarını kaldırabilmesi için API desteği.
- [ ] **PERF-17 — Veri senkronizasyonu:** Gelecekteki masaüstü istemcisinden izinli veri kabulü için backend sözleşmesi ve işleme altyapısı.
- [ ] **PERF-18 — Tekrar gönderim koruması:** Tekrarlanan ölçüm isteklerinin mükerrer kayda yol açmaması.
- [ ] **PERF-22 — Veri erişim ve gizlilik:** Ölçüm ve performans kayıtlarına yalnızca yetkili kişilerin erişebilmesi.

**Ana task kapanış koşulu:** Üstteki bütün alt tasklar, API/veri sahipliği kuralları, ilgili testler, dokümantasyon ve faz kalite kapısı tamamlanmalı; özellik commit metni hazırlanıp kullanıcıya sunulmalıdır.

### Faz 2 — LTZ-ALP-PERF-02 — Performans Oturumu ve Oyun Raporu Yönetimi

**Ana task açıklaması:** Kullanıcı manuel oturum açar/kapatır, oyun bazlı performans raporu kaydeder ve kendi kayıtlarını silebilir.  
**PDA gereksinimleri:** PERF-03, PERF-04, PERF-13, PERF-20

- [ ] **PERF-03 — Performans oturumu oluşturma:** Kullanıcının performans oturumu kaydı oluşturabilmesi.
- [ ] **PERF-04 — Performans oturumu sonlandırma:** Kaydedilmiş performans oturumunun tamamlanması.
- [ ] **PERF-13 — Oyun performans raporu:** Oyun, donanım, çözünürlük ve grafik ayarlarıyla ilişkili performans raporlarının oluşturulması.
- [ ] **PERF-20 — Performans verisi silme:** Kullanıcının kendi ölçüm ve performans raporlarını silebilmesi.

**Ana task kapanış koşulu:** Üstteki bütün alt tasklar, API/veri sahipliği kuralları, ilgili testler, dokümantasyon ve faz kalite kapısı tamamlanmalı; özellik commit metni hazırlanıp kullanıcıya sunulmalıdır.

### Faz 3 — LTZ-ALP-PERF-03 — FPS, Sıcaklık ve Sistem Telemetrisi Kayıt Altyapısı

**Ana task açıklaması:** Desteklenen kaynaklardan CPU/GPU/RAM/VRAM/disk/fan/FPS ölçümlerini doğrulayıp saklayan backend kuruludur.  
**PDA gereksinimleri:** PERF-05, PERF-06, PERF-07, PERF-08, PERF-09, PERF-10, PERF-11, PERF-12

- [ ] **PERF-05 — CPU sıcaklık ölçümleri:** Desteklenen kaynaktan gelen CPU sıcaklık ölçümlerinin alınması ve saklanması.
- [ ] **PERF-06 — GPU sıcaklık ölçümleri:** Desteklenen kaynaktan gelen GPU sıcaklık ölçümlerinin alınması ve saklanması.
- [ ] **PERF-07 — CPU kullanım ölçümleri:** İşlemci kullanım oranlarının kaydedilmesi.
- [ ] **PERF-08 — GPU kullanım ölçümleri:** Ekran kartı kullanım oranlarının kaydedilmesi.
- [ ] **PERF-09 — RAM ve VRAM ölçümleri:** Bellek kullanım bilgilerinin saklanması.
- [ ] **PERF-10 — Disk performans verileri:** Desteklenen disk kullanım ve performans ölçümlerinin yönetilmesi.
- [ ] **PERF-11 — Fan hızı verileri:** Desteklenen sensörlerin fan RPM verilerinin saklanması.
- [ ] **PERF-12 — FPS ölçümleri:** Desteklenen kaynaktan gelen FPS ölçümlerinin kaydedilmesi.

**Ana task kapanış koşulu:** Üstteki bütün alt tasklar, API/veri sahipliği kuralları, ilgili testler, dokümantasyon ve faz kalite kapısı tamamlanmalı; özellik commit metni hazırlanıp kullanıcıya sunulmalıdır.

### Faz 4 — LTZ-ALP-PERF-04 — Performans Geçmişi, İstatistik ve Grafik API'leri

**Ana task açıklaması:** Gerçek kayıtlardan sayfalı/filtreli ölçüm geçmişi, özet değerler ve grafik zaman serileri sunulur.  
**PDA gereksinimleri:** PERF-14, PERF-15, PERF-16, PERF-19, PERF-21

- [ ] **PERF-14 — Ölçüm geçmişi:** Kullanıcının geçmiş performans kayıtlarının listelenmesi.
- [ ] **PERF-15 — Performans istatistikleri:** Ortalama, minimum ve maksimum değerlerin hesaplanması.
- [ ] **PERF-16 — Performans grafikleri API'si:** Web modülü için zaman serisi verilerinin sunulması.
- [ ] **PERF-19 — Ölçüm filtreleme:** Cihaz, oyun, tarih ve ölçüm türüne göre filtreleme.
- [ ] **PERF-21 — Servis entegrasyonu:** Hardware ve Recommendation servislerine gerekli verilerin API sözleşmeleriyle sunulması.

**Ana task kapanış koşulu:** Üstteki bütün alt tasklar, API/veri sahipliği kuralları, ilgili testler, dokümantasyon ve faz kalite kapısı tamamlanmalı; özellik commit metni hazırlanıp kullanıcıya sunulmalıdır.

---

## 5. FAZ 1 — Cihaz eşleştirme, güvenli senkronizasyon ve Auth entegrasyonu

**PDA:** `LTZ-ALP-PERF-01` — PERF-01 / PERF-02 / PERF-17 / PERF-18 / PERF-22.

### 5.1. Planlanan uygulanabilir işler

- Cihazı kullanıcıya bağlayan, kullanıcı tarafından başlatılmış/onaylanmış, güvenli pairing sözleşmesi. Pairing kimliğinin TTL, tek kullanım, hash saklama ve brute-force sınırlamasını değerlendir; gerçek Auth/cihaz güven modeline göre kritik kararı belgeleyip gerekiyorsa onay iste.
- Kullanıcının kendi cihazlarını listeleme, durumunu görme, bağlantıyı kaldırma; güvenli iptal sonrası yeni sync isteğinin engellenmesi.
- Gelecekteki desktop uygulamasından **izinli ve yetkili** sync isteğini kabul edecek MVP backend sözleşmesi; gerçek masaüstü uygulaması veya sensör okuyucu geliştirilmeyecek.
- `Idempotency-Key`/batch anahtarı, request hash, tekrar yürütme ve çakışma semantiği; eş zamanlı istekler için DB tekilliği.
- Performance kayıtlarında kullanıcı/cihaz sahipliği ve privacy-by-design; açık yetki, hata ve veri sızdırmama kuralları.
- Faz 3'teki `performance_samples` yapısını engellemeyecek ölçeklenebilir sözleşme, ancak henüz desteklenmeyen ölçümleri kaydettim deme.

### 5.2. Auth talepleri ve entegrasyon belgeleri

- `auth-service-talepleri/auth-requests-01.md`: principal/`userId`, geçerli oturum ve hesap durumu doğrulama, cihaz pairing için gereken sözleşme, cihaz credential lifecycle veya servis kimliği eksikleri.
- `auth/auth-integration-01.md`: gerçekte kullanılan security config/filter, Auth çağrısı veya JWT doğrulama yolu, cihaz eşleştirmedeki koruma ve 401/403/CSRF test kanıtı.
- **Sahte client authentication kullanma.** Auth tarafında eksik capability varsa istek aç, blokajı bildir, izinsiz Auth kodu değiştirme.

### 5.3. Asgari test senaryoları

- Geçerli ve geçersiz kullanıcının cihaz eşleştirmesi; pairing kodu kullanım/süre aşımı/tekrar kullanım.
- Başka hesaba ait cihazın listelenememesi veya kaldırılamaması.
- İptal edilen cihazın sync yapamaması; izinsiz ölçüm aktarımının reddi.
- Aynı `(device, idempotency-key, payload)` iki kez/eş zamanlı gönderildiğinde tek kayıt; anahtar aynı içerik farklıysa conflict.
- Auth geçersiz/ulaşılamaz ve kullanıcı durumu kısıtlıysa fail-closed davranış.
- Negatif body validation, kısıtlı batch, SQL/JPA gerçek PostgreSQL tekillik kontrolleri.
- Sonuçları `testings/phase-01-tests.md` içine kanıtla.

### 5.4. Faz 1 çıkışı

`progress/phase-01.md`, `decisions/phase-01-decisions.md`, iki Auth belgesi, `reports/issues.md`, `testings/phase-01-tests.md`, **`docs/api.md`** ve şema değiştiyse **`database.md` + `er-diagram.md`** güncellensin; ardından §9 kalite kapısı uygulanıp commit metni hazırlanarak **DUR**.

**Commit başlığı:** `feature: LTZ-ALP-PERF-01 — Cihaz Eşleştirme ve Güvenli Ölçüm Senkronizasyon API'leri`

---

## 6. FAZ 2 — Performans oturumu ve oyun raporu yönetimi

**PDA:** `LTZ-ALP-PERF-02` — PERF-03 / PERF-04 / PERF-13 / PERF-20.

### 6.1. Planlanan uygulanabilir işler

- Manuel oturum başlatma ve sonlandırma; `ACTIVE`, `COMPLETED`, `ABANDONED` gibi durumları gerçek sözleşmeyle teyit et.
- Oturum başlangıç/bitiş zamanını, oyunu ve donanım profilini modelle; uygunsa cihaz referansı opsiyonel olmalı.
- Oyun bazında çözünürlük ve grafik ayarlarını ilişkilendiren **manuel** performans raporu; ölçülmeyen sensör/FPS verisi otomatik doldurulmasın.
- Silme: yalnız sahibin kendi oturum/ölçüm/rapor verisini silebilmesi; ilişkili veri, cascade/restrict ve gizlilik beklentisini açıkça modelle.
- `game-service` ve `hardware-service` ID referanslarını yalnız **servis sözleşmesi** ile doğrula; diğer DB'lere bağlanma. Diğer servis hazır değilse bunu raporla, temsili entegrasyonu gerçek entegrasyon diye sunma.
- İşlem tekrarları, eş zamanlı sonlandırma/silme ve optimistic locking/unique constraint ihtiyacını test et.

### 6.2. Auth belgeleri

- `auth-requests-02.md`: mevcut oturum principal, kullanıcı rol/durum, oturum/rapor mutasyonu ve silme için gerekli Auth kabiliyetleri.
- `auth-integration-02.md`: kullanıcıdan gelen kimliğin servis içinde sahiplikle karşılaştırılması, cihaz kaynaklı kayıt ile manuel oturum farkları, negatif Auth testleri.

### 6.3. Asgari test senaryoları

- Valid/invalid manuel oturum açma, kapatma; zaman ve durum geçişleri.
- Aynı oturumun tekrar sonlandırılması, eş zamanlı update/delete.
- Sahiplik ihlalleri ve başka kullanıcı raporunun silinememesi.
- Geçersiz oyun/hardware referansı ve servis bağımlılığı kesintisi.
- Gerçek veritabanında rapor ve oturum constraint kontrolü.
- `testings/phase-02-tests.md` içine sonuçları kanıtla.

### 6.4. Faz 2 çıkışı

İlgili faz ilerleme/karar/test/Auth belgeleri ve **`docs/api.md`** güncellensin; gerekliyse DB/ER dosyalarını da güncelle. §9 kalite kapısını çalıştır. **Commit taslağını sun ve DUR.**

**Commit başlığı:** `feature: LTZ-ALP-PERF-02 — Performans Oturumu ve Oyun Raporu Yönetimi`

---

## 7. FAZ 3 — Telemetri kabulü ve veri modeli

**PDA:** `LTZ-ALP-PERF-03` — PERF-05 / PERF-06 / PERF-07 / PERF-08 / PERF-09 / PERF-10 / PERF-11 / PERF-12.

### 7.1. Planlanan uygulanabilir işler

- CPU/GPU sıcaklığı, CPU/GPU kullanım oranı, RAM/VRAM, disk kullanım/okuma-yazma, fan RPM ve FPS verilerinin **desteklenen kaynaktan** kabulü ve saklanması.
- Ölçüm türü, birim, timestamp, oturum/cihaz ilişkisi, veri kaynağı ayrımı ve sınır değer kontrolü.
- `performance_samples` veya gerçek şemaya uygun, idempotent ve denetlenebilir batch işleme.
- Ölçümler gerçekten gelmediyse boş veriyi 0 ile doldurma. 0 ile `null` / ölçülmedi ayrımını koru.
- CPU/GPU kullanımı için yüzde sınırları; negatif FPS/MB/RPM gibi geçersiz değerlerin reddi; ölçüm türüne göre akla yatkın limitler. Kesin limitleri gerçek uygulama/sensör sözleşmesi üzerinden belirle ve belgeye yaz.
- Gelecek zaman, sırasız timestamp, aşırı büyük paket, desteklenmeyen metrik ve bozuk sayısal alanları değerlendir.
- Faz 1 sync sözleşmesinin geriye dönük tutarlılığını koru; farklı kimlik/format üreten ikinci paralel sync sistemi kurma.

### 7.2. Auth belgeleri

- `auth-requests-03.md`: cihaz kaynaklı kimlik/izin doğrulaması, pairing iptali, kullanıcı durum değişimi ve riskli yüksek hacimli sync işlemi için gereken eksikler.
- `auth-integration-03.md`: gerçek cihaz kimliğinin kullanıcıya bağlanması, metric write authorization, rate limit ve negatif testler.

### 7.3. Asgari test senaryoları

- Sekiz PERF metriğinin her biri için olumlu/olumsuz kayıt.
- Geçersiz metrik, birim, timestamp, negatif ve aralık dışı değerler; `null`, eksik sensör.
- Büyük batch, kısmi hata/transaction sınırı, iki kez gönderim, eş zamanlı tekrar ve idempotency.
- İptal edilmiş cihaz, başka kullanıcıya ait oturum, sahte `userId`, Auth arızası.
- DB CHECK/indeks/Flyway/JPA `validate` doğrulamaları, güvenli loglama.
- `testings/phase-03-tests.md` içine gerçek komut/sonuçları yaz.

### 7.4. Faz 3 çıkışı

Faz raporu, decisions, testings, Auth request ve integration, **`docs/api.md`**, `database.md` ve `er-diagram.md` (şema değiştiyse) güncellensin; §9 kalite kapısı uygulansın. **Commit taslağını sun ve DUR.**

**Commit başlığı:** `feature: LTZ-ALP-PERF-03 — FPS, Sıcaklık ve Sistem Telemetrisi Kayıt Altyapısı`

---

## 8. FAZ 4 — Ölçüm geçmişi, istatistik, grafik ve servis sözleşmeleri

**PDA:** `LTZ-ALP-PERF-04` — PERF-14 / PERF-15 / PERF-16 / PERF-19 / PERF-21.

### 8.1. Planlanan uygulanabilir işler

- Ölçüm geçmişi, oyun/cihaz/oturum/tarih/metrik filtreleri; tarih sınırı, sayfalama ve deterministik sıralama.
- Gerçek örnekler üzerinden ortalama/minimum/maksimum değerler; boş veri, farklı birim ve hesaplama doğruluğu.
- Web modülünün tüketebileceği timestamp + metric + unit temelli zaman serisi DTO'ları; büyük veriye karşı pencereli sorgu ve gerekirse açıklanmış aggregation/downsampling.
- Hardware Service için gereken performans özetlerini ve Recommendation Service için güvenilir FPS/performans referansını **minimum yetkiyle** sözleşmeli API üzerinden sun.
- Internal endpointlerde özel veri sızıntısı önleme; gerçek güvenilir servis kimliği, yetki ve kullanıcı gizliliği sözleşmesi. Mevcut Auth modelinin internal servis auth'u destekleyip desteklemediğini doğrula.
- Başka servisin DB'sine sorgu, ortak tablo veya sahipliği belirsiz veri kopyası oluşturma.

### 8.2. Auth belgeleri

- `auth-requests-04.md`: kullanıcı geçmişi/grafik izinleri, internal service-to-service authorization ve gizlilik için eksik sözleşmeler.
- `auth-integration-04.md`: gerçek owner filtreleri, service principal, 401/403/404, diğer servislerden çağrılar, veri minimizasyonu testleri.

### 8.3. Asgari test senaryoları

- Sayfalama/filtre kombinasyonları; zaman aralığı sınırı; boş sonuç.
- Ortalama/minimum/maksimum değerlerin birim ve örnek sayısıyla tutarlılığı.
- Zaman serisi sıralaması, uzun aralıklar, büyük veri ve indeks davranışı.
- Başka kullanıcı cihazı/ölçümü, gizli kaydın API'den sızmaması.
- Yanlış/eksik internal servis kimliğiyle yetkisiz erişim.
- Hardware/Recommendation tüketici sözleşmesi; gerçek entegrasyon mümkün değilse contract test + açık BLOCKED kaydı.
- `testings/phase-04-tests.md` içine sonuçları yaz.

### 8.4. Faz 4 çıkışı

- `progress/phase-04.md` ve bütün ilerleme/checklist durumlarını doğrula.
- Auth request/integration, decisions, tests, `docs/api.md`, DB/ER ve gerektiğinde architecture güncellemelerini tamamla.
- Tüm Performance Service testleri, fazlar arası regression, uygun güvenlik/yük doğrulamaları, §9 `pre-push` ve SonarQube kontrollerini uygula.
- `reports/final-report.md` içine **4 fazın kod teslimi durumu** ile kalan migration finalizasyonunu ayrı belirt.
- Commit taslağını hazırla; kullanıcı manuel commit yapmadan migration final aşamasına geçme.

**Commit başlığı:** `feature: LTZ-ALP-PERF-04 — Performans Geçmişi, İstatistik ve Grafik API'leri`

---

## 9. HER FAZIN ZORUNLU KALİTE KAPISI — Test + `pre-push` + SonarQube

Bu bölüm yeni çalışmanın **ayrılmaz bir parçasıdır**. Her fazın sonunda çalıştırılır. Dördüncü faz sonunda bütün servise yeniden uygulanır. Son migration düzenleme aşamasında da tekrar edilir.

### 9.1. Test ön koşulları

1. Mevcut repo `AGENTS.md`, `SECURITY.md`, `pre-push/pre-push.md`, `sonarqube/sonarqube.md` kurallarını oku; komutları **betiklerin gerçekte desteklediği argümanlarla** çağır.
2. JDK 25, Maven, Node ve gerekiyorsa Docker/PostgreSQL'in mevcut olup olmadığını kontrol et. Gizli ENV dosyalarını açma/çıktılama.
3. Geliştirme sırasında hızlı hedefli testleri koş, fakat faz sonunda kapsamlı gerçek doğrulama yap.
4. Unit, controller, repository, Spring Security, idempotency/eşzamanlılık ve PostgreSQL entegrasyon testleri anlamlı üretim davranışını kapsasın.
5. JaCoCo backend **satır coverage ≥ %80** hedefi ve repo zorunlu quality gate'i korunur. Coverage için testleri atlama veya üretim kodu dışlamalarını manipüle etme.
6. Hedeflenen bağımlı servis kapalıysa testleri sahte PASS gösterme; test yaklaşımını `mock-based contract / integration with running service` olarak ayır.

### 9.2. `pre-push` çalıştırılması — ZORUNLU

`pre-push` **Git push komutu değildir**; repoda manuel çalıştırılan yerel kalite betiğidir. Kullanıcının bu prompt ile verdiği yetki yalnız bu kalite betiğini **çalıştırmaya** yöneliktir, `git push` yetkisi değildir.

Kaynak `docs/SECURITY.md` içindeki örnek girişler:

```powershell
# Windows — desteklenen modu repository'de inceleyerek seç
.\pre-push\pre-push.cmd service
.\pre-push\pre-push.cmd backend
# Repo betiğine göre uygun ise full/dev-prod modu da vardır.
```

```bash
# Linux/macOS — repo betiğinde tanımlanan argümanları doğrula
sh ./pre-push/pre-push.sh full dev
```

**Önemli:** Yukarıdaki örnekler birer başlangıç referansıdır; `service` modu ek interaktif seçim veya parametre gerektirebilir. Betik içindeki gerçek kullanım dokümanını incele ve desteklenmeyen argüman uydurma. Çalıştırmadan önce betiğin istemeden environment switch, secret yazdırma, temizleme veya Git yazma işlemi yapmayacağını kontrol et. Böyle bir yan etki varsa önce kullanıcı izni al.

Uygun moda göre **Performance Service'e ilişkin backend testlerini, mümkünse genel backend regresyonunu** çalıştır. Her fazın sonunda `pre-push` başarısızsa hata nedenini düzelt ve yeniden çalıştır; düzeltilemiyorsa `BLOCKED/FAIL` işaretleyip kullanıcıya bildir.

`SAFE TO COMMIT` veya `BACKEND TEST CHECK PASSED` ifadesi yalnız betiğin geçerli yerel kapısını geçtiği anlamına gelir. **SonarQube sonucuyla eşanlamlı değildir.**

### 9.3. SonarQube analizi — `pre-push`ten AYRI ve ZORUNLU

- Her faz sonunda **`sonarqube/sonarqube.md`** ve güncel `sonar.cmd/.sh/.mjs` scriptlerini incele.
- Mevcut ve desteklenen akışla **Performance Service (ve gerekiyorsa backend parent) için gerçek analiz çalıştır**; sonuç/raporun hangi project key / branch / commit tree'ye ait olduğunu kaydet.
- SonarQube sunucusu veya gerekli token hazır değilse **kullanıcının gizli tokenını isteme, .env dosyasını açma, uydurma sonuç üretme**. Kullanıcıya ortamın neden bloke olduğunu ve güvenli olarak ne gerektiğini söyle.
- SonarQube sonucu görünüyorsa **Quality Gate**, Security, Reliability ve Maintainability notlarını (hedef: **A**), bug/vulnerability/code smell ve yeni kod uyarılarını kontrol et.
- HIGH/CRITICAL security bulguları ve Quality Gate başarısızlığı için çözüm üret; ilgisiz bulguları saklama.
- Scanner çalıştı ama sunucu analizi henüz sonuçlandırmadıysa bunu `PENDING` olarak yaz; otomatik A/PASS kabul etme.
- **`pre-push` başarılı + SonarQube başarısız = fazın kalite kapısı başarılı değildir.** Her iki sonucu birbirinden bağımsız belirt.
- SonarQube bir dış servise bağlıysa, kontrolsüz ağ/deploy/konfigürasyon değişikliği yapma; yalnız mevcut yerel proje scriptlerinin izinli akışını kullan.

### 9.4. Faz kalite raporu formatı

`testings/phase-XX-tests.md` içinde şu tabloyu gerçek sonuçlarla doldur:

| Kontrol | Komut/araç | Beklenen | Gerçek sonuç | Durum |
| --- | --- | --- | --- | --- |
| Compile/build | Repo Maven komutu | Başarılı | Komut, özet | PASS/FAIL/NOT RUN |
| Unit/service test | JUnit/Maven | İlgili senaryolar geçer | n başarılı / m başarısız | PASS/FAIL/NOT RUN |
| API/security | Spring test / integration | 401/403/owner kontrolü | Kanıt | PASS/FAIL/NOT RUN |
| PostgreSQL/Flyway | Gerçek DB/Testcontainers | Migration + JPA validate | Kanıt | PASS/FAIL/NOT RUN |
| JaCoCo | Maven/rapor | ≥ %80 satır | Gerçek yüzde | PASS/FAIL/NOT RUN |
| `pre-push` | Repo betiği | Yerel kalite kapısı geçer | Tam komut + çıkış kodu | PASS/FAIL/BLOCKED |
| SonarQube scan | Repo `sonarqube` betiği | Analiz tamamlanır | Proje/analiz kaydı | PASS/FAIL/BLOCKED/PENDING |
| Sonar Quality Gate | SonarQube | PASS | Gerçek kapı | PASS/FAIL/BLOCKED/PENDING |
| Sonar Security | SonarQube | A | Gerçek derece | A/B/C/D/E/UNKNOWN |
| Sonar Reliability | SonarQube | A | Gerçek derece | A/B/C/D/E/UNKNOWN |
| Sonar Maintainability | SonarQube | A | Gerçek derece | A/B/C/D/E/UNKNOWN |

Log dosyalarına secret veya kullanıcı verisi yazma. Çalıştırılmamış kontrol PASS değildir. Hiçbir test başarısı olmadan alt task `[x]` veya faz `DONE` yazma. Ortam kaynaklı eksiklikte **kod tamamlanmış olabilir fakat doğrulama BLOCKED** şeklinde dürüst ayrım yap.

### 9.5. Her fazın kabul kararı

Aşağıdaki maddeler aynı anda sağlanmadıkça faz **tamamlandı / commite hazır** olarak ilan edilmesin:

- [ ] Fazın bütün PDA alt taskları kabul testleriyle doğrulandı.
- [ ] Auth entegrasyonu gerçekte uygulandı ve negatif testleri geçti.
- [ ] Flyway/DB şeması doğrulandı, gerekiyorsa database/ER güncellendi.
- [ ] `docs/api.md` gerçek endpointlere göre güncellendi.
- [ ] Testler ve JaCoCo hedefi karşılandı.
- [ ] `pre-push` başarıyla çalıştı.
- [ ] SonarQube analizi ve Quality Gate sonuçları doğrulandı; A hedefleri karşılandı.
- [ ] Progress, decisions, issues, Auth request/integration, testing raporları güncellendi.
- [ ] Commit başlığı + gerçek maddelerle commit açıklaması hazırlandı.
- [ ] **Agent Git yazma işlemi yapmadı.**

Bir kriter bloke kaldıysa tamamlanma iddiası yerine sorunu ve tam olarak gerekli kullanıcı/ekip aksiyonunu bildir.

---

## 10. Beşinci aşama — Dört özellik commit'inden sonra Flyway şema konsolidasyonu

Bu çalışma **yalnız Faz 1–4 tamamlanıp kullanıcının dört özellik commit'ini kendisi attığı teyit edildikten sonra** başlayacak. Ajan kullanıcıya önceden şu bildirimi yapacak:

> "Performance Service'in dört fazının geliştirmesi ve commit hazırlıkları tamamlandı. Şimdi performans migration dosyalarındaki sonradan yapılan `ALTER TABLE` işlemlerini güvenle ilk `CREATE TABLE` tanımlarına birleştirmeyi değerlendireceğim. Önce Flyway uygulanma geçmişini ve veri güvenliğini kontrol edeceğim; risk varsa değişiklik yapmadan onay isteyeceğim. Bu çalışma ayrı beşinci commit olarak hazırlanacak."

### 10.1. İstenen sonuç

- Migration dosyalarını sadeleştir, gereksiz adım/tekrarları ve mümkün olan `ALTER TABLE` ifadelerini ilk tablo/constraint tanımlarıyla birleştir.
- Amaç: **konsolide başlangıç şemasında gereksiz `ALTER TABLE` kalmaması**. Tablo/indeks/FK oluşturma sıralaması ve gerçek veri tipi kısıtları doğru olmalı.
- Yalnız `performance-service` için V21–V30 aralığı; diğer servis migrationlarına dokunma.
- Entity/şema/ER ve API uyumunu kontrol et.

### 10.2. Değiştirilemez güvenlik koşulu

**Daha önce herhangi bir kalıcı/paylaşılan/production veritabanına uygulanmış Flyway migration dosyasının içeriğini değiştirme.** Değişiklik checksum ve veri geçişini bozabilir; `docs/decisions/migrations.md` ve `SECURITY.md` bunu yasaklıyor.

Önce:

1. Uygulanmış dosyaları ve `flyway_schema_history` durumunu uygun **izinli ve güvenli** yöntemle denetle (secret okumadan/ortama zarar vermeden).
2. Yalnızca henüz uygulanmamış ve geçmişi koruyan dosyalarda güvenli konsolidasyon yap.
3. Herhangi bir migration uygulanmışsa / başka ortamlar bilinmiyorsa **DUR**, riski ve alternatiflerini kullanıcıya açıkla. Onaysız `repair`, `clean`, `baseline`, volume silme, DB reseti veya yeniden numaralandırma yapma.
4. İlk şemaya konsolidasyon güvenli değilse sıfır `ALTER TABLE` hedefini gerçekleştirmek uğruna geçmişi bozma. Kalan `ALTER TABLE` adımlarını gerekçeleriyle belgele. Alternatif olarak mevcut şemayı koruyan yeni migration ancak onay ve uygun numara aralığıyla yapılabilir.

### 10.3. Son test ve dokümantasyon

- Konsolide başlangıç şemasını izinli **temiz test DB** üzerinde uçtan uca doğrula.
- Var olan migration zinciri ve yükseltme senaryosunu veri kaybına yol açmadan doğrula.
- Spring JPA `ddl-auto=validate`, repository ve service integration testleri.
- **`pre-push` ve ayrı SonarQube analizini yeniden çalıştır.**
- Güncel `docs/database.md`, `docs/er-diagram.md` ve `docs/decisions/migrations.md` uyumunu denetle; değişiklik varsa gerekli belgeleri güncelle.
- `reports/final-report.md` içine migration konsolidasyon sonucu, izin kısıtları, kalan `ALTER TABLE` ve final kalite sonuçlarını işle.

**Beşinci commit başlığı:**

`refactor: LTZ-ALP-PERF — Flyway Migration Düzenleme ve Şema Konsolidasyonu`

Commit açıklaması yalnız **gerçekte** yapılan düzenlemelerden oluşsun. **Agent commit/push yapmaz.**

---

## 11. Commit standardı — 4 özellik + 1 refactor

Kullanıcı her faz sonrası **kendisi** 1 commit atacak. Agent yalnız metin hazırlayıp kullanıcıya sunacak. Beşinci migration commit'i de aynı kurala tabidir.

### 11.1. Başlıklar (birebir)

```text
feature: LTZ-ALP-PERF-01 — Cihaz Eşleştirme ve Güvenli Ölçüm Senkronizasyon API'leri
feature: LTZ-ALP-PERF-02 — Performans Oturumu ve Oyun Raporu Yönetimi
feature: LTZ-ALP-PERF-03 — FPS, Sıcaklık ve Sistem Telemetrisi Kayıt Altyapısı
feature: LTZ-ALP-PERF-04 — Performans Geçmişi, İstatistik ve Grafik API'leri
refactor: LTZ-ALP-PERF — Flyway Migration Düzenleme ve Şema Konsolidasyonu
```

### 11.2. Commit body örnek şablonu

Ajan **bu örnek satırları otomatik başarı olarak kopyalamaz**. Gerçekte tamamladığı işlere göre doldurur:

```text
feature: LTZ-ALP-PERF-01 — Cihaz Eşleştirme ve Güvenli Ölçüm Senkronizasyon API'leri

- PERF-01: [Gerçekte yapılan cihaz eşleştirme geliştirmesi]
- PERF-02: [Gerçekte yapılan cihaz yönetimi geliştirmesi]
- PERF-17: [Gerçekte yapılan veri senkronizasyonu API altyapısı]
- PERF-18: [Gerçekte uygulanan tekrar gönderim ve idempotency kontrolleri]
- PERF-22: [Gerçekte uygulanan sahiplik/gizlilik önlemleri]
- Auth Service: [Gerçek entegrasyonun doğrulanmış sonucu]
- Testler: [Gerçek test/JaCoCo/pre-push/SonarQube sonuçları]
- Dokümantasyon: [Güncellenen karar, API, entegrasyon ve ilerleme belgeleri]
```

- Açıklama Türkçe, profesyonel, somut, kısa ve okunaklı olsun.
- Tamamlanmamış işi yapıldı diye yazma; her tamamlanan PERF alt görevine karşılık gelen somut değişiklik belirt.
- `Co-Authored-By`, AI/bot imzası, `Signed-off-by` dahil hiç katkı trailer'ı ekleme.
- `git add/commit/push` komutunu çalıştırma. Gerekiyorsa **yalnız kullanıcıya manuel kullanım için düz metin** olarak öner; otomatik çalıştırma.

---

## 12. Karar gerektiren durumlarda zorunlu duruş protokolü

Bu durumlardan biri varsa ilgili işi durdur:

- Auth Service'te gerekli doğrulama/servis kimliği/rol veya hesap durumu sözleşmesi yoksa
- Yeni Auth endpoint veya başka geliştiricinin servis kodunda değişiklik gerekiyorsa
- Güvenlik, gateway, JWT/cookie/CSRF, server-to-server trust modeli belirsizse
- Yanıt verilmeden uygulanması geri alınması zor bir API/DB tasarım kararı gerekiyorsa
- Başka servis API'si mevcut değil veya sözleşmesi uyuşmuyorsa
- Yeni ENV sözleşmesi ya da sır yapılandırması gerekiyorsa
- Uygulanmış migration değiştirilecekmiş gibi risk oluşuyorsa
- Kullanıcının mevcut verisi, Docker volume'u, migration history veya repo değişiklikleri zarar görebilecekse
- `pre-push` / SonarQube kalite kapısı başarısızsa ve düzeltme kapsam dışı karar gerektiriyorsa

**Sorunun sunum biçimi:**

1. `FAZ / PDA ALT TASK` kodu
2. Mevcut gerçek durum (kanıt ve ilgili dosya)
3. Problem ve etkisi
4. Seçenekler ve riskler
5. Teknik önerin
6. Kullanıcıdan gereken tek somut karar
7. Karar çıkana kadar hangi alt tasklar güvenle sürebilir?

Karardan sonra `decisions` ve `reports/issues.md` güncelle. Sorular alt task üzerinde çıkabilir; bu beklenen çalışma biçimidir. Bloke iş açıkken bütün fazı bitti deme.

---

## 13. Her faz sonunda kullanıcıya sunulacak zorunlu çıktı

Aşağıdaki sırayı kullan:

1. **FAZ X / LTZ-ALP-PERF-0X** ve genel durum (`DONE`, `PARTIAL`, `BLOCKED`).
2. Tamamlanan/eksik 22 task içinden **o faza ait** görevlerin checkbox tablosu.
3. Gerçekte değiştirilen önemli dosyalar, API endpointleri ve migrationlar.
4. Auth Service'e yazılan talepler ve **yapılan gerçek** entegrasyonlar.
5. Test sayıları, JaCoCo %, `pre-push` komutu/çıkış kodu, SonarQube Quality Gate ve üç A derecesi.
6. Açık problemler, riskler, karara ihtiyaç varsa soru.
7. `docs/api.md` ve diğer belgelerin güncel olup olmadığı.
8. Hazır **commit başlığı ve açıklaması** (yalnız biten işler).
9. **"Commit/push yapmadım. Manuel commit ve devam talimatını senden bekliyorum."**

Sıradaki faza veya migration konsolidasyonuna otomatik geçme.

---

## 14. Final teslim kabul listesi

- [ ] `LTZ-ALP-PERF-01` kapandı (5 task)
- [ ] `LTZ-ALP-PERF-02` kapandı (4 task)
- [ ] `LTZ-ALP-PERF-03` kapandı (8 task)
- [ ] `LTZ-ALP-PERF-04` kapandı (5 task)
- [ ] Toplam 22 PERF alt taskı eksiksiz doğrulandı
- [ ] Auth Service gerçek entegrasyonu ve 4 request/integration dosyası doğrulandı
- [ ] `docs-performance` klasörü ve bütün faz raporları mevcut
- [ ] `docs/api.md` her faz sonunda güncellendi
- [ ] `docs/database.md` / `docs/er-diagram.md` gerçek DB'ye uyumlu
- [ ] `docs/architecture.md` yalnız gerektiğinde güncellendi
- [ ] Flyway V21–V30 disiplini korundu; migrationlar uygulanmış geçmişe zarar vermedi
- [ ] Unit/integration/security/repository/validation/idempotency/regression testleri geçti
- [ ] JaCoCo satır coverage ≥ %80 (gerçek ölçüm)
- [ ] Her faz sonunda `pre-push` çalıştırıldı ve sonucu raporlandı
- [ ] Her faz sonunda ayrı SonarQube analizi/Quality Gate sonucu raporlandı; A hedefleri ölçülerek doğrulandı
- [ ] Kullanıcı manuel olarak dört feature commit'ini attı
- [ ] Dört commit'ten sonra güvenli migration konsolidasyonu değerlendirildi ve varsa 5. commit taslağı hazırlandı
- [ ] Final rapor açık sorunları ve MVP sonrası masaüstü sınırını dürüstçe belirtiyor
- [ ] **Ajan hiçbir Git yazma işlemi veya push yapmadı**

---

## 15. ŞİMDİ BAŞLA — İlk yürütme komutu

1. **ÖNCE `AGENTS.md` ve `CLAUDE.md` oku.** Bu kural sonraki her ajan oturumunda da geçerli.
2. §1'deki bütün zorunlu proje belgelerini ve `pre-push`, `sonarqube` yönergelerini oku.
3. `feature/performance-service` branch'ini ve çalışma ağacını **salt okunur** kontrol et.
4. Performance, Auth, Hardware ve Gateway mevcut kodunu analiz et. Auth hazır bilgisine rağmen gerçek sözleşmesini doğrula.
5. `docs-hardware` konumuna uygun **`docs-performance`** yapısını oluştur; master checklist'e dört ana taskın kaynakta yazıldığı gibi 22 alt taskını ekle.
6. Faz 1'in `auth-requests-01.md` ihtiyaçlarını mevcut Auth durumundan çıkar; `auth-integration-01.md` için gerçek entegrasyon planı yap.
7. Kritik karar gerekmiyorsa **yalnız Faz 1** geliştirmesine başla.
8. Faz 1'i gerçek testlerle, **pre-push + SonarQube** kontrolleriyle doğrula; API ve rapor dosyalarını güncelle.
9. İlk `feature: LTZ-ALP-PERF-01 — ...` commit mesajını gerçek maddelerle hazırla. **COMMIT/PUSH ATMADAN DUR.**

**Son hedef:** Dört bağımsız, güvenli, doğrulanmış backend özellik teslimi ve ardından mümkünse güvenli Flyway konsolidasyonu; gerçek Auth entegrasyonu, açık test kanıtları, her fazda `pre-push` ve SonarQube sonuçları, güncel API dokümanı ve tamamen kullanıcı kontrolünde Git geçmişi.
