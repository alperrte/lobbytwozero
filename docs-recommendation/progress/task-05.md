# TASK 5 / LTZ-ALP-REC-05 — checkpoint ve final durum

2026-11-15 · Alper · **PARTIAL — REC-22 1/1 DONE; final merge kapıları BLOCKED**.

## Gerçek özellik

`POST /api/v1/recommendation/assistant`: assistant-v1 / assistant-rules-v1, tr/en,
GAME/SELECT_GAME, HARDWARE/ASSESS_GAME, LTZ_USAGE/VIEW_HISTORY veya GIVE_FEEDBACK.
Kapalı sözlük, deterministik kararlar ve altı sabit şablon gerçek CPython ile çalışır.
Oyun seçimi rehberi gerçek Game aday listesi üretmez; donanım modu mevcut owner assessment
durumlarını açıklar; eksik girdiler NEEDS_INPUT, doğrulanamayan donanım INSUFFICIENT_DATA'dır.
LLM, serbest metin sohbeti, FPS garantisi veya kullanıcı adına mutasyon yoktur.

Mevcut JWT/rol/rıza/ACTIVE ve generation kotası paylaşılır. Python'a identity/token/ham
profil/snapshot/özel library-rating gönderilmez. Stdin 8 KiB; stdout/stderr ayrı 16 KiB;
2 saniye deadline, en çok 2 process; sürüm/bağlam/şablon/step allowlist doğrulaması.
Hatalar kontrollü ProblemDetail; process/stream/executor ve geçici script kapanışta temizlenir.

## Onay ve değişiklik envanteri

Kullanıcı runtime/Docker/portable indirmeye açık onay verdi; [ADR 0003](../../docs/decisions/0003-recommendation-python-yardimcisi.md).
CPython 3.14.10 ZIP SHA-256 doğrulanarak yalnız `.cache/tools/python/` içine alındı;
sistem Python/PATH/registry değişmedi. Docker'da mevcut image'a python3-minimal ve gerekli
libpython3-stdlib eklendi. Yeni ENV/pip/Maven bağımlılığı, sidecar veya DB/migration yoktur.

Servis `src/main/java/com/ltz/recommendation/` köküne göre yeni production dosyaları:

- `application/port/AssistantEngine.java`
- `application/service/{AssistantQuery,RecommendationAssistantService}.java`
- `api/controller/RecommendationAssistantController.java`
- `api/dto/request/AssistantRequest.java`, `api/dto/response/AssistantResponse.java`
- `infrastructure/client/{PythonAssistantEngine,BoundedPythonProcess,AssistantReplyReader,AssistantPlan}.java`

Diğer yeni dosyalar: `src/main/resources/assistant/assistant_rules.py`,
`src/test/python/test_assistant_rules.py`, Java test/helper sınıfları
`infrastructure/client/{PythonAssistantEngineTest,PythonTestRuntime}.java` ve
`infrastructure/security/PerformanceReferenceFixtureValues.java`.
Mevcut HardwareAssessmentApiIntegrationTest'e dört assistant testi eklendi; security
allowlist'e yalnız assistant yolu eklendi. Dockerfile/klasör yerleşimi, API/mimari,
integration/karar/test/checklist/issues/güvenlik ve commit/PR belgeleri güncellendi.

## Doğrulama

- **80 Java testi + 7 Python testi PASS**, Java failure/error/skipped 0.
- Maven clean verify + disposable PostgreSQL 17.11 / Flyway V11/V12 / gerçek Auth issuer/JWKS.
- Toplam LINE **%97,51** (980 covered / 25 missed), merkezi ek/değişen kod **%97,70** (979/1002).
  Karşılaştırma origin/main'e göre beş aşamanın kodunu kapsar; TASK 5'e özel yüzde değildir.
- Checkstyle/PMD yeni ihlal 0; JAR dependency gate ve final image HIGH/CRITICAL scan PASS.
- Secretsiz dar context Docker build exit 0; non-root/read-only/network-none smoke PASS.
  JAR içindeki Python script SHA-256'sı kaynakla aynı; rehber gerçek Linux Python'da çalıştı.
- Native pre-push ve Sonar giriş noktaları güvenlik korumasıyla denendi, **BLOCKED**.
  Scanner/Quality Gate/A dereceleri doğrulanmadı; CI PASS onların yerine sayılmaz.

[Tüm koşu/hata düzeltme kanıtları](../testings/task-05-tests.md),
[final kalite tablosu](../testings/pre-merge-quality.md),
[kararlar](../decisions/task-05-decisions.md), [runtime yönergesi](../integration/assistant-runtime.md).

## Beş taskın gerçek durumu

| Ana task | Durum | REC kabulü |
| --- | --- | --- |
| LTZ-ALP-REC-01 | DONE (servis içi temel) | 3/3 |
| LTZ-ALP-REC-02 | PARTIAL | 5/9; REC-02/03/05 BLOCKED, REC-16 PARTIAL |
| LTZ-ALP-REC-03 | PARTIAL | 5/7; REC-06/09 PARTIAL |
| LTZ-ALP-REC-04 | BLOCKED | 0/3; REC-13/14/15 |
| LTZ-ALP-REC-05 | PARTIAL (feature DONE, final gate BLOCKED) | REC-22 1/1 |

**Toplam 14/23 DONE (belgelenmiş desteklenen kapsam); 23/23 ve 5/5 tam teslim değildir.**
Game/Review/User/Social/Event ve bağlamlı Performance sözleşme talepleri açıktır; başka
owner koduna/DB'sine müdahale edilmedi. Canlı çok servis E2E NOT VERIFIED.

- [x] REC-22 gerçek Python, üç konu, kapalı DTO/şablon, güvenlik ve negatif testleri.
- [x] Onaylı runtime ve dağıtım etkisi belgelendi/doğrulandı; ENV sırları okunmadı.
- [x] Beşinci commit, PR title/description ve final rapor hazırlandı.
- [x] Whitespace, yeni/güncel belge bağlantıları, 23 kaynak gereksinim metni ve beş commit taslağı kontrolü PASS.
- [x] Gitleaks tracked diff, önceki commit aralığı ve 22 yeni kaynak taraması PASS.
- [ ] Native pre-push PASS — gerçek profil/DB politikası nedeniyle BLOCKED.
- [ ] SonarQube scanner/gate ve A dereceleri — BLOCKED/UNKNOWN.
- [ ] 23/23 REC ve canlı E2E — açık sözleşmeler nedeniyle tamamlanmadı.

Başlangıç worktree temizdi; TASK 4 kullanıcı commit'i `985b2388` görüldü. Önceki commit'ler
kullanıcıya aittir. Ajan stage/commit/push/PR/merge veya Git kimlik işlemi yapmadı.
**Kesin final karar: NOT READY / BLOCKED. PR taslağı DRAFT kalır. DUR.**
