# LTZ — Klasör Yapısı ve Dosya Yerleşim Kuralları

> Son güncelleme: 2026-10-20
> Kapsam: repo kökü, `backend/` (17 iş servisi + api-gateway), `frontend/` (Next.js), `docs/`, ops ve kalite araçları.
> Etiketler: `[MEVCUT]` repoda var, `[ÖNERİ]` henüz yok ama bu düzende eklenir, `[KARAR BEKLİYOR]` ekip kararı gerekir.
> İlgili belgeler: [architecture.md](architecture.md), [database.md](database.md), [decisions/migrations.md](decisions/migrations.md), [SECURITY.md](SECURITY.md), [web-rules.md](web-rules.md).

Bu belge "yeni dosya nereye konur?" sorusunun tek cevabıdır. Burada tanımlı olmayan yeni bir üst seviye klasör açılmadan önce ekip onayı alınır ve bu belge güncellenir.

---

## 1. Repo kökü `[MEVCUT]`

```text
lobbytwozero/
├─ README.md                  # Projeyi tanıtan vitrin sayfası (görseller: docs/assets/readme/)
├─ AGENTS.md                  # Yapay zekâ ajanları için çalışma kuralları
├─ CLAUDE.md                  # Claude Code girişi (AGENTS.md'yi içe aktarır)
├─ backend/                   # Maven multi-module Spring Boot servisleri
├─ frontend/                  # Next.js 16 web uygulaması
├─ docs/                      # Mimari, güvenlik, veri, kural ve gereksinim belgeleri
├─ docs-hardware/             # Hardware görev ilerlemesi, kararlar ve kalite kanıtları
├─ docs-performance/          # Performance görev ilerlemesi, kararlar, Auth entegrasyonu ve kalite kanıtları
├─ postgres/init/             # 01-create-databases.sql (17 DB oluşturma)
├─ ops/                       # env.cmd / env.ps1 / env.sh, env.test.mjs, ops.md
│                             # hardware-auth-e2e.mjs / .test.mjs (izole gerçek HTTP kabulü)
├─ pre-push/                  # Push öncesi kalite kapısı (pre-push.cmd / .sh)
├─ sonarqube/                 # Yerel SonarQube çalıştırma betikleri
├─ lib/                       # profile-env.mjs, quality-tools.mjs (ortak Node yardımcıları)
├─ logs/                      # Yerel çalışma logları (commit edilmez)
├─ test-results/              # Yerel test çıktıları (commit edilmez)
├─ docker-compose.yml         # Postgres + gateway + 17 servis (frontend compose'da değil)
├─ docker-compose.sonar.yml   # SonarQube
├─ .env.example               # Değişken şablonu (gerçek değer içermez)
├─ .env, .env.dev             # GERÇEK değerler — okunmaz, kopyalanmaz (SECURITY.md §1)
├─ .dockerignore, .gitignore, .gitattributes
```

Kurallar:

- `.env`, `.env.dev`, `*.bak` ve `frontend/.env.local` **okunmaz, içeriği hiçbir belgeye/koda aktarılmaz**. Yeni değişken gerekiyorsa yalnız `.env.example` önerilir ve kullanıcı onayı alınır.
- `logs/`, `test-results/`, `.next/`, `target/`, `node_modules/` üretilmiş içeriktir; elle düzenlenmez, commit edilmez.
- Kök dizine yeni betik eklenmez; Node yardımcıları `lib/`, ortam betikleri `ops/`, kalite betikleri `pre-push/` veya `sonarqube/` altına gider.
- CI iş tanımları `.github/workflows/`, ortak Java kuralları `backend/config/`, secretsiz CI test/araç yardımcıları `pre-push/ci/` altındadır. `.mvn/` ortak kural dosyaları için repository kökünü belirler; kurulum ve test sözleşmesi [engineering.md](engineering.md) içindedir.

---

## 2. `docs/` `[MEVCUT]`

