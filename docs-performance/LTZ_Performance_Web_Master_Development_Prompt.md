# LobbyTwoZero — Performance Web | MASTER DEVELOPMENT PROMPT

> **Amaç:** Bu dosya, GPT-6.1 Sol Medium / Codex'e tek sohbet ve tek çalışma akışında verilecek **bağlayıcı geliştirme talimatıdır**. Ajan mevcut LTZ reposunu önce inceler; üç Performance Web PDA ana taskını **sırayla, tam kapsamlı** uygular; her ana task sonunda ayrı, detaylı bir **commit mesajı taslağı** oluşturur; üçüncü task sonunda **merge öncesi pre-push ve SonarQube** kontrollerini çalıştırır, sorunları giderir ve **PR title + PR description taslağı** verir. Gerçek commit, push, PR oluşturma veya merge yapmaz.
>
> **Sorumlu:** Alper  
> **Ürün:** LobbyTwoZero (LTZ) — Performance Web  
> **Kapsam:** Next.js LTZ Performance web modülü, **3 PDA ana task / 10 PWEB alt task**  
> **Kaynak:** `LTZ_PDA_Alper(4).md`, `LTZ_Servis_Icerikleri_Alper.md`; mevcut repo kodu ve bağlayıcı proje kuralları önceliklidir.  
> **Çalışma modeli:** **Tek chat / 3 ardışık task / 3 ayrı commit önerisi / 1 final PR önerisi**  
> **Duruş semantiği:** Her task sonunda **kodlamayı durdurup checkpoint yap**, test ve kapsamı doğrula, commit metnini kayıt altına al ve görünür durum bildir; **kullanıcıdan `devam` istemeden sonraki taska geç**. Üçüncü taskın ardından final quality gate ve PR taslağını hazırla.  
> **Git:** Commit/push/merge için bu talimat yetki vermez; yalnız öneri metinleri üretilecek.

---

## 0. Değişmez talimatlar ve çalışma şekli

Sen LobbyTwoZero projesindeki **Performance Web** görevlerini uçtan uca tamamlamakla sorumlu bir yazılım ajanısın. Görevin sadece görsel sayfa oluşturmak değil; **gerçek backend servis sözleşmeleriyle çalışan**, yetkilendirmesi ve veri gizliliği doğru uygulanmış, erişilebilir, responsive, test edilmiş, bakımı kolay, mevcut tasarım sistemiyle bütünleşik bir teslim üretmektir.

### 0.1. Öncelikler

1. **Her şeyden önce** repo kökündeki `AGENTS.md`, `CLAUDE.md` ve varsa ilgili iç içe ajan talimatlarını oku. Proje kurallarını görmeden dosya değiştirme.
2. `docs/SECURITY.md`, `docs/web-rules.md`, `docs/architecture.md`, `docs/folder-structure.md`, `docs/api.md`, `docs/database.md`, `docs/er-diagram.md` ve `docs/decisions/migrations.md` dosyalarını **reponun güncel sürümüyle** incele. Eksik belgeleri varmış gibi kabul etme.
3. Sorumluluk sırası: **mevcut doğrulanmış repo kodu ve sözleşmeler > güncel servis gereksinimleri/PDA > eski gereksinim docx**; ancak `SECURITY.md` güvenlik politikası gevşetilemez.
4. `git status`, `git branch --show-current`, `git diff`, `git log` ile aktif dalı ve önceden var olan değişiklikleri kaydet. **Branch adını varsayma, branch oluşturma veya değiştirme.** Beklenmeyen bir dal/çalışma ağacı riski varsa güvenli kapsamda kalıp bildir.
5. **`git add`, `git commit`, `git push`, `git merge`, `git rebase`, `git reset`, `git clean`, tag, PR açma, deploy, release, Git kimliği değiştirme YASAK.** Sadece salt-okunur Git incelemeleri ve `git diff --check` serbesttir. Kullanıcının manuel commit/PR/merge kontrolü değişmez.
6. **`Co-Authored-By`, `Signed-off-by`, Codex/Claude/AI bot veya ortak-yazarlık trailer'ı ekleme.** Git author/user config'e dokunma.
7. **Tek sohbet içinde üç taskın hepsini sırayla yap.** Her task sonunda ayrı checkpoint/commit önerisi üret; checkpoint bitince kullanıcı yanıtı beklemeden sonraki taskı başlat. **Tek bir taskın değişikliklerini baştan sona doğrulamadan diğerine başlama.** Kullanıcıya commit atmadan sonraki taska geçildiğini açıkça bildir.
8. Otomatik devam **sınırsız yetki değildir**. Güvenlik politikasının açık onay istediği değişiklik, gizli ENV sözleşmesi, veri kaybı riski, uygulanmış migration'ı değiştirme veya diğer takım üyesinin sahip olduğu servis koduna müdahale gerektiriyorsa **o işlemde dur**; bağımsız güvenli işleri tamamla, engeli belgele. Güvenlik kararı uydurma.
9. **Üretim UI'da fake backend, mock cevap, sabit `userId`, uydurma cihaz, sahte sensör/FPS, statik başarılı işlem veya olmayan indirme URL'si kullanma.** Testte mock izinlidir; üretimde değildir.
10. **KISS / DRY / YAGNI / SOLID (gerçekten gerektiği kadar)**: En küçük yeterli çözüm. Gereksiz abstraction, wrapper, adapter, provider, generic utility, context, global state, bağımlılık, klasör veya placeholder özellik ekleme.
11. **Code hygiene zorunlu:** unused import/variable, ölü kod, copy-paste, commented-out kod, `console.log`, debugger, gereksiz `eslint-disable`, riskli `any`, `@ts-ignore`, `as unknown as`, gereksiz `useEffect`/`useMemo`, gereksiz client component ve kullanılmayan export bırakma. Var olan kodun tutarlı örüntüsünü takip et.
12. Yeni paket, yeni ENV anahtarı, script, global tema veya altyapı değişikliği yapmadan önce mevcut olanı yeniden kullan. Gereken değişiklik için lisans/güvenlik, build ve runtime etkisini değerlendir; repo politikası onay gerektiriyorsa onay almadan uygulama.
13. Test ve kalite kapılarını çalıştır; başarısız olan yeni sorunları düzeltip yeniden dene. **Test/coverage/Sonar/CI çıktısı uydurma, test skip etme, üretim güvenliğini gevşeterek test geçirme.**
14. Kullanıcıya kapsamı ilerleme raporları ve son teslimde **Türkçe**, somut dosya/test/endpoint kanıtlarıyla anlat. Belirsiz noktalarda gözlem, karar ve kalan bağımlılığı ayrı yaz.

### 0.2. Tek sohbetlik yürütme protokolü (ÇOK ÖNEMLİ)

Aşağıdaki yürütme planını aynen uygula:

