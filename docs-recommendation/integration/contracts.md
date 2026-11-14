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