```text
docs/
├─ SECURITY.md                # Güvenlik kuralları (bağlayıcı)
├─ api.md                     # Tüm API'lerin servis başlıkları altında kaydı (bağlayıcı)
├─ architecture.md            # Servis topolojisi, iletişim, saga, akışlar
├─ database.md                # DB sahipliği, tablo önerileri, Flyway stratejisi
├─ er-diagram.md              # DB başına Mermaid ER diyagramları
├─ folder-structure.md        # Bu belge
├─ web-rules.md               # Frontend / UX / erişilebilirlik kuralları
├─ assets/
│  ├─ cv/                     # Ekip üyelerinin README'den bağlanan CV'leri (<isim>-<soyisim>-cv.pdf)
│  └─ readme/                 # README görselleri (animasyonlu SVG; dış kaynak içermez)
├─ decisions/
│  ├─ migrations.md           # Flyway sürüm aralıkları (bağlayıcı)
│  └─ NNNN-baslik.md          # [ÖNERİ] Yeni mimari kararlar (ADR)
├─ servis-gereksinimleri/
│  └─ LTZ_Servis_Icerikleri_<Isim>.md   # Alper, Hamza, Kerem, Nisa, Umut — görev listeleri
├─ gereksinimler/
│  └─ LTZ Gereksinim Dokümanı .docx     # Genel gereksinim dokümanı (kısmen eski: MSSQL/Vite/Java 21)
└─ checklist/
   └─ Web_Sitesi_Master_Checklist.md
```

- Yeni karar → `docs/decisions/NNNN-kisa-baslik.md` (ör. `0002-mesaj-broker-secimi.md`). `migrations.md` mevcut istisnadır, adı değiştirilmez.
- Kaynak önceliği: **repo kodu > servis-gereksinimleri > gereksinimler (docx)**.

---

## 3. `backend/` `[MEVCUT]`

```text
backend/
├─ pom.xml                    # Parent POM (groupId com.ltz), Java 25, Spring Boot 4.1.1
└─ services/
   ├─ api-gateway/
   ├─ auth-service/          ├─ user-service/          ├─ mail-service/
   ├─ game-service/          ├─ library-service/       ├─ review-service/
   ├─ social-service/        ├─ messaging-service/     ├─ lfp-service/
   ├─ community-service/     ├─ content-service/       ├─ event-service/
   ├─ hardware-service/      ├─ recommendation-service/├─ performance-service/
   ├─ admin-service/         └─ notification-service/
```

Servis klasör adı her zaman `<ad>-service` biçimindedir; Java base package `com.ltz.<ad>` (ör. `hardware-service` → `com.ltz.hardware`).

### 3.1 İş servisi iskeleti `[MEVCUT]` — 17 serviste aynı

```text
<ad>-service/
├─ Dockerfile
├─ pom.xml
├─ mvnw, mvnw.cmd, .mvn/
├─ HELP.md, .gitignore, .gitattributes
└─ src/
   ├─ main/
   │  ├─ java/com/ltz/<ad>/
   │  │  ├─ <Ad>ServiceApplication.java
   │  │  ├─ api/
   │  │  │  ├─ controller/              # @RestController — yalnız HTTP eşlemesi, iş kuralı yok
   │  │  │  ├─ dto/request/             # Gelen istek record'ları + Bean Validation
   │  │  │  ├─ dto/response/            # Dışarı dönen record'lar (entity asla dönmez)
   │  │  │  └─ exception/               # @RestControllerAdvice → ProblemDetail
   │  │  ├─ application/
   │  │  │  ├─ service/                 # Use-case servisleri, @Transactional sınırı burada
   │  │  │  ├─ mapper/                  # Entity ↔ DTO dönüşümü
   │  │  │  ├─ port/                    # Dış bağımlılık arayüzleri (client, publisher)
   │  │  │  └─ saga/                    # Çok servisli akış orkestrasyonu / telafi adımları
   │  │  ├─ domain/
   │  │  │  ├─ entity/                  # JPA entity'leri (yalnız kendi DB tabloları)
   │  │  │  ├─ enums/                   # Durum/tip enum'ları (DB'de varchar + CHECK)
   │  │  │  ├─ event/                   # Domain event record'ları (outbox payload)
   │  │  │  └─ exception/               # İş kuralı istisnaları
   │  │  └─ infrastructure/
   │  │     ├─ client/                  # Diğer servislere RestClient adaptörleri (port impl.)
   │  │     ├─ client/dto/request/      # Dış servise giden istek DTO'ları
   │  │     ├─ client/dto/response/     # Dış servisten gelen yanıt DTO'ları
   │  │     ├─ config/                  # @Configuration, @ConfigurationProperties
   │  │     ├─ storage/                 # [yalnız dosya depolayan servislerde: user] MediaStoragePort adaptörü, görsel işleme
   │  │     ├─ messaging/outbox/        # outbox_events yazımı + relay
   │  │     ├─ messaging/inbox/         # POST /internal/events alımı, inbox_events dedup
   │  │     ├─ messaging/realtime/      # [yalnız messaging] SSE akış kaydı (ADR 0001)
   │  │     ├─ persistence/repository/  # Spring Data JPA repository'leri
   │  │     └─ security/                # Servis içi güvenlik (kimlik başlığı, rol kontrolü)
   │  └─ resources/
   │     ├─ application.properties      # Yalnız ${ENV:varsayilan} referansları, gizli değer yok
   │     ├─ db/migration/               # Flyway SQL dosyaları (bkz. §4)
   │     ├─ static/
   │     └─ templates/
   └─ test/java/com/ltz/<ad>/
      ├─ <Ad>ServiceApplicationTests.java   # contextLoads
      ├─ api/controller/                    # @WebMvcTest / MockMvc testleri
      ├─ application/service/               # Birim testleri (Mockito)
      ├─ application/saga/                  # Saga adım + telafi testleri
      ├─ infrastructure/client/             # RestClient adaptör testleri (MockRestServiceServer)
      └─ infrastructure/persistence/        # Repository / migration testleri
```