```
BAŞLANGIÇ
  ├─ Repo/AGENTS/CLAUDE/güvenlik/web/backend sözleşmesi denetimi
  ├─ Performance Web master checklist ve 3 faz dokümantasyonu
  ├─ TASK 1: PWEB-01, PWEB-02, PWEB-03
  │    ├─ Gerçek kod + entegrasyon + hedefli testler
  │    ├─ Faz 1 checklist/rapor/kanıt
  │    ├─ 1. ayrıntılı Conventional Commit MESAJI ÖNER
  │    └─ CHECKPOINT 1 → kayıt altına al, bildir, OTOMATİK DEVAM ET
  ├─ TASK 2: PWEB-04, PWEB-05, PWEB-06, PWEB-07
  │    ├─ Gerçek kod + gerekiyorsa hardware-service uyarlaması + testler
  │    ├─ Faz 2 checklist/rapor/kanıt
  │    ├─ 2. ayrıntılı Conventional Commit MESAJI ÖNER
  │    └─ CHECKPOINT 2 → kayıt altına al, bildir, OTOMATİK DEVAM ET
  ├─ TASK 3: PWEB-08, PWEB-09, PWEB-10
  │    ├─ Gerçek kod + entegrasyon + security/ownership testleri
  │    ├─ Faz 3 checklist/rapor/kanıt
  │    ├─ 3. ayrıntılı Conventional Commit MESAJI ÖNER
  │    └─ CHECKPOINT 3 → kayıt altına al, bildir
  ├─ FINAL GATE (MERGE ÖNCESİ)
  │    ├─ Kod/unused import/lint/typecheck/test/coverage/build/E2E/regresyon
  │    ├─ GERÇEK PRE-PUSH çalıştır → düzelt → tekrar çalıştır
  │    ├─ AYRI SONARQUBE taraması + Quality Gate → düzelt → tekrar doğrula
  │    ├─ Full diff, gizli veri ve entegrasyon gözden geçirmesi
  │    └─ PASS/BLOCKED ayrımı, rapor, kalan riskler
  └─ ÜÇ COMMIT TASLAĞI + PR TITLE + DETAYLI PR DESCRIPTION + SON RAPOR
       (Gerçek Git write / PR oluşturma / merge YOK)
```

- **Checkpoint'te durma**: O taskın kod değişikliklerini sonlandır, kapsamı izole et, testleri ve `git diff --check` sonuçlarını kaydet, commit başlığı/gövdesini **o taskın tamamlanan gerçek değişikliklerinden** türet; ardından **bir sonraki taska otomatik devam et**. Sadece UI yanıtı sonlandırıp `devam` bekleme.
- Her taskın commit metni, diğer taskların değişiklikleriyle karışmamalı. Her checkpoint'te **ilgili dosyaların listesi, test komutları ve sonuçlarını** kaydet. Aynı dosya birden çok taskta değişiyorsa her aşamada neyin değiştiğini ayrı açıkla. Henüz Git commit olmadığı için gerçek commit SHA'sı uydurma.
- **Normal dışı durma**: Kritik yetki/güvenlik/veri kaybı riski, proje politikasındaki açık onay veya düzeltilemeyen kritik teknik blokaj varsa ilgili operasyonu yapma. Güvenli bağımsız işleri tamamla. “3 task bitti” iddiasını yalnız gerçek kabul sonuçlarına göre yaz.

---

## 1. Zorunlu repo keşfi — herhangi bir kod değişikliğinden önce

### 1.1. Okunacak belgeler ve kod

**Ajan ve kalite belgeleri**

- `AGENTS.md`, `CLAUDE.md` ve ilgili iç içe talimatlar
- `docs/SECURITY.md`, `docs/web-rules.md`, `docs/architecture.md`, `docs/folder-structure.md`
- `docs/api.md` (mevcut endpointleri doğrula; sadece gerçekten değişen endpointleri güncelle)
- `docs/database.md`, `docs/er-diagram.md`, `docs/decisions/migrations.md` (yalnız backend uyarlaması gerekirse değiştir)
- `pre-push/` ve `sonarqube/` içindeki **gerçek** kullanım belgeleri ve scriptler
- Repoda mevcut Alper PDA/servis gereksinimleri, `docs/checklist/` ve varsa mevcut `docs-hardware` / `docs-performance` raporları
- Paylaşılan Hardware frontend tasarım kuralları ve repo tema token'ları; mevcut kodda hangisinin geçerli olduğunu doğrula

**Frontend**

- `frontend/package.json` scriptleri, `package-lock.json`, Next.js/i18n konfigürasyonu
- `frontend/src/app/` içindeki public/dashboard route grup örüntüleri
- `frontend/src/features/{performance,hardware,recommendation,auth}` mevcut feature yapıları
- `frontend/src/shared/lib/api/client.ts` veya mevcut gerçek `apiRequest` uygulaması
- `frontend/src/shared/components/ui/`, tema CSS tokenları, TR/EN mesaj dosyaları
- `frontend/` mevcut unit/component ve `e2e/` testleri

**Backend ve Gateway — salt-okunur analiz, yetki dahilinde dar düzeltme**

- `backend/services/auth-service/`: gerçek cookie/JWT, session, current principal, CSRF, 401/403, account status davranışları
- `backend/services/hardware-service/`: donanım profili, görünürlük, `hardwareProfileId`, model/marka ve endpoint/DTO sözleşmesi
- `backend/services/performance-service/`: bağlı cihaz, geçmiş, session, ölçüm, grafik, rapor, silme API'leri
- `backend/services/recommendation-service/`: oyun uyumluluğu, öneriler, tahmin ve neden bilgisi
- `backend/services/game-service/`: oyun ve minimum/önerilen sistem gereksinimleri için okunabilir sözleşme
- `backend/services/api-gateway/`: frontend'in kullandığı route/proxy/CORS/credentials sözleşmesi
- `backend/pom.xml`, ilgili servis POM'ları, mevcut testler, mevcut migration dosyaları

### 1.2. Keşif çıktısı

Kodlamaya başlamadan **kanıta dayalı bir sözleşme matrisi** hazırla:

| Ekran ihtiyacı | Kaynak servis/API | Gerçekten mevcut mu? | Auth ve sahiplik | Eksik alan / uyarlama | Kullanılacak strateji |
| --- | --- | --- | --- | --- | --- |
| Kullanıcı oturumu | auth-service | Kodla doğrula | HttpOnly/CSRF/principal | Var/yok | Mevcut güvenli pattern |
| Bağlı cihazlar | performance-service | Kodla doğrula | userId ve device ownership | Var/yok | Gerçek liste/boş durum |
| Donanım profilleri | hardware-service | Kodla doğrula | owner/visibility | Var/yok | API, gerekirse dar HW değişikliği |
| Geçmiş/rapor/ölçüm | performance-service | Kodla doğrula | userId/session ownership | Var/yok | Gerçek veri/boş durum |
| Oyun gereksinimleri | game-service | Kodla doğrula | izin verilen veri | Var/yok | Yalnız mevcut sözleşme |
| Uyumluluk/öneri | recommendation-service | Kodla doğrula | izinli kişiselleştirme | Var/yok | Gerçek sonuç/veri yetersiz |

Eski dokümanda tanımlanan endpointi **mevcut kodda bulunmadan uygulanmış sayma**. İlk değerlendirme ile final değerlendirmeyi karıştırma. Mevcut başka geliştiricinin çalışma ağacı değişikliklerini silme/üzerine yazma.

---

## 2. Mimari, servis yetkisi ve entegrasyon sınırları

### 2.1. Web: ayrı mikroservis değil

- LTZ Performance Web, mevcut **Next.js frontend** içindeki feature'dır. Yeni `performance-web-service`, yeni veritabanı veya bağımsız frontend projesi oluşturma.
- Kaynaklarda yer alan teknik referanslar (repo ile teyit et): Next.js 16, React 19, TypeScript, Tailwind 4, `next-intl`, `Vitest`, `Playwright`; backend Java/Spring Boot, PostgreSQL, Flyway.
- Feature sınırını koru: `src/app` route/page, `src/features/<feature>/components|hooks|services|schemas|types`, `src/shared` gerçekten ortak parçalar içindir.
- **Bileşen → hook → feature services → mevcut `apiRequest` → Gateway** zincirini kullan. Component içinde doğrudan `fetch` yazma; tarayıcıdan `:5093`/`:5095` gibi backend servisi portuna erişme; yeni paralel API client yazma.
- Varsa Next.js Server/Client Component sınırlarına, React Compiler'a, mevcut caching/auth politikasına uyarak gereksiz `use client` ve `useEffect` kullanımından kaçın.

