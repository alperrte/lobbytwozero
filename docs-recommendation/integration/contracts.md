# Başlangıç gerçek sözleşme ve izin matrisi

2026-11-14 · branch: `feature/recommendation-service` · Sorumlu: Alper.
Bu inceleme TASK 1 başlangıç envanteridir; diğer taskların uygulanması veya E2E kabulü değildir.
Başlangıç worktree yalnız izlenmeyen `docs-recommendation/` içeriyordu; mevcut master korunmuştur.
Recommendation kaynakları yalnız launcher/context testi, mevcut starter'lar ve application.properties idi.
Java 25 / Boot 4.1.1 kodla doğrulandı. PATH'teki Java 21 yerine kurulu JDK 25 açık seçildi.

| Veri sahibi | Gerçek kod/sözleşme | Durum ve Recommendation tüketimi | Gerekli sınır |
| --- | --- | --- | --- |
| Auth / Kerem | `docs-auth/servis-sozlesmeleri.md`, `TokenClaims`, JWKS ve servis token issuer; Recommendation→Library `signals:read`, →Performance `reference:read` hesaba bağlı izinleri var | Kaynak MEVCUT; Recommendation JWT/service-token adaptörü EKSİK; TASK 1 HTTP ucu açmaz | RS256, typ/iss/aud/zaman/sub; aktif oturum/iptal politikası; user token ve svc token ayrımı |
| User / Kerem | `MeController`, `ConsentController`; kendi profilinde favoriteGenres/favoritePlatforms ve ayrı recommendation rızası | Kaynak MEVCUT; tüketici EKSİK | Doğrulanmış kullanıcı token'ı, yalnız kendi alanları; rıza geri çekmede işlemeyi durdurma |
| Game / Hamza | `GameBatchController` POST `/api/v1/game/games/batch`; keşif/detay DTO'ları; kanonik UUID ve sistem gereksinimleri kaynakta | Kaynak MEVCUT; tüketici EKSİK | Batch sınırı, pagination, schema doğrulaması; metadata bilinmiyorsa tahmin üretmeme |
| Library / Hamza | `LibraryInternalReadController` GET `/internal/v1/library/users/{userId}/recommendation-signals`; `LibrarySignalPageResponse(items,page,size,totalElements)`; item(gameId,favorite,completed) | MEVCUT; tüketici EKSİK | svc:recommendation-service, account_id=yol, signals:read; 1–50 size, consent kontrolü, sınırlı toplam sayfa |
| Review / Hamza | Kendi oyun puanı HTTP ucu ve `InternalRatingSummaryController` `/internal/v1/games/rating-summaries` | KISMİ: oyun geneli summary, kullanıcının bütün puanlarını batch dönen sözleşmeyle aynı değil; tüketici EKSİK | Kişisel puanları N+1 ile toplama; kişisel batch ihtiyacı owner sözleşmesiyle çözülmeli |
| Hardware / Alper | `HardwareBatchController` POST `/api/v1/hardware/profiles/batch`, sahiplik ve görünürlük politikaları | Kaynak MEVCUT; tüketici EKSİK; bu taskta değiştirilmedi | Owner principal, batch/page limitleri; başka kullanıcının profiline erişim reddi |
| Performance / Alper | `PerformanceInternalController` GET `/internal/v1/accounts/{accountId}/games/{gameId}/performance-reference` | Kaynak MEVCUT; tüketici EKSİK | svc:recommendation-service, reference:read ve account_id; reliable=false yetersiz veri; timeout |
| Social / Umut | `BlocksCheckController` POST internal blocks/check | KISMİ: yalnız Messaging/LFP caller allowlist; Recommendation kimliğine izin yok | İki yönlü block ve arkadaşlık/gizlilik için izinli batch sözleşmesi olmadan sosyal öneri üretilemez |
| Event / Nisa | `src/main/java` altında yalnız `EventServiceApplication` | EKSİK; çalışan etkinlik/eligibility API'si yok | Uydurma etkinlik veya DB erişimi yok; gerçek kontrat owner'dan gelmeli |
| Gateway / Kerem | application.properties route[13], Path `/api/v1/recommendation/**`; cookie→Bearer ve istemci kimlik header temizliği kodda | MEVCUT route; Recommendation iş endpoint'i EKSİK | Internal uçlar bu prefix altında açılmaz; gateway yönlendirmesi JWT doğrulaması yerine geçmez |