Bağımlılık yönü: `api → application → domain`; `infrastructure` yalnız `application/port` arayüzlerini uygular. `domain` hiçbir katmanı import etmez. Controller doğrudan repository veya client çağırmaz.

### 3.2 api-gateway `[MEVCUT]`

```text
api-gateway/src/main/java/com/ltz/gateway/
├─ ApiGatewayApplication.java
├─ api/exception/
└─ infrastructure/
   ├─ config/         # Route tanımları (/api/v1/<ad>/** → <AD>_SERVICE_URL)
   ├─ filter/         # [ÖNERİ] correlation-id, rate limit, kimlik başlığı aktarımı
   └─ security/       # [ÖNERİ] JWT doğrulama, CORS
```

Gateway'de iş kuralı, entity veya veritabanı bulunmaz.

### 3.3 Dosya adlandırma

| Tür | Konum | Ad örneği |
| --- | --- | --- |
| Controller | `api/controller` | `HardwareProfileController` |
| İstek / yanıt DTO | `api/dto/request`, `api/dto/response` | `CreateHardwareProfileRequest`, `HardwareProfileResponse` |
| Servis | `application/service` | `HardwareProfileService` |
| Port | `application/port` | `GameCatalogPort` |
| Client adaptörü | `infrastructure/client` | `GameServiceClient` |
| Entity | `domain/entity` | `HardwareProfile` |
| Repository | `infrastructure/persistence/repository` | `HardwareProfileRepository` |
| Domain event | `domain/event` | `HardwareProfileCreatedEvent` |
| Test | aynı paket yolu, `test/` altında | `HardwareProfileServiceTest` |

---

## 4. Flyway migration yerleşimi `[MEVCUT klasör]`

Migration yazmadan önce **[decisions/migrations.md](decisions/migrations.md) okunması zorunludur**.

- Konum: `backend/services/<ad>-service/src/main/resources/db/migration/`
- Ad: `V{NUMARA}__{aciklama}.sql` — açıklama küçük harf, `_` ile ayrılır.
- Her servis yalnız kendi aralığını kullanır:

| Servis | Aralık | İlk dosya örneği |
| --- | --- | --- |
| hardware | V1–V10 | `V1__create_hardware_tables.sql` |
| recommendation | V11–V20 | `V11__create_recommendation_tables.sql` |
| performance | V21–V30 | `V21__create_performance_tables.sql` |
| game | V31–V40 | `V31__create_game_tables.sql` |
| library | V41–V50 | `V41__create_library_tables.sql` |
| review | V51–V60 | `V51__create_review_tables.sql` |
| auth | V61–V70 | `V61__create_auth_tables.sql` |
| user | V71–V80 | `V71__create_user_tables.sql` |
| mail | V81–V90 | `V81__create_mail_tables.sql` |
| community | V91–V100 | `V91__create_community_tables.sql` |
| content | V101–V110 | `V101__create_content_tables.sql` |
| event | V111–V120 | `V111__create_event_tables.sql` |
| social | V121–V130 | `V121__create_social_tables.sql` |
| messaging | V131–V140 | `V131__create_messaging_tables.sql` |
| lfp | V141–V150 | `V141__create_lfp_tables.sql` |
| notification | V151–V160 | `V151__create_notification_tables.sql` |
| admin | V161–V170 | `V161__create_admin_tables.sql` |