### 2.2. Auth Service — mevcut entegrasyonu yap, eksik için request dosyası

- **Mevcut gerçek Auth sözleşmesine entegrasyon** frontend/uygun consumer tarafında yapılabilir ve yapılmalıdır. Cookie-based oturumda `credentials: include`, HttpOnly cookie, CSRF, giriş durumu, 401/403 ayrımı ve geçerli `userId` sözleşmesini doğrula.
- JWT/cookie veya refresh token'ları `localStorage`, `sessionStorage`, IndexedDB, URL query veya log'a yazma. İstemci header/body'den gelen `X-User-Id` / `userId` değerini yetki kaynağı kabul etme.
- **`auth-service` sahibi başka geliştiricidir.** Onun koduna, migration'ına, rollerine, security config'ine ve DB'sine doğrudan dokunma. Yetmeyen auth endpoint/sözleşme/servis kimliği için **yalnız request belgesi** hazırla.
- Talep dizini **BİREBİR**: `docs-performance/integrtion/auth/performance-web-requests/`. Buradaki `integrtion` yazımını **kendiliğinden `integration` olarak değiştirme**; kullanıcı tarafından verilen yol bağlayıcıdır.
- Her **gerçek yeni ihtiyaç için** dosya: `PWEB-XX-auth-request-<kisa-konu>.md`. Aynı sorunu farklı dosyalarda tekrar etme.
- Auth request dosyasında: bağlı PWEB kodları, mevcut endpoint/kanıt, eksik kabiliyetin nedeni, beklenen tüketici davranışı, **örnek şematik request/response**, 401/403/CSRF/token/sahiplik güvenliği, kabul kriterleri, bağımlı ekranlar, öncelik, açık statü (`REQUESTED/BLOCKED/VERIFIED`), Auth sahibine iletilecek net istek olsun.
- Talep açılmasını **gerçek entegrasyon tamamlandı** diye sayma; çözülmeyen capability yüzünden erişim güvenliği eksikse task `PARTIAL/BLOCKED` kalır. Auth ihtiyacı yoksa **boş talep dosyası oluşturma**, 'Yeni Auth talebi yok; mevcut sözleşme kullanıldı' kaydı yeterlidir.
- Mock authentication, `permitAll`, hardcoded oyuncu, yetkisiz fallback veya API Gateway'e aşırı güvenle güvenlik açığı oluşturma.

### 2.3. Hardware Service — doğrudan uyarlama yetkisi VAR

- **`hardware-service` sorumlusu kullanıcıdır.** Performance Web'in gerçek sözleşmeyle çalışabilmesi için **ihtiyaç olan asgari API/DTO/validation/ownership/test değişikliklerini doğrudan yapabilirsin.**
- Önce mevcut endpointi, DTO'yu, filtreyi ve pagination'ı yeniden kullan. Gereksiz endpoint çoğaltma, farklı veri sahibi oluşturma, aynı profili Performance DB'de kopyalama.
- HW profilleri, bileşenler, model kataloğu ve opsiyonel marka/üretici alanının sahibi Hardware Service'tir. Genel model için marka zorunlu değildir; elle girilen ve eksik alanlar doğru ele alınır.
- Kullanıcı yalnız kendi donanım profilini değiştirebilir. Görünürlük `hardware-service` API katmanında uygulanır; frontend'in gizlemesi yetmez.
- Şema gerekiyorsa **yalnız hardware DB** ve Hardware için ayrılmış **V1–V10** Flyway aralığını gözet; mevcut uygulanmış migration'ı değiştirme, şemayı rastgele yeniden başlatma, `repair/clean` yapma. İlgili backend test, docs/api ve DB/ER dokümanını güncelle.
- API uyarlaması için kapsamı dar tut; HW-01..17'yi yeniden baştan yazma.

### 2.4. Performance / Recommendation / Game servisleri

- `performance-service`, cihaz/oturum/ölçüm/rapor/veri silmenin; `recommendation-service`, öneri/uyumluluk/tahminin; `game-service`, oyun kataloğu ve gereksinimlerin sahibidir.
- Öncelikle **mevcut ve test edilmiş API'leri** tüket. Performance/Recommendation, Alper'in sorumluluk alanında olsa da **yalnız PWEB için zorunlu dar kapsamlı sözleşme düzeltmeleri** yap; yeni büyük backend özellik veya başka PDA ana tasklarını bu işe ekleme.
- Game/Auth gibi diğer ekip sahiplerinin servislerinde değişiklik yapma; eksik sözleşmeyi net bağımlılık olarak raporla. Hardware dışı ortak servis/gateway güvenlik değişiklikleri için repo onay şartları geçerlidir.
- Başka servisin veritabanına doğrudan SQL/JPA/FK ile erişme; servisler arası ilişkiler için kanonik ID ve izinli HTTP/contract kullan.
- Performance DB migration gerekirse **V21–V30**, Recommendation DB migration gerekirse **V11–V20** aralığı ve mevcut geçmiş korunur. Gerçekten zorunlu değilse migration açma.
- API erişilemezse kullanıcıya kontrollü hata/boş durum göster; `200 []` dönen hayali proxy veya test mock'unu production'a taşımak yasaktır.

### 2.5. Güvenlik ve ortam yönetimi

- Gerçek `.env`, `.env.dev`, `.env.prod`, `frontend/.env.local`, `*.bak`, credential/token/secret dosyalarını **okuma, kopyalama, loglama, değiştirme**. Kullanılabilir referans `.env.example` ve gizli olmayan kaynak koddur.
- Yeni ENV adı/değeri, JWT/CORS/CSRF yaklaşımı, credential, güven modeli veya Docker volume işlemi repo politikasında izin gerektiriyorsa **önce onay**; blokajda bağımsız işleri sürdür.
- Backend API'leri kullanıcı ve kaynak sahipliğini **backend'de** doğrulamalı; IDOR/BOLA, hesap durumu, rol, session expiry/yenileme, CSRF, 401/403/404/409/429/5xx/timeout davranışlarını test et.
- Performans verileri kullanıcıya özeldir; URL, log, SEO metadata, analytics payload, paylaşım önizlemesi veya hata mesajına kişisel ölçüm sızdırma.
- Değişiklik kullanıcının mevcut verisini silecek veya uygulanmış Flyway geçmişini bozacaksa **dur ve izin iste**. `.env` switch, reset, destructive DB, force-fix veya deploy uygulama.

---

## 3. Dokümantasyon ve checklist yapısı

Var olan `docs-performance` / `docs-hardware` düzenine uyum sağla; yeni dokümanları mevcut klasör varsa koruyarak genişlet. Minimum hedef:

```text
docs-performance/
├── integrtion/
│   └── auth/
│       └── performance-web-requests/
│           └── PWEB-XX-auth-request-<konu>.md   # yalnız gerçek talep varsa
├── performance-web/
│   ├── performance-web-master-checklist.md
│   ├── progress/
│   │   ├── task-01.md
│   │   ├── task-02.md
│   │   └── task-03.md
│   ├── testings/
│   │   ├── task-01-tests.md
│   │   ├── task-02-tests.md
│   │   ├── task-03-tests.md
│   │   └── pre-merge-quality.md
│   └── reports/
│       ├── issues.md
│       ├── commit-messages.md
│       ├── pr-description.md
│       └── final-report.md
```