RestClient tüketicilerinde mevcut 3000/5000 ms timeout ve dont-follow korunmalı; URL istemciden alınmaz.
Hiçbir entegrasyon bu taskta canlı çağrılmadı; başka servis koduna/DB'sine dokunulmadı.
Recommendation migration klasörü başlangıçta boştu: ilk boş V11 alındı; V12–V20 boş.
Canlı deploy geçmişi sır gerektirecek şekilde sorgulanmadı; V11 yalnız disposable temiz PostgreSQL'de doğrulanır.

## Kaynak çelişkileri

Eski genel docx MSSQL/Vite/Java 21, mimari/güvenlik metinleri bazı servisler için yalnız iskelet der;
repo kodu PostgreSQL/Java 25/Boot 4.1.1 ve uygulanmış Auth/Library/User vb. gösterir.
Güncel kod ve `docs-auth` sözleşmeleri esas alındı; eski ortak metinler kapsam dışı yeniden yazılmadı.
`LTZ_PDA_Alper*.md` kaynak dosyası `docs/` altında bulunamadı; kullanıcının bağlayıcı master §4'ündeki
5 ana/23 alt görev tanımları kaynak kabul edilip checklist'e aynen aktarılır.

## TASK 2 sonrası güncel tüketici durumu — 2026-11-14

Yukarıdaki tablo TASK 1 başlangıç fotoğrafıdır; bu bölüm güncel değişiklikleri belirtir.

- Auth: Recommendation RS256/JWKS kullanıcı ve D4 decoder'ı, canonical role ve no-store/ProblemDetail
  uygulandı. Testte gerçek Auth issuer/JWKS üretim sınıfları kullanılır. Kişisel işlemde mevcut
  account-bound Library grant'ının ACTIVE denetimi çağrılır; session revocation introspection yoktur.
- User: `/me/consents` ve `/me`, request-scope kullanıcı Bearer'ıyla gerçek RestClient adaptörü.
- Library: hesabına bağlı gerçek Auth grant + recommendation-signals; 500 kayıt/10 sayfa;
  favorite/completed dışlama. Bounded şema/tekrar/total kontrolü vardır.
- Game: genre/platform filtreli discovery; max 10×50 aday, canonical ID, MAIN_GAME/adult=false.
  Game GAME-15 toplu metadata ucu teslim edildi (KAPANDI); REC-02/05 tür yakınlığı tüketicisi Recommendation tarafında yazılacak; [kapanan talep](dependencies/done/game-service-requests.md).
- Review: kişisel puanlar için batch/paging yok; genel summary kullanılmadı. REC-03 BLOCKED;
  [owner talebi](dependencies/review-service-requests.md).
- Recommendation D4 hedef ucu hazır; gerçek Auth worker ve kaynak servislerle çok servisli E2E
  henüz doğrulanmadı. Fixture contract kabulü canlı E2E diye raporlanmaz.
- Mevcut `AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET` aktarımı kullanıcı onayıyla yalnız
  Recommendation Compose/property ve `.env.example` yorumunda eklendi; gerçek ENV değişmedi.

V12 kişisel geçmiş/feedback eklendi; V13–V20 boş. Hardware/Performance/Social/Event/Gateway
kodları değiştirilmedi; TASK 3/4 üzerinde uygulama yapılmadı.

## TASK 3 sonrası güncel tüketici durumu — 2026-11-15

- Hardware: owner batch kullanıcı Bearer'ıyla kullanılır; request tek profileId/page0/size100,
  response exact profileId/version, tekil component IDs, quantity/spec ve tam sayfa şeması doğrulanır.
  >100 veya eksik pencere 503; private/foreign profile 404 gizleme korunur. Marka zorunlu değildir.
- Game: gerçek path `GET /api/v1/game/{gameId}/metadata`; exact gameId, adult=false/delisted=false
  ve platform minimum/recommended veri şeması. Katalog filtresi için GAME-15 toplu metadata ucu vardır ve REC-06/16 onu tüketir;
  [kapanan talep](dependencies/done/game-service-requests.md).