- Uygulanmış migration dosyası değiştirilmez; düzeltme yeni numarayla yapılır.
- Bir servisin migration'ı başka servisin DB'sine dokunmaz.
- Test verisi / seed migration klasörüne konmaz; testlerde `@Sql` veya test builder'ları kullanılır.

---

## 5. `frontend/` `[MEVCUT]`

```text
frontend/
├─ package.json, package-lock.json
├─ next.config.ts, tsconfig.json, eslint.config.mjs
├─ vitest.config.mts, playwright.config.ts
├─ .env.example               # Yalnız NEXT_PUBLIC_API_BASE_URL
├─ .env.local                 # GERÇEK değer — okunmaz
├─ public/                    # Statik varlıklar (public/assets/…)
├─ test/setup.ts              # Vitest ortak kurulum
├─ e2e/                       # Playwright senaryoları (ör. home.spec.ts)
└─ src/
   ├─ app/                    # Yalnız Next.js App Router: sayfa, layout, route group
   │  ├─ (public)/            # Anonim sayfalar: ana sayfa, oyun detay, topluluk vitrini
   │  ├─ (auth)/              # Giriş, kayıt, doğrulama kodu, şifre sıfırlama
   │  ├─ (dashboard)/         # Oturum gerektiren kullanıcı alanı
   │  ├─ (admin)/             # Yönetim paneli (ADMIN rolü)
   │  ├─ layout.tsx, page.tsx, globals.css
   │  └─ error.tsx, loading.tsx, not-found.tsx
   ├─ features/<ozellik>/     # Özellik bazlı modüller (bkz. §5.1)
   └─ shared/                 # Özellikten bağımsız ortak kod (bkz. §5.2)
```

### 5.1 Özellik modülü `[MEVCUT iskelet]`

