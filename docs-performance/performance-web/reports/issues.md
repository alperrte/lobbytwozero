# Performance Web — açık bağımlılıklar

- PWEB-08/09 BLOCKED: `recommendation-service/src/main/java` yalnız `RecommendationServiceApplication.java` içeriyor; `docs/api.md` RECOMMENDATION bölümünde kayıt yok. Gerçek uyumluluk, kişiselleştirilmiş öneri, açıklama ve tahmin sözleşmesi bulunmuyor. Master §2.4 büyük yeni backend özelliklerini kapsam dışında tutuyor. Yeni Recommendation PDA geliştirmesi ve test edilmiş API sözleşmesi gerekiyor. Frontend puan/FPS/uyumluluk hesaplayarak bu eksikliği gizlemeyecek.
- RESOLVED (TASK 2): ilk standart typecheck/build, eski `.next/dev/types/validator.ts` içindeki bulunmayan `community/mine/words/page.js` referansıyla FAIL oldu. Playwright'ın gerçek Next.js dev sunucusu tipleri doğal biçimde yeniledi; ardından standart `npm.cmd run typecheck` ve production build PASS. Üretilmiş dosyalar elle düzenlenmedi, TypeScript kuralları gevşetilmedi.
- Pre-push full ve mevcut Sonar runner gerçek `.env.dev` okur. Kullanıcı bu iki kalite betiğinin mevcut değerleri yalnız alt süreçlerde kullanmasını açıkça onayladı; dosyaları elle açma/yazdırma/değiştirme yetkisi yok. Final gate TASK 3 sonrasındadır. Mevcut Sonar runner yalnız backend tarar, frontend kapsamını doğrulamaz.
- RESOLVED (TASK 2): tam frontend regresyonunda, TASK 1'de etkinleşen Performans rotası için AppShell/footer testlerinde üç eski pasif menü beklentisi bulundu. Gerçek `/tr/performans` bağlantısı ayrıca doğrulanarak beklentiler düzeltildi; tam koşu 1544 PASS / önceden mevcut 1 SKIP.
- Sandbox SWC cache sahipliği nedeniyle ilk build başlatılamadı. Aynı kaynak ve ENV dosya okumalarını engelleyen test preload'u ile normal kullanıcı bağlamında build/E2E çalıştırıldı. Güvenlik veya uygulama derleyici ayarları değiştirilmedi.
- RESOLVED (TASK 3): coverage aracının ürettiği HTML/JS içindeki unused eslint-disable uyarısı, yalnız `coverage/**` generated rapor klasörünün mevcut ESLint build-output ignore listesine eklenmesiyle giderildi. Kaynak kod kuralları ve kaynak taraması korunur; son lint exit 0/uyarısız.
- Final tam E2E: ilk 294 PASS/12 FAIL/56 mevcut SKIP; fixture/shell/guest beklenti düzeltmeleri ve tekrarlar sonrasında tam iki-worker koşu 305 PASS/1 FAIL/56 SKIP. Kalan Navbar Ctrl+K focus senaryosu izole tekrarda 1/1 PASS. Temiz tam koşu olmadığı için FLAKY/FAIL raporlanır. Kapsam dışı arama/animasyon üretim kodu test geçirmek için değiştirilmedi.
- `docs/SECURITY.md`/`architecture.md` içinde bazı eski NOT IMPLEMENTED Auth/Gateway ifadeleri güncel kodla çelişiyor. Güncel `CookieBearerFilter` cookie → Bearer, CSRF ve Origin denetimi uygular; Performance JWT/owner kontrolleri mevcut. Kaynak kod esas alındı, güven modeli değiştirilmedi.

Başlangıç dalı: `feature/performance-service`, HEAD `085af157`.
Önceden var olan değişiklikler: dört Auth talep belgesinin silinmiş görünmesi, `eski-requestler/` dizini ve master MD untracked; korunuyor.

## Final pre-push — 2026-11-11

Gerçek full dev: Maven test/build/coverage ve 18 HTTP health PASS, fakat genel gate **FAIL / NOT SAFE TO COMMIT**, exit 1. İlk eksik Gitleaks kuruldu ve redakte worktree/yeni Performance kaynak taramaları PASS; ilk full sonuç değiştirilmedi.
Hardware kalite ratchet'i mevcut kaynaklarda altı dosya/kural satırında sekiz ek ihlal buldu: `HardwareProductSourceView` star import; `IcecatProductParser` iki unused import; `IcecatHttpClient` kaynak kapatma/complexity; `IcecatXmlReader` iki complexity; `LocalCatalogMediaStorage` complexity. Tam ayrıntı kalite raporunda.
`git diff --quiet 085af157 -- backend` exit 0: bu oturumda backend kaynakları değişmedi. İlişkisiz katalog/Icecat/depolama refactor'u veya baseline genişletme yapılmadı. Ayrı Hardware kalite işi gerekir.

## SonarQube — güncel gerçek analiz

`sonar.cmd scan dev` exit 1: analiz yüklendi/işlendi fakat backend Quality Gate **FAILED**, Security/Reliability/Maintainability **C/C/A**, coverage **%90,4**, duplication **%3,3**. CE `d72330be-eb5d-4370-875e-e60c9e4bc2e6`, SCM `5cfe014f`. Runner 10 BUG/3 VULNERABILITY kaydı ve sunucunun yeni-kod döneminde 299 issue raporladı; bu son sayı task diff'i değildir. Değişmeyen Auth/Mail/Community/Messaging/Hardware kaynakları için owner triage gerekir; bulgular bağımsız doğrulanmış güvenlik açıkları diye sunulmadı.
Frontend runner kapsamı dışında **BLOCKED**. Code smell/hotspot toplamları **UNKNOWN**; ek anonim ölçüm API'si 401 verdi. Raporlarda gate/A veya sıfır bulgu uydurulmadı.