- Performance: mevcut aynı Recommendation secret'ıyla Auth audience=performance-service,
  reference:read/account_id grant'ı; `/internal/v1/accounts/{id}/games/{id}/performance-reference`
  ve exact hardwareProfileId parametresi. Response game/profile/count/statistics bağı kontrol edilir.
  Eski referans DTO'su bağlam taşımadığından tahmin üretmez. REC-09 kapandı (2026-11-18):
  tam bağlamlı (resolution + preset + NATIVE_RASTER) istekte
  `/internal/v1/accounts/{id}/games/{id}/performance-evidence` kullanılır, LOW güvenli geçmiş aralık
  döner; bkz. [kapanan talep](dependencies/done/performance-service-requests.md).
- User/Auth: TASK 2 rıza/ACTIVE gate ve withdrawal purge yeni stateless uçta da paylaşılır.
  Auth/Gateway güven modeli, mevcut ENV ve izin çiftleri değiştirilmedi.
- Producer servisler ve DB'leri değiştirilmedi; testler gerçek Auth issuer/JWKS + kaynak HTTP
  contract fixture'ıdır. Canlı Hardware/Game/Performance üçlü tüketici E2E NOT VERIFIED.

TASK 3 yeni kalıcı veri veya migration eklemedi; V11/V12 korunur. REC-06/09 PARTIAL,
REC-07/08/10/11/12 yalnız belgelendirilmiş desteklenen alan kapsamıyla testlidir.

## TASK 4 sonrası güncel tüketici durumu — 2026-11-15

**REC-13/14/15 BLOCKED, 0/3 DONE.** Bu task yeni production tüketicisi veya endpoint açmaz.

| Kaynak | Doğrulanmış mevcut kabiliyet / eksik | Sonuç / owner |
| --- | --- | --- |
| User | Tek profil details; search ve batch yalnız kart. İzinli öneri adayı discovery/tercih batch'i yok; temel kart privacy eligibility kanıtı değil | REC-13/14 BLOCKED; [Kerem talebi](dependencies/done/user-service-requests.md) |
| Social | Owner arkadaş listesi var; iki yönlü blocks/check yalnız Messaging/LFP subject'leri; Recommendation Auth Social hedefi yok | REC-13/14 BLOCKED; [Umut + Kerem talebi](dependencies/done/social-service-requests.md) |
| Event | Yalnız EventServiceApplication; API kataloğu boş | REC-15 BLOCKED; [Nisa talebi](dependencies/event-service-requests.md) |

Mevcut owner Library rızası sosyal aday işlemeye genişletilmedi. Profil kartı taraması,
N+1 details/relationship sorgusu, private library/rating okuması, başka servis kimliği taklidi,
cross-DB erişimi veya tahmini Event endpoint'i çağrısı yapılmaz.
RecommendationApiIntegrationTest'te mevcut denyAll için üç negatif boundary testi eklendi;
User/Social/Event üretici kodu ve auth izinleri değişmedi. Bu testler aday filtreleme/ranking,
Event kapasite/zaman veya canlı çok servisli E2E kabulü değildir.
[TASK 4 karar kaydı](../decisions/task-04-decisions.md).

## TASK 5 sonrası güncel tüketici durumu — 2026-11-15

- Yeni assistant endpoint'i mevcut Auth/JWKS/rıza/ACTIVE/kota modelini kullanır.
- GAME yalnız bounded tür girdisiyle katalog filtresi rehberi; Game aday discovery çağrısı yok.
- HARDWARE tam girdide mevcut owner Hardware/Game/Performance assessment'ını kullanır;
  Python'a sadece üç durum kodu ve eksik alan kodları aktarılır. Yeni producer sözleşmesi yok.
- LTZ_USAGE kendi history/feedback kullanım rehberi; history okumaz/yazmaz, mutasyon başlatmaz.
- AssistantEngine Java portu + aynı servis içinde gerçek CPython; assistant-v1 input/output,
  assistant-rules-v1 motor; stdin/çıktı/timeout/process sınırları ve finite response plan kontrolü.
- Runtime kararı kullanıcı onaylı ADR 0003; mevcut Recommendation Dockerfile'a Python
  minimal/stdlib eklendi. ENV/pip/Maven dependency/sidecar/DB/migration yoktur.

80 Java + 7 Python test; production Auth issuer/JWKS ve kaynak contract fixture; final image
non-root ağsız/read-only smoke ve Trivy PASS. Canlı kaynak servis E2E NOT VERIFIED.
[Çalışma yönergesi](assistant-runtime.md), [testler](../testings/task-05-tests.md),
[final gate BLOCKED ayrımı](../testings/pre-merge-quality.md).