**Not:** Mevcut repoda aynı amaca hizmet eden kanonik klasör/rapor varsa gereksiz duplicate üretme. Ancak **Auth request yolu aynen korunmalı**. Bu yeni klasörün yeri repo kurallarında kayıtlı değilse `docs/folder-structure.md` içinde yalnız ilgili eklemeyi kaydet. Raporların içi gerçek kanıt olmadan doldurulmaz; gelecek tasklar başlangıçta `[ ]` veya `PLANNED` kalır. Geçmişi yok sayıp yeni rapor isimleriyle karışıklık yaratma.

### 3.1. İşaretleme standardı

- `[ ]`: yapılmadı, kısmi, test edilmedi veya bloke.
- `[x]`: gerçek kod + istenen akış + ilgili güvenlik + uygun gerçek test + güncel dokümantasyon doğrulandı.
- Kısmi işte `[ ]` bırak ve yanında `IN_PROGRESS / PARTIAL / BLOCKED` sebebi/kanıtı belirt.
- Her alt task için kod, kabul şartları, dokunulan dosyalar, gerçek API ve veri kaynağı, test, Auth/Hardware bağımlılığı, durum olsun.
- Her ana task bitiminde o taskın **tamamlanan / toplam alt görev sayısı** ve checkpoint sonucu saklansın.
- Master checklist'te 3 ana task ve 10 alt task **aşağıdaki PDA ifadeleri aynen** bulunacak; aşağıdaki ek teknik adımlar ayrı kabul kontrolleridir, PWEB kodu yaratma.

---

## 4. ANA CHECKLIST — PDA'daki 3 görev ve 10 alt görev

### TASK 1 — LTZ-ALP-PWEB-01 — Performance Tanıtım ve Cihaz Yönetimi Arayüzü

**Sorumlu:** Alper  
**Servis:** `Next.js / LTZ Performance modülü`  
**Faz:** MVP  
**İlgili gereksinimler:** PWEB-01, PWEB-02, PWEB-03

**Ana task açıklaması:** Mevcut Next.js uygulamasında tanıtım, bağlantılı cihazlar ve henüz yayınlanmamış indirme alanını oluştur.

- [ ] **PWEB-01 — Performance ana sayfası:** LTZ Performance özelliklerinin anlatıldığı web sayfası.
- [ ] **PWEB-02 — Masaüstü uygulaması indirme alanı:** Masaüstü sürümü yayımlandığında kurulum dosyası bağlantısının sunulacağı alan. **MVP'de gerçek indirme bağlantısı aktif olmayacak**; yayımlanmamış dosyaya yönlendirme yapılmayacak.
- [ ] **PWEB-03 — Cihaz yönetimi:** Kullanıcının hesabına bağlı cihazları görüntüleyebileceği alan; masaüstü bağlantısı yokken uygun boş durum gösterilmesi.

**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

#### TASK 1 — Uygulama ve kabul kriterleri

- [ ] Mevcut route ve navigasyona uygun **LTZ Performance tanıtım** sayfası: ne sunduğu, şu anda gerçekten kullanılabilenler ve MVP sonrası masaüstü özellikleri açıkça ayrılır.
- [ ] Desktop app indirme alanı **yayınlanana kadar pasif** ve nedenini açıklayan erişilebilir durumdadır. Uydurma indirme linki, örnek kurulum dosyası, aktifmiş gibi CTA yoktur.
- [ ] Kullanıcının gerçekten bağlı cihazları **mevcut `performance-service` API'sinden** okunur; yalnız sahip olduğu cihazlar gösterilir. Varsa API'nin sunduğu durum/son eşleşme/güvenli bağlantı kaldırma gerçek sözleşmeye göre eklenir; sözleşmede yoksa uydurulmaz.
- [ ] Bağlı cihaz yoksa anlamlı `empty state`; yetkisiz/oturumsuz/bağımlılık kesintisinde ayrı mesaj, güvenli retry ve navigation vardır.
- [ ] TR/EN, light/dark mevcut destek, responsive, klavye ve screen reader etiketleri, reduced-motion doğrulanır.
- [ ] Unit/component/API state testleri; uygun olduğunda E2E smoke; `git diff --check` geçer.
- [ ] `progress/task-01.md` ve `testings/task-01-tests.md` gerçek sonuçlarla yazılır.
- [ ] **CHECKPOINT 1:** Ayrıntılı **1. Conventional Commit başlığı + body** `reports/commit-messages.md` içine yazılır; kısa ara rapor gönderilir; **hiçbir Git commit atmadan TASK 2'ye otomatik geçilir.**

### TASK 2 — LTZ-ALP-PWEB-02 — Donanım ve Performans Paneli

**Sorumlu:** Alper  
**Servis:** `Next.js / LTZ Performance modülü`  
**Faz:** MVP  
**İlgili gereksinimler:** PWEB-04, PWEB-05, PWEB-06, PWEB-07

**Ana task açıklaması:** Kullanıcının kayıtlı donanımını, performans geçmişini, ölçüm grafiğini ve oyun raporlarını doğru boş durumlarla sun.

- [ ] **PWEB-04 — Donanım paneli:** CPU, GPU, RAM ve diğer kayıtlı donanım özelliklerinin görüntülenmesi.
- [ ] **PWEB-05 — Performans geçmişi:** Kayıtlı performans oturumlarının listelenmesi.
- [ ] **PWEB-06 — Performans grafikleri:** Mevcut FPS, sıcaklık ve kullanım ölçümlerinin grafiklerle gösterilmesi; veri olmadığında boş durumun doğru sunulması.
- [ ] **PWEB-07 — Oyun performans raporları:** Kullanıcının oyun bazlı manuel/kayıtlı performans sonuçlarını görüntülemesi.

**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

#### TASK 2 — Uygulama ve kabul kriterleri

- [ ] Donanım paneli gerçek Hardware Service profil(ler)inden CPU/GPU/RAM/diğer mevcut alanları gösterir; **çoklu bilgisayar**, opsiyonel marka, elle girilmiş model, bilinmeyen alan ve gizlilik durumu işlenir. Hardware API eksikse yalnız gerekli dar uyarlamayı yap ve backend test ekle.
- [ ] Geçmiş oturumlar `performance-service`ten gelir; mevcut API'nin desteklediği sıralama/filtre/sayfalama kullanılır. Tarih/saat ve timezone dönüşümü tutarlıdır.
- [ ] FPS/CPU-GPU sıcaklığı/kullanım ölçümleri yalnızca **mevcut kayıtlı ölçümlerden** çizilir. Zaman damgası, eksen, metrik, birim (`FPS`, `°C`, `%`, `MB`, `RPM`) ve missing/null ayrımı doğrudur. Verisi olmayan seri 0 ile tamamlanmaz, uydurma grafik üretilmez.
- [ ] Grafik için **erişilebilir metinsel özet veya veri tablosu** sağlanır; loading/empty/unsupported sensor/timeout/partial response doğru ayrılır. Grafik kütüphanesi varsa onu kullan; sırf grafik için büyük gereksiz yeni paket ekleme.
- [ ] Oyun performans raporları yalnız gerçek manuel veya kaydedilmiş kayıtları gösterir; oyun/donanım profili/çözünürlük/grafik ayarı ve veri kaynağı varsa doğru yerleştirilir. Tahmin ve ölçüm kesinlikle karıştırılmaz.
- [ ] Başka kullanıcı verisi gösterilmez; gerekli backend ownership/visibility denetlenir. Performans aralık/sayfa sınırları taşınan veriyi kontrol altında tutar.
- [ ] TR/EN, tablet/mobilde tablo ve grafik düzeni, klavye/focus, loading/error/empty, testler ve olası Hardware backend regresyonu doğrulanır.
- [ ] `progress/task-02.md` ve `testings/task-02-tests.md` gerçek sonuçlarla yazılır.
- [ ] **CHECKPOINT 2:** Ayrıntılı **2. Conventional Commit başlığı + body**, gerekirse Hardware değişiklik dosyalarıyla birlikte kayıt altına alınır; kısa ara rapor gönderilir; **Git commit atmadan TASK 3'e otomatik geçilir.**

