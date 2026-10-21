# LTZ-KER-AUTH-05 — Faz 5 Kapanış Raporu (Rol, Yetki ve Hesap Durumları) + Kod Kalitesi Denetimi

> Tarih: 2026-10-21 · Sorumlu: Kerem · Kapsam: AUTH-11, AUTH-12, AUTH-14

## 1. Durum

| Görev | Durum | Not |
| --- | --- | --- |
| AUTH-11 Rol yönetimi | IMPLEMENTED | 5 kanonik rol seed (V67), ADMIN uçları, gerekçeli ve denetimli verme/alma |
| AUTH-12 Hesap durumları | IMPLEMENTED (kısmi) | Askı/yasak/devre dışı/aktif; süreli askı ve BANNED kaldırma yok |
| AUTH-14 RBAC | IMPLEMENTED (auth-service) | `/admin/**` → ROLE_ADMIN servis sınırında; 401/403 ayrımı; diğer servislerin RBAC'i kendi görevleri |

## 2. Tasarım

- Yönetici kendi rolünü/durumunu değiştiremez; `PLAYER` alınamaz; `ADMIN`/`MODERATOR` yalnız ACTIVE + TOTP'li hesaba verilir.
- Rol/durum değişince oturumlar iptal, `token_version` artar: eski access token'lar auth uçlarında anında düşer (testle doğrulandı: rolü alınan yöneticinin eski token'ı 401).
- Eşzamanlı aynı rol verme: hesap satır kilidi → 4 istekten 1 başarılı.
- `V67`: roller, `account_status_history`, `account_role_history`, `auth_sessions.revoke_reason` CHECK'ine `ROLE_CHANGED`. V68–V70 boş.
- Yeni ENV ve yeni bağımlılık yok.

## 3. Denetim araçları ve sonuçları

| Araç | Sonuç |
| --- | --- |
| **Gitleaks 8.30.1** (git geçmişi) | Sızıntı yok. Çalışma dizini taraması: `.env`/`.env.dev` (Git dışı, beklenen) ve Git dışı `target/`, `logs/` içinde test sırasında üretilmiş rastgele değerler — gerçek secret değerleri log/target'ta aranıp bulunmadı → yanlış pozitif |
| **PMD 7.17 (quickstart) + CPD** | 2 kullanılmayan import → **düzeltildi**. CPD: 2 küçük tekrar (entity yaşam döngüsü yardımcıları, 11–15 satır) → kabul edildi |
| **SpotBugs 4.9.8 + FindSecBugs 1.14** (Low eşik, 172 sınıf) | 74 bulgu, hiçbiri açık değil. **Düzeltilen:** `NullPointerException` yakalama (SessionStateValidator → açık null denetimi); DTO record'larında `List` sızıntısı (EI_EXPOSE_REP → `List.copyOf`). **Yanlış pozitif / kabul:** `HRS_REQUEST_PARAMETER_TO_HTTP_HEADER` (X-Request-Id `[A-Za-z0-9-]{8,64}` ile doğrulanıyor), `UNSAFE_HASH_EQUALS` (iki değer de sunucu tarafı hash), `CRLF_INJECTION_LOGS` (yalnız UUID/sınıf adı loglanıyor), `CT_CONSTRUCTOR_THROW` (bilinçli fail-fast yapılandırma), `SPRING_ENDPOINT/SERVLET_*/COOKIE_USAGE/IMPROPER_UNICODE` (bilgi amaçlı; kullanıcı adı ASCII ile sınırlı) |
| **Semgrep 1.181** (p/java, owasp-top-ten, secrets; 182 dosya) | 1 uyarı: actuator `info` açık görünüyor → `/actuator/info` SecurityConfig'te 401, yalnız `health` açık (yanlış pozitif) |
| **Trivy 0.76** (çalışma zamanı bağımlılıkları) | **Bulundu:** 3 CRITICAL `tomcat-embed-core 11.0.24`, 10 HIGH `jackson-core/databind 2.21.5 ve 3.1.5`. **Düzeltildi** (auth-service pom'unda `tomcat.version=11.0.25`, `jackson-2-bom=2.21.7`, `jackson-bom=3.1.7`); yeniden taramada 0 HIGH/CRITICAL |
| **ArchUnit 1.4.1** (geçici proje) | 4/4 kural geçti: domain hiçbir katmanı import etmez; application → api bağımlılığı bayt kodunda yok; controller/API repository'ye erişmez |
| **Checkstyle 10.26** (google_checks) | 4042 bulgu yalnız biçim (girinti 2↔4 boşluk, import sırası, satır uzunluğu); projenin kendi stili farklı → anlamlı sinyal yok, kural seti uygulanmadı |
| **OWASP Dependency-Check** | **Çalıştırılmadı** (araç önbellekte yok; NVD veri tabanı indirmesi uzun). Yerine Trivy'nin Java bağımlılık taraması kullanıldı |

Raporlar repo dışındaki geçici dizinde; repoya hiçbir araç yapılandırması veya rapor eklenmedi.

## 4. Kalan riskler

- **Diğer 17 servis** Boot 4.1.1 BOM'undaki aynı CVE'li Tomcat/Jackson sürümlerini taşıyor (ortak parent pom). Ortak çözüm: Boot yama sürümüne geçiş veya parent pom'a üç property — ortak dosya olduğu için onay bekliyor.
- **İlk ADMIN** nasıl oluşturulacak karar bekliyor (uç yok; elle SQL/CLI). Bu yüzden yönetici uçları şu an yalnız veritabanında ADMIN rolü verilmiş TOTP'li hesapla kullanılabilir.
- **BANNED kaldırma** ve **süreli askı** kararı yok; admin-service için servis kimliği (D4) yok.
- `application` katmanı Spring Data repository'lerini doğrudan kullanıyor ve `GoogleLoginService/SteamLoginService` `AuthApiPaths` sabitlerine kaynak düzeyinde bakıyor (derleyici sabiti satır içine aldığı için bayt kodunda bağımlılık görünmez). Gerçek sorun değil, katman saflığı borcu.
- Önceki fazlardan açık: AUTH-13 IP limiti (K8), D3 (diğer servislerde iptal ≤5 dk), Gateway CORS ve cookie → Bearer, tablo temizlik işleri, hesap bağlama ucu, production Mail Service (PENDING), gerçek Google/Steam denemesi.