Özellikler: `admin, auth, community, content, event, game, hardware, lfp, library, messaging, notification, performance, recommendation, review, social, user` (backend servisleriyle birebir; `mail` UI'sı yoktur).

```text
src/features/<ozellik>/
├─ components/   # Özelliğe ait UI bileşenleri — API çağrısı YAPMAZ
├─ hooks/        # Veri çekme / mutasyon hook'ları — services'i çağırır
├─ schemas/      # Zod form ve yanıt şemaları
├─ services/     # Endpoint fonksiyonları — yalnız shared/lib/api/client.ts (apiRequest) kullanır
└─ types/        # Özelliğe ait TypeScript tipleri
```

Akış: `app/.../page.tsx → features/<x>/components → hooks → services → shared/lib/api/client.ts → gateway /api/v1/<ad>/**`.

- Bir özellik başka bir özelliğin `components/` veya `services/` iç dosyasını import etmez; paylaşılması gereken kod `shared/` altına taşınır.
- Testler kaynak dosyanın yanına konur: `use-hardware-profiles.test.ts`, `hardware-profile-form.test.tsx`.
- Dosya adları kebab-case, bileşen adları PascalCase'dir.

### 5.2 `shared/` `[MEVCUT]`

```text
src/shared/
├─ components/
│  ├─ layout/                 # Header, footer, sidebar, sayfa kabukları
│  └─ ui/                     # Genel UI parçaları (language-switcher.tsx vb.)
├─ config/                    # Uygulama sabit yapılandırması
├─ constants/                 # Route adları, limitler, sabit listeler
├─ hooks/                     # Özellikten bağımsız hook'lar
├─ lib/
│  ├─ api/client.ts           # apiRequest — tek HTTP giriş noktası (+ client.test.ts)
│  └─ i18n/                   # config.ts, request.ts, actions.ts, messages/{tr,en}.json
├─ providers/                 # React context sağlayıcıları
├─ types/                     # Ortak tipler (ProblemDetail, sayfalama vb.)
└─ utils/                     # Saf yardımcı fonksiyonlar
```

- Kullanıcıya görünen her metin `messages/tr.json` ve `messages/en.json` içine birlikte eklenir; bileşende sabit metin yazılmaz.

---

## 6. Ops ve kalite araçları `[MEVCUT]`

| Klasör | İçerik | Kullanım |
| --- | --- | --- |
| `ops/` | `env.cmd`, `env.ps1`, `env.sh`, `env.test.mjs`, `ops.md` | Profil ortam değişkenlerini yükler; ajanlar bu betiklerin çıktısını okumaz/loglamaz |
| `pre-push/` | `pre-push.cmd/.sh/.md`, `backend-check.mjs`, `menu.mjs`, `quality.mjs`, `runtime-checks.mjs` (+ testler) | `.\pre-push\pre-push.cmd service|backend|full dev|full prod` — başarı çıktısı `SAFE TO COMMIT` |
| `sonarqube/` | `sonar.cmd/.mjs/.sh`, `sonar.test.mjs`, `sonarqube.md` | Yerel SonarQube analizi (port 5199) |
| `lib/` | `profile-env.mjs`, `quality-tools.mjs` | Yukarıdaki betiklerin ortak kodu |
| `postgres/init/` | `01-create-databases.sql` | İlk açılışta 17 DB'yi oluşturur; tablo DDL'i buraya **konmaz** (Flyway'e aittir) |

---

## 7. Yeni şey eklerken hızlı tablo

Hardware'e özel rapor kökü `docs-hardware/` dizinidir (LTZ_HARDWARE_SERVICE.md ile onaylı).
`README.md` indeksi; `progress/` çalışma günlüğünü, `decisions/` açık/onaylı kararları,
`testing/` gerçek kalite kanıtlarını, `reports/` yalnız doğrulanmış teslimleri içerir.
Kullanıcı onaylı Hardware–Auth entegrasyon planı ve kanıtları `docs-hardware/integration/auth/`,
Auth sahibine iletilecek eksik sözleşme kayıtları bunun `auth-service-talepleri/` altındadır.
Durum otoritesi kökteki `LTZ_HARDWARE_SERVICE.md` checklist/checkpoint alanıdır;
merkezi API sözleşmesi `docs/api.md` içinde kalır.

Performance'a özel rapor kökü `docs-performance/` dizinidir (kaynak: `LTZ_Performance_Service_Master_Prompt.md`).
`progress/` faz ilerlemesini ve `performance-master-checklist.md` PDA kontrol listesini, `decisions/` faz kararlarını,
`testings/` gerçek kalite kanıtlarını (PASS/FAIL/BLOCKED/NOT RUN), `reports/` sorun kaydı ve nihai raporu içerir.
Auth entegrasyon kanıtları `integration/auth/`, Auth sahibine iletilecek talepler `integration/auth-service-talepleri/` altındadır.
Merkezi API sözleşmesi `docs/api.md` içinde kalır.

| Eklenecek | Konum |
| --- | --- |
| Yeni REST endpoint | `<ad>-service/.../api/controller` + `api/dto/*` + `application/service` + kayıt: `docs/api.md` |
| Başka servise senkron çağrı | `application/port` arayüzü + `infrastructure/client` adaptörü |
| Asenkron olay | `domain/event` + `infrastructure/messaging/outbox` (yayın), `.../inbox` (alım) |
| Yeni tablo / kolon | `src/main/resources/db/migration/V{n}__*.sql` (servisin aralığında) |
| Gateway route | `api-gateway/.../infrastructure/config` + `.env.example` önerisi (onaylı) |
| Yeni sayfa | `frontend/src/app/(<grup>)/<yol>/page.tsx` |
| Özellik UI / veri | `frontend/src/features/<ozellik>/{components,hooks,services,schemas,types}` |
| Ortak UI parçası | `frontend/src/shared/components/ui` |
| Çeviri metni | `frontend/src/shared/lib/i18n/messages/{tr,en}.json` |
| Mimari karar | `docs/decisions/NNNN-baslik.md` |
| E2E senaryo | `frontend/e2e/<ozellik>.spec.ts` |

---

## 8. Yasaklar

- Servis klasörleri arasında kaynak kod paylaşımı (`../other-service`) yapılmaz; ortak sözleşme DTO'su her serviste kendi `client/dto` paketinde tanımlanır. Ortak kütüphane modülü gerekiyorsa `[KARAR BEKLİYOR]` → ADR.
- `src/app/` içinde API çağrısı, iş kuralı veya `fetch` bulunmaz.
- `postgres/init/` içinde tablo oluşturulmaz.
- Gizli değer içeren dosya (`.env*`, sertifika, anahtar) repoya eklenmez.
- Üretilmiş klasörler (`target/`, `.next/`, `node_modules/`, `logs/`, `test-results/`) elle düzenlenmez.