### TASK 3 — LTZ-ALP-PWEB-03 — Uyumluluk, Öneriler ve Veri Gizliliği Ekranları

**Sorumlu:** Alper  
**Servis:** `Next.js / LTZ Performance modülü`  
**Faz:** MVP  
**İlgili gereksinimler:** PWEB-08, PWEB-09, PWEB-10

**Ana task açıklaması:** Oyun uygunluğu/öneri sonuçlarını sun; kişisel performans verilerini görüntüle/sil akışını entegre et.

- [ ] **PWEB-08 — Donanım uyumluluğu:** Kullanıcı bilgisayarı ile oyun sistem gereksinimlerinin karşılaştırılması.
- [ ] **PWEB-09 — Kişiselleştirilmiş öneriler:** Oyun ve donanım önerilerinin görüntülenmesi.
- [ ] **PWEB-10 — Gizlilik ve veri yönetimi:** Kullanıcının saklanan veya ileride senkronize edilecek performans verilerini görüntüleme ve silme işlemleri.

**Bitmiş sayılma:** Yukarıdaki akış API/veri sahipliği kurallarına uygun çalışır, ilgili testler geçer ve ana taskın tek özellik teslimi yapılır.

#### TASK 3 — Uygulama ve kabul kriterleri

- [ ] Uyumluluk sonuçları gerçek Hardware ve Game/Recommendation sözleşmelerine dayanır; minimum/önerilen gereksinim ve desteklenen parçalar gösterilir. Eksik metadata veya doğrulanamayan karşılaştırmada **`bilinmiyor` / `veri yetersiz`** durumu; sahte “uyumlu” sonucu yoktur.
- [ ] Öneriler yalnız Recommendation Service'in gerçek sonucu üzerinden gösterilir; varsa tür/filtre/neden/geri bildirim akışları mevcut gerçek sözleşmeyle entegre edilir; desteklenmeyeni ekleme. FPS tahmini **tahmin** olarak etiketlenir, ölçüm gibi gösterilmez.
- [ ] Kullanıcının kişisel performans verileri ve mevcut gizlilik seçenekleri yetkili uçlardan okunur. Gerçekten desteklenen **kendi verisini silme** eyleminde açık onay, loading/disabled, 401/403/404/409/429/5xx, sonuç kesinleşince başarı ve liste/cache yenileme uygulanır.
- [ ] Silme eyleminde başka kullanıcı verisine erişme/silme mümkün değildir; backend owner kontrolü test edilir. Delete beklenmedik şekilde başarısızsa UI kayıt silinmiş gibi davranmaz.
- [ ] Gelecekteki masaüstü senkronizasyonu için MVP dışı işlev varmış gibi davranılmaz; saklanan/kayıtlı veri ile ileride elde edilebilecek veri net ayrılır.
- [ ] TR/EN, responsive, WCAG, privacy, unit/component/E2E ve ilgili negative security senaryoları doğrulanır.
- [ ] `progress/task-03.md` ve `testings/task-03-tests.md` gerçek sonuçlarla yazılır.
- [ ] **CHECKPOINT 3:** Ayrıntılı **3. Conventional Commit başlığı + body** kaydedilir; ardından **TASK 1–3 genel regresyonu ve MERGE ÖNCESİ FINAL QUALITY GATE** başlatılır.

---

## 5. Frontend kalite / UI / UX standardı

1. Mevcut **LTZ design system**, Tailwind tema değişkenleri, Hardware/Performance ortak görsel dili ve shared UI component'leri kullan. Ayrı renk paleti, ekran başına ham hex, ilgisiz yeni dashboard görünümü, yoğun glow/gradient veya abartılı animasyon üretme. Donanım modülünün karanlık kırmızı/magenta paleti repoda bağlayıcıysa ona uy; bu, reponun mevcut light/dark davranışını bozmak için gerekçe değildir.
2. Tüm kullanıcı metinleri TR/EN çeviri namespace yapısında olmalı. Eksik çeviri anahtarı, tek dil hardcode'u, yanlış tarih/birim yerelleştirmesi bırakma.
3. Semantik başlık/landmark, görünür focus, buton accessible name, label/description, `aria-live` durum mesajları, contrast, reduced-motion, klavye ve dokunmatik kullanım; hedef **WCAG 2.2 AA**.
4. Mobile/tablet/desktop gerçek kullanılabilir: taşan geniş tablo ve grafik yerine responsive düzen, kontrollü yatay scroll veya alternatif tablo; mobilde yalnız hover ile çalışan etkileşim yok.
5. Private dashboard, donanım/performans/gizlilik ekranlarında uygun `noindex` ve kişisel veriyi meta/veri katmanına taşımama kurallarını koru.
6. Loading/skeleton, empty/no sensor, timeout, failed request, 401, 403, 404, 409, 429, 5xx, retry, network offline; tüm ekranları işlevsel hâle getir. Başarıyı backend onayından önce ilan etme.
7. Tablo/listelerde stabil key, deterministik sıralama, gerçek server pagination veya sınırlandırma. Büyük time-series için tüm dataset'i tek client payload'ına zorlamama.
8. Formlarda mevcut schema/validation pattern'ini kullan; backend validation nihai kaynaktır. Silme gibi kritik mutasyonlarda kullanıcı onayı, çift gönderimi önleme, hata sonrası kontrollü tekrar ve güvenli cache invalidation.
9. API tipleri gerçekten dönen DTO ile uyuşsun. Kullanılmayan property ve cast ekleme, `any` ile API uyumsuzluğunu örtme. Tarih/nullable değerleri veri türüne göre doğru işle.
10. **Minimum kod**: Aynı davranış için iki farklı hook, üç mapper, paralel API client, gereksiz global store, genel amaçlı mega-component, yeni state library kurma. Tekrarlanan ama gerçekten ortak olan kodu gerektiğinde ayır; küçük bileşenlerde gereksiz mimari parçalama yapma.
11. Testlerde `getByRole`, kullanıcı davranışı, API state geçişleri, hata, erişilebilirlik ve sahiplik ihlali temelli senaryoları öne çıkar. Snapshot çoğaltmak yerine anlamlı assertions yaz.

---

## 6. Test stratejisi, kalite standardı ve temiz kod kontrolü

### 6.1. Her task sonunda hedefli doğrulama

Aşağıdakilerin **gerçekte var olup olmadığını** `frontend/package.json`, `backend/pom.xml` ve repo scriptlerinden doğrula; olmayan komutu uydurma:

- `npm run lint`
- `npm run typecheck`
- `npm run test` (ve gerekiyorsa ilgili test filtreleri)
- `npm run test:coverage`
- `npm run build`
- `npm run test:e2e` (ilgili rotaları hedefleyebiliyorsan)
- Backend değiştiyse ilgili Maven compile/test/integration/verify ve gerçek PostgreSQL/Flyway kontrolleri
- Her task bitiminde `git diff --check`; eklendiği/değiştirildiği iddia edilen dosyaların varlık kontrolü

