# TASK 3 — hedefli testler

- `npm.cmd run test -- src/features/performance src/shared/lib/i18n/messages.test.ts`: 8 dosya / 53 PASS, exit 0.
- `npm.cmd run typecheck`: PASS (mutasyon iki dönüş tipi ortak void operation'a çevrildikten sonra).
- `npm.cmd exec eslint -- src/features/performance e2e/performance.spec.ts`: PASS.
- ENV okumaları kapalı `npm.cmd run test:e2e -- performance.spec.ts`: son koşu 10/10 PASS, exit 0. İlk koşu 9 PASS/1 FAIL: güncel durumu kontrol et düğmesi substring ile iki hedefe uydu; kesin ad seçicisiyle düzeltildi.
- Unit: açık onay/vazgeç, sunucu cevabından önce başarı olmaması, duplicate ref kilidi, 401/403/404/409/429/500/503 ve ağ hataları, belirsiz sonuçta tekrar deneme yerine refresh, bulk/single path ayrımı, unmount abort/callback izolasyonu, gerçek izin GET/grant/revoke, CSRF edinme/tek retry/fail-closed.
- Browser: tekli silmede rapor/seri temizleme, bulk silmede donanım/izin ayrımı, 503 sonrası satırların korunması, mobil EN izin ve onay akışı; önceki TR/EN/light/dark/klavye/noindex senaryolarıyla birlikte 10 test.
- Backend owner/IDOR testi mevcut `PerformanceSessionIntegrationTest.anotherUsersSessionAndReportAreInvisibleAndImmutable`; gerçek pre-push Maven doğrulamasında SessionIntegration **32/32**, HistoryIntegration **23/23**, Performance toplam **138/138** PASS, %97,10 line. Gerçek filtre zinciri ve geçici PostgreSQL kullanılır; Game/Hardware peer doubles canlı servis değildir. Fixture tarayıcı testi bu backend güvenlik testinin yerine geçmez.

PWEB-08/09 gerçek Recommendation sözleşmesi olmadığından tamamlanmış özellik testi yok; BLOCKED. Ayrıntı `reports/issues.md`.

## Final regresyonda bulunan ve giderilen test sözleşmesi sorunları

Tam frontend unit koşusu: **161 dosya / 1563 PASS / önceden mevcut 1 SKIP**, exit 0. Özellik coverage koşusu: **55 PASS**, %97,46 satır (192/197), %83,62 branch; tüm frontend oranı değildir.
İlk tüm Playwright koşusu: **294 PASS / 12 FAIL / mevcut 56 SKIP**, exit 1. Başarısız 12 senaryonun tek worker tekrarı: **9 PASS / 3 FAIL**. Kalıcı üç sorun:

- Hardware Atölye fixture'ı CSRF cookie'si sunmayıp gerçek Auth `/csrf` adresine gidiyordu; mevcut double-submit sözleşmesine uygun test cookie'si eklendi. Üretim Auth/Hardware kodu veya CORS ayarı değiştirilmedi.
- Navbar guest testi, gerçek giriş bağlantısı yerine eski pasif logout satırını bekliyordu; mevcut session-aware davranışa uygun login href ve logout yokluğu doğrulandı.
- İlk 12 başarısızın içindeki shell sayım testi de TASK 1'de etkinleşen Performans rotasını içermiyordu; 8 gerçek link/6 bekleyen öğe ve açık Performans href kontrolü eklendi.

Son üç fixture/beklenti düzeltmesinin tekrarı **3/3 PASS**. Diğer 9 senaryo üretim değişikliği yapılmadan tek worker tekrarında geçti; yük/animation hassasiyeti gözlemi, ilk hatalar yok sayılmadı. İki worker final tam koşu **305 PASS/1 FAIL/56 mevcut SKIP**, kalan Ctrl+K focus testi izole tekrarda **1/1 PASS**; full sonuç FAIL/FLAKY olarak korunur. Hiçbir yeni test skip'i eklenmedi veya assertion kaldırılarak güvenlik gevşetilmedi.

Final lint uyarısız PASS; typecheck/build/whitespace PASS. Gerçek pre-push ve ayrı Sonar denemesi başarısız genel gate sonuçlarıyla tamamlandı: backend test/build/coverage PASS (4270 test), mevcut Hardware ratchet FAIL; Sonar backend gate FAIL, C/C/A, frontend scan BLOCKED. Sonuçların ayrıntısı `pre-merge-quality.md` içindedir.