**Task checkpoint'lerinde** pahalı tam repo Sonar/pre-push turunu üç kez zorunlu çalıştırma; hızlı ve ilgili testlerle kapsamı izole et. **Üç taskın ardından final pre-merge gate'te pre-push ve SonarQube AYRI AYRI ZORUNLUDUR.** İhtiyaç varsa daha erken çalıştırılabilir.

### 6.2. Code cleanup — son teslimde eksiksiz uygula

- [ ] Kullanılmayan tüm importlar temiz; TypeScript/ESLint unused import/variable hatası yok.
- [ ] Boş class/function/component, unutulmuş placeholder/TODO, unreachable branch, debug/console log, gereksiz yorum satırı, gereksiz disable/cast yok.
- [ ] Yeni eklenen `any`, `ts-ignore`, `eslint-disable`, `as unknown as` zorunlu değilse kaldırıldı; zorunluysa net ve dar gerekçe belgelendi.
- [ ] Gereksiz `useEffect` ile türetilmiş state, gereksiz rerender, duplicate fetch veya fetch loop yok.
- [ ] Mevcut `apiRequest`, shared component, i18n, tema token'ı tekrar kullanıldı; gereksiz bağımlılık yok.
- [ ] API ve UI veri tipi, nullability, timezone/birim ve error response sözleşmesi tutarlı.
- [ ] 401/403/404/409/429/5xx, empty, loading, privacy ve malicious input testleri anlamlı.
- [ ] Gereksiz dosya/sahte veri/migration yok; üç task dışına çıkılmadı.
- [ ] Production code'a test geçsin diye `permitAll`, dummy user, fake sensör, backend bypass veya sabit response eklenmedi.
- [ ] Yalnız ilgili dosyalar değiştirildi, kullanıcının var olan yerel değişiklikleri korunuyor.

### 6.3. Backend değişikliği varsa

- DTO/request-response, validation, exception `ProblemDetail`, authorization/ownership, servis sözleşmesi ve ilgili unit/integration testleri birlikte ele alınır.
- Postgres/DB/JPA/Flyway kullanılıyorsa gerçek davranış test edilir; JPA `ddl-auto=validate` ve uygulanan migration güvenliği korunur.
- Backend kapsamı için repo politikasındaki **JaCoCo satır kapsamı ≥ %80** kapısı geçerli; coverage hesabı ölçülür, yapay biçimde yükseltilmez.
- Her backend dosyası **hangi PWEB ihtiyacına** bağlı olduğunu raporda belirtir; performans/recommendation başka alanlarını refactor etme.

---

## 7. FINAL QUALITY GATE — MERGE ÖNCESİ ZORUNLU

Bu bölüm **yalnız TASK 1–3 checkpoint'leri tamamlandıktan sonra** çalıştırılır. Sonuçları `testings/pre-merge-quality.md` içine kaydet. **`pre-push` ve SonarQube farklı araçlardır; birbirinin yerine geçmez.** Her gerçek hata düzeltmesinden sonra etkilenen kontrolleri yeniden çalıştır ve son durumu kaydet.

### 7.1. Ön koşullar

1. `pre-push/` ve `sonarqube/` dokümanlarını ve script içeriğini incele; **gerçek desteklenen komutları** seç. Pre-push Git push değildir. Script yan etkilerini (ortam değiştirme, secret yazdırma, Git işlemi, volume silme) doğrula; risk varsa onay almadan çalıştırma.
2. JDK, Maven, Node, npm, Docker, PostgreSQL ve SonarQube erişilebilirliğini **gerçek araç çıktılarıyla** kontrol et. `.env` veya secret okumak yasaktır.
3. Güncel çalışılan dal, diff, önceki değişiklikler, test çıktılarının hangi çalışma ağacına ait olduğu kayda geçsin.

### 7.2. Pre-push — ZORUNLU

- Repo Windows ortamındaysa uygun mod için örnek: `./pre-push/pre-push.cmd full dev` veya PowerShell'de ` .\pre-push\pre-push.cmd full dev`.
- Linux/macOS için uygun ise örnek: `sh ./pre-push/pre-push.sh full dev`.
- Bunlar yalnız **başlangıç örneğidir**. Scriptin gerçek argüman/sözleşmesini inceleyip **gerçek full frontend + gerektiğinde backend regresyonunu** kapsayan modu çalıştır. `service`/`backend`/`full` modunun kapsamını kanıt olmadan tahmin etme.
- Komutu, exit code'u, geçen/başarısız test sayısını, lint/typecheck/build/coverage sonucunu raporla.
- FAIL ise nedenini teşhis et; **bu işten kaynaklanan kod/config/test sorunlarını düzelt** ve yeniden çalıştır. Sorun ortam veya dış servis kaynaklıysa `BLOCKED` yaz; PASS uydurma.
- Kullanıcının var olan hatalarını, bu çalışmanın regresyonlarından ayrı listele. İlgisiz büyük çaplı refactor yapma.

### 7.3. SonarQube — AYRI ve ZORUNLU

- `sonarqube/` scriptlerini ve kullanım rehberini oku; token/secret yazdırmadan mevcut izinli analiz akışını kullan.
- Analiz proje/branch kapsamını ve güncel çalışma ağacını yansıtsın; frontend ve bu tasklarda değişen backend modülleri kapsamda olsun. Yalnız analiz başlatıldı diye Quality Gate PASS kabul etme.
- **Quality Gate** ve varsa Security, Reliability, Maintainability notları için **A hedefini** kontrol et. Security Hotspots, Vulnerabilities, Bugs, Code Smells, Duplications ve yeni kod sorunlarını özellikle incele.
- Analiz çalışıp sonuç `PENDING` ise bekleyebiliyorsan repo araçlarıyla sonucu doğrula; doğrulanamadıysa `PENDING` yaz. Sunucu/token yoksa `BLOCKED/NOT RUN` kaydet, A/PASS uydurma.
- FAIL / B-C-D-E derecesi / HIGH-CRITICAL bulgu varsa kapsamla ilgili gerçek sorunu düzelt, ilgili testleri tekrar koş ve mümkünse analizi yeniden çalıştır. Sağlam kodu not almak için `sonar.exclusions` ile saklama, Quality Gate'i düşürme, güvenlik kontrollerini gevşetme.
- **Pre-push PASS + SonarQube FAIL/BLOCKED → merge'e hazır değildir.** Bu durumu açıkça belirt.

### 7.4. Quality Gate rapor tablosu

| Kontrol | Kanıt/komut | Gerçek sonuç | Durum |
| --- | --- | --- | --- |
| 3 PDA task / 10 alt task | Checklist/test referansı | X/10 | DONE/PARTIAL/BLOCKED |
| `git diff --check` | Gerçek komut | Çıktı | PASS/FAIL |
| Frontend lint | npm scripti | Çıktı | PASS/FAIL/NOT RUN |
| Frontend TypeScript | npm scripti | Çıktı | PASS/FAIL/NOT RUN |
| Vitest/unit/component | npm scripti | Test sayısı/coverage | PASS/FAIL/NOT RUN |
| Next build | npm scripti | Çıktı | PASS/FAIL/NOT RUN |
| Playwright E2E | npm scripti | Test sayısı | PASS/FAIL/BLOCKED |
| Backend test/Flyway (değişmişse) | Maven/Testcontainers | Test/coverage | PASS/FAIL/BLOCKED/N/A |
| **pre-push full** | Repo betiği | Exit code | PASS/FAIL/BLOCKED |
| **SonarQube scan** | Repo betiği | Analiz kimliği/branch | PASS/FAIL/BLOCKED/PENDING |
| **Sonar Quality Gate** | Gerçek server sonucu | Sonuç | PASS/FAIL/BLOCKED/PENDING |
| Sonar Security | Gerçek derece | A/B/C/D/E | PASS/FAIL/UNKNOWN |
| Sonar Reliability | Gerçek derece | A/B/C/D/E | PASS/FAIL/UNKNOWN |
| Sonar Maintainability | Gerçek derece | A/B/C/D/E | PASS/FAIL/UNKNOWN |
| Secrets/Auth/ownership | Kod + test + diff inceleme | Kanıt | PASS/FAIL/BLOCKED |
| Boş import/ölü kod/format | TypeScript/ESLint/diff | Kanıt | PASS/FAIL |

- Bir araca erişilememesi sonucu `NOT RUN/BLOCKED` olur, **PASS olmaz**. Düzeltilemeyen risk varsa PR taslağına `NOT READY FOR MERGE` notu ekle.
- Her yeni düzeltmenin üç taskın özelliklerini bozmadığından emin olmak için son regresyonu tekrarla.

---

## 8. Her ana task sonunda commit mesajı standardı — 3 AYRI TASLAK

**Gerçek commit atma.** Her taskın commit taslağını `docs-performance/performance-web/reports/commit-messages.md` dosyasına anında kaydet ve çalışmanın ara ilerleme mesajında göster. Final yanıtta üç metnin **tamamını** yeniden ve ayrı ayrı sun.

### 8.1. Başlıklar (tasklara ait, sonucu görmeden başarı iddia etmeyen taslak kalıplar)

```text
feat(performance-web): implement PWEB-01–03 landing and device management
feat(performance-web): implement PWEB-04–07 hardware and performance dashboard
feat(performance-web): implement PWEB-08–10 compatibility, recommendations and privacy
```

> Not: Başlıkları gerçek tamamlanma kapsamına göre düzelt; task yalnız kısmi kaldıysa tüm PWEB kodlarını yapılmış gibi başlığa yazma. Conventional Commit `feat`/`fix`/`refactor` tiplerini gerçek değişikliğe göre kullan. Her task **bir ayrı önerilen commit** olacak; agent Git'i değiştirmeyecek.

### 8.2. Her bir commit'in BODY formatı

```text
feat(performance-web): <taska özel İngilizce kısa başlık>

- PWEB-XX: <gerçekten geliştirilmiş ekran/akış/özellik>
- PWEB-YY: <gerçekten geliştirilmiş ekran/akış/özellik>
- Entegrasyon: <somut gerçek API'ler, Auth/HW/PERF/REC durumu>
- Güvenlik: <auth/CSRF/ownership/privacy gibi doğrulanmış kontroller>
- UI/UX: <i18n, responsive, accessibility, empty/error durumları>
- Testler: <gerçek komutlar, test sayıları ve sonuçları>
- Dokümantasyon: <güncellenen ilgili belgeler>
```

- Body **Türkçe** ve teknik olarak detaylı; başlık kısa, anlaşılır ve Conventional Commit biçiminde. Gerçekte yapılmayan bir şeyi başlığa veya body'ye yazma.
- **Task 1**, PWEB-01/02/03 ve bağlı cihaz/Auth durumu; **Task 2**, PWEB-04/05/06/07 ve gerekiyorsa Hardware API; **Task 3**, PWEB-08/09/10 ve Recommendation/silme/gizlilik güvenliği odaklı olsun.
- 1. commit'in detaylarını 2.'ye, 2.'nin detaylarını 3.'ye taşımadan bütün değişiklikleri checkpoint dosyalarıyla ilişkilendir. `git diff` ile mümkün olduğunda taska ait değişiklikleri ayır.
- Test doğrulaması eksikse 'geçti' yazma; `NOT RUN/BLOCKED` notunu commit içeriğine değil ayrı teslim raporuna da açıkça koy. Commit mesajı olmayan feature'ı övmesin.
- Commit body'ye `Co-Authored-By`, `Signed-off-by`, AI/agent kredisi, sahte SHA, sahte PR/merge referansı koyma.

---

## 9. Üç task bittiğinde PR TITLE + PR DESCRIPTION

**Son kalite kapısını çalıştırdıktan sonra** aşağıdaki iki metni, gerçek diff ve test sonuçlarına göre hazırla. `reports/pr-description.md` içine tam kopyalanabilir biçimde yaz. **PR oluşturma, merge etme veya push yapma; yalnız taslak hazırla.**

### 9.1. PR Title

Örnek başlık (ancak 10/10 tamamlandıysa):

```text
feat(performance-web): complete LTZ Performance Web MVP (PWEB-01–10)
```

Kısmi/bloke ise `complete` veya `ready` deme; gerçek kapsamı yansıtan dürüst bir başlık kullan.

### 9.2. PR Description — zorunlu içerik

Aşağıdaki düzeni koru; yer tutucuları **gerçek uygulama ve test kanıtlarıyla** doldur:

```markdown
## Summary
<LTZ Performance Web'in hangi gerçek özelliklerinin teslim edildiğinin kısa özeti>

## Scope / PDA Tasks
- [ ] LTZ-ALP-PWEB-01 — Performance Tanıtım ve Cihaz Yönetimi Arayüzü (PWEB-01–03)
- [ ] LTZ-ALP-PWEB-02 — Donanım ve Performans Paneli (PWEB-04–07)
- [ ] LTZ-ALP-PWEB-03 — Uyumluluk, Öneriler ve Veri Gizliliği Ekranları (PWEB-08–10)

## Implemented Changes
### Task 1
<gerçek ekranlar, API entegrasyonu, davranış ve dosyalar>
### Task 2
<donanım, geçmiş, grafik, rapor; varsa Hardware Service uyarlamaları>
### Task 3
<uyumluluk, öneri, güvenli görüntüleme/silme>

## Service Integrations
- Auth Service: <mevcut sözleşme, eklenen request dosyaları, kalan bağımlılıklar>
- Hardware Service: <gerçek DTO/API/kod değişiklikleri>
- Performance Service: <gerçek kullanılan uçlar ve varsa uyarlama>
- Recommendation / Game Service: <gerçek veri sözleşmesi ve eksikler>

## Security, Privacy & Accessibility
<gerçekten doğrulanmış auth/CSRF/ownership/IDOR, no fake measurement,
WCAG, klavye, i18n, responsive ve a11y kararları>

## Validation
| Check | Result | Evidence |
| --- | --- | --- |
| Lint / TypeScript / Build | <PASS/FAIL/BLOCKED> | <komut/kanıt> |
| Unit / Component / E2E | <PASS/FAIL/BLOCKED> | <sayılar> |
| Backend tests (if changed) | <PASS/FAIL/BLOCKED/N/A> | <kanıt> |
| Pre-push | <PASS/FAIL/BLOCKED> | <tam komut/exit code> |
| SonarQube scan / Quality Gate | <PASS/FAIL/BLOCKED/PENDING> | <proje/branch/sonuç> |
| Sonar Security / Reliability / Maintainability | <gerçek notlar> | <kanıt> |

## Risks / Known Limitations
<gerçek kalan bağımlılıklar, Auth talepleri, ortam blokajı, MVP sonrası desktop>

## Review / Merge Checklist
- [ ] 10 PWEB gereksinimi kod + test + belge olarak doğrulandı
- [ ] Kullanıcı sahipliği, Auth ve kişisel veri güvenliği doğrulandı
- [ ] Pre-push başarıyla geçti
- [ ] SonarQube Quality Gate PASS ve A hedefleri doğrulandı
- [ ] Üç taska ait commit mesajları kullanıcıya sunuldu
- [ ] Uygulanmamış backend/desktop/ölçüm özellikleri yapılmış gibi gösterilmedi

**Merge readiness:** READY / NOT READY — <kanıt ve gerekçe>
```

- Son PR açıklaması ne kısa ve yüzeysel, ne de hayali başarılarla dolu olmalı. Reviewer herhangi bir PWEB maddesinin nerede uygulandığını ve nasıl test edildiğini görebilsin.
- Eğer SonarQube/pre-push **FAIL/BLOCKED/PENDING** ise `READY` veya “merge edilebilir” yazma; PR metnini yine hazırla ama **NOT READY FOR MERGE** olarak işaretle ve eksik işi açıkça listele.
- Merge **kullanıcının manuel karar ve eylemidir**. Bu prompt otomatik merge izni vermez.

---

## 10. Zorunlu test/rapor formatı ve final teslim

### 10.1. Task başına checkpoint raporu

Her TASK sonu `progress/task-0X.md` içinde şunları gerçek sonuçla kaydet:

1. TASK kodu, `DONE / PARTIAL / BLOCKED`, alt task checklist'i (X/Y).
2. Gerçekte eklenen/değişen ekranlar, dosyalar, HTTP endpointleri ve gerekli backend uyarlamaları.
3. Auth Service entegrasyonu: kullanılan sözleşme, gerekiyorsa `docs-performance/integrtion/auth/performance-web-requests/` altındaki istek dosyaları ve açık eksik.
4. Test komutları, başarı/başarısızlık sayısı, TypeScript/lint ve `git diff --check` kanıtı.
5. Kalan teknik borç/erişilemeyen servis/güvenlik riski; sahte DONE yok.
6. **O TASKIN TAM CONVENTIONAL COMMIT BAŞLIĞI + DETAYLI BODY'Sİ.**
7. 'Commit/push yapılmadı; sıradaki taska otomatik devam ediyorum.' (Task 3'te 'final quality gate'e geçiyorum.').

### 10.2. Final rapor (tek chat sonu)

Şu sırayla cevap ver:

1. **Genel sonuç:** `DONE / PARTIAL / BLOCKED`, X/10 PWEB, 3 task ayrı durum.
2. **Task 1 teslimi:** PWEB-01–03 ve **1. ayrıntılı commit mesajı**.
3. **Task 2 teslimi:** PWEB-04–07 ve **2. ayrıntılı commit mesajı**.
4. **Task 3 teslimi:** PWEB-08–10 ve **3. ayrıntılı commit mesajı**.
5. **Auth integration/request durumu:** açılan dosyaların tam yolları veya 'talep gerekmedi'.
6. **Hardware/Performance/Recommendation entegrasyonları:** gerçek değişen dosyalar, API/DTO/DB testleri ve varsa blokajlar.
7. **Merge öncesi kalite:** lint, typecheck, unit/component, build, e2e, backend, pre-push, **SonarQube scan ve gerçek Quality Gate** ayrı ayrı; yanlış PASS yok.
8. **Önerilen PR Title** — kopyalanabilir tek satır.
9. **Önerilen PR Description** — GitHub'a yapıştırılabilir tam Markdown metni.
10. **Merge kararı önerisi:** `READY` veya `NOT READY`; eksikleri ve manuel takip adımları.
11. **Git özeti:** 'Git add/commit/push/merge yapılmadı. Üç ayrı commit mesajı ve tek PR taslağı hazırlandı.'

### 10.3. Final kabul kontrol listesi

- [ ] `AGENTS.md`, `CLAUDE.md`, `SECURITY.md`, `web-rules.md` ve gerçek kod sözleşmeleri okundu.
- [ ] PWEB-01, 02, 03 gerçek ve testli; Checkpoint 1 + detaylı commit taslağı mevcut.
- [ ] PWEB-04, 05, 06, 07 gerçek ve testli; Checkpoint 2 + detaylı commit taslağı mevcut.
- [ ] PWEB-08, 09, 10 gerçek ve testli; Checkpoint 3 + detaylı commit taslağı mevcut.
- [ ] 10 PWEB maddesi dürüst statü ve kanıtla tek checklist içinde.
- [ ] Mevcut Auth entegrasyonu doğrulandı; yeni ihtiyaç varsa **tam istenen path** altında request dosyası açıldı.
- [ ] Hardware Service gerekirse dar kapsamlı ve testli uyum değişikliğini aldı.
- [ ] Performance/Recommendation servisleri sözleşmeyle bağlandı; uydurma veri yok.
- [ ] Frontend TR/EN, responsive, WCAG, privacy, noindex, empty/loading/error/disabled tamam.
- [ ] Unused import, dead code, debug, gereksiz paket/abstraction temizlendi.
- [ ] Değişen backend için güvenlik, test, migration ve doküman tutarlılığı sağlandı.
- [ ] Final **pre-push** gerçekten çalıştırıldı, başarısızlıklar düzeltildi veya **BLOCKED** raporlandı.
- [ ] Final **SonarQube** gerçekten çalıştırıldı, Quality Gate ve A hedefleri kanıtlandı veya **BLOCKED/PENDING** raporlandı.
- [ ] Üç ayrı somut commit önerisi + GitHub PR Title + ayrıntılı PR Description verildi.
- [ ] Kullanıcının izni olmadan Git yazma, push, PR açma, merge, secret okuma, destructive DB işlemi yapılmadı.

---

## 11. ŞİMDİ BAŞLA — Codex'in ilk uygulayacağı talimat

1. İlk iş olarak `AGENTS.md`, `CLAUDE.md` ve ilgili talimatları oku; sonrasında proje güvenliği, web kuralları, kalite scriptleri ve servis sözleşmelerini denetle.
2. Aktif Git branch ve çalışma ağacını **salt okunur** kontrol et; kullanıcı değişikliklerine zarar verme.
3. Üç task/10 alt task checklist'ini `docs-performance/performance-web/performance-web-master-checklist.md` içine yerleştir; dokümanlar varsa uygun şekilde güncelle.
4. Mevcut Auth, Performance, Hardware, Recommendation, Game ve Gateway API'lerini gerçekten incele; gerekli dar kapsamlı entegrasyon/eksik request haritasını çıkar.
5. **TASK 1'i yap, test et, checkpoint/commit mesajını hazırla; sorulacak bir şey yoksa TASK 2'ye otomatik geç.**
6. **TASK 2'yi yap, test et, checkpoint/commit mesajını hazırla; sorulacak bir şey yoksa TASK 3'e otomatik geç.**
7. **TASK 3'ü yap, test et, checkpoint/commit mesajını hazırla.**
8. **Üçü bitince** full frontend/backend kapsamlı kalite kontrolünü; **ayrı pre-push ve SonarQube** analizini çalıştır, somut hataları düzelt ve tekrar doğrula.
9. Tamamlanan 3 taska ait 3 detaylı commit taslağını ve final PR title/description'ı raporla. Sonuçlar kanıtlı değilse dürüst `PARTIAL/BLOCKED/NOT READY` yaz.
10. **Hiçbir Git commit, push, PR oluşturma veya merge yapma.** Bütün süreç tek chat içinde tamamlanacak; normal checkpoint'lerde kullanıcıdan `devam` isteme.

**Son hedef:** LTZ Performance Web için üç kapsamı birbiriyle karışmayan, gerçek API'lere entegre, güvenli, erişilebilir ve test edilmiş özellik teslimi; her biri için ayrı **profesyonel detaylı commit mesajı** ve gerçek **merge öncesi SonarQube + pre-push** kanıtlarıyla hazırlanmış **PR title + PR description**. Kullanıcının Git kontrolü korunur.
