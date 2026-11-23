# TASK 2 — doğrulama kanıtları

Tarih: 2026-11-11, dal `feature/performance-service`, başlangıç HEAD `df308dfa`.
Yalnız TASK 2 çalışma ağacı; TASK 1 commit'i kullanıcı tarafından atıldı.

| Kontrol | Gerçek komut | Sonuç |
| --- | --- | --- |
| Tüm unit/component regresyonu | `npm.cmd run test` | PASS: 159 dosya, 1544 test geçti, önceden mevcut 1 test skipped, exit 0 |
| Özellik coverage | `npm.cmd run test:coverage -- src/features/performance src/features/hardware/components/hardware-snapshot.test.tsx src/features/hardware/components/hardware-profile-reference.test.tsx --coverage.include='src/features/performance/**/*.{ts,tsx}' --coverage.include='src/features/hardware/components/hardware-snapshot.tsx' --coverage.include='src/features/hardware/components/hardware-profile-reference.tsx'` | PASS: 7 dosya, 36 test; lines %98,46 (128/130), branches %78,08, statements %94,38; yalnız belirtilen özellik kapsamıdır, tüm frontend coverage değildir |
| ESLint | `npm.cmd run lint` | PASS: exit 0, kaynak hatası yok; coverage generated JS üzerinde 1 uyarı |
| TypeScript | `npm.cmd run typecheck` | PASS: standart tsconfig, exit 0; eski Next dev tipleri gerçek dev sunucusunca yenilendi |
| Production build | `npm.cmd run build` | PASS: TypeScript, page collection ve statik üretim; `/[locale]/performance/dashboard` rota listesinde |
| Chromium E2E | `npm.cmd run test:e2e -- performance.spec.ts` | PASS: 7/7, exit 0 |
| Whitespace | `git diff --check` | PASS: exit 0; Git CRLF bilgilendirmeleri hata değil |
| Backend | Kod/DTO/şema değişmedi | Yeni Maven/Flyway koşusu N/A; canlı owner/IDOR kabulü bu taskta NOT VERIFIED |
| Pre-push / Sonar | Master §7 | TASK 3 sonrası final gate; bu checkpoint'te NOT RUN |

Build ve E2E başlangıcı: sandbox SWC native cache sahiplik hatası verdi. Aynı komutlar normal kullanıcı bağlamında çalıştırıldı. `.env*` okumaları `test-results/performance-web/no-env.cjs` test preload'uyla engellendi; test alt sürecinde mevcut public API adresi `http://localhost:5080` verildi. Gerçek ENV dosyası okunmadı/değiştirilmedi. İlk build/typecheck eski `.next/dev` referansıyla FAIL oldu, E2E tip üretimi sonrasında standart kontroller PASS. İlk tam test koşusunda üç eski menü beklentisi FAIL oldu; AppShell/footer beklentileri gerçek rota için güncellendi ve tekrar koşuldu.

## Senaryolar

- Donanım: çoklu bilgisayar, manuel model, opsiyonel marka, null teknik alan, gerçek görünürlük, gizlilik yanıtı hatasında varsayım yapmama; raporun kendi profilini çözme, erişilemeyen profil için hata.
- Geçmiş: backend sayfalama/durum filtresi, seçimde detay getirme (liste başına N+1 yok), filtre/sayfa değişiminde seçimi sıfırlama, gerçek boş sonuç ve 401/403/404/409/429/500/503 ayrımı.
- Grafik: 10 metrik, tam/ilk 5/ilk 30 dakika penceresi, maxPoints=100, gerçek sıfır ile eksik veri ayrımı, RAW/BUCKET açıklaması, AVG/MIN/MAX/count tablosu, tek nokta/sabit seri koordinatları, kısmi hata.
- Rapor: manuel kaynak açıkça belirtilir, nullable metrikler kaydedilmemiş görünür, 0 % korunur, HTML benzeri not metin olarak gösterilir, rapor yokluğu hata 404'ten güvenli biçimde ayrılır.
- Auth: misafir/loading durumunda private consumer mount edilmez; hesap değişince alt ağaç yenilenir. Ortak cookie/refresh/Gateway mekanizması kullanılır; backend kimliği taklit edilmez.
- Chromium: 375/768/1440 px, TR/light; EN/dark/reduced-motion ve Enter ile açma; noindex, sayfa düzeyinde yatay taşma yok; misafir tanıtım ve güvenli login yönlendirme; servis/kısmi grafik hata durumları.

Tarayıcı testleri route fixture kullanır; canlı Auth/Gateway/Hardware/Performance uçtan uca testi olarak sunulmaz. Ekran görüntüleri `frontend/test-results/playwright/performance-{375,768,1440}.png` ve `performance-dark-en.png`; 375/1440 ve dark EN görselleri açılarak incelendi. Mobilde grafik yazıları küçülmesin diye grafik/table kontrollü yatay kaydırma alanında sunulur. Tam WCAG sertifikasyonu veya ekran okuyucu kabulü iddia edilmez.

Mevcut jsdom medya/form/navigation uyarıları tam test çıktısında var; yeni test skip'i eklenmedi. Ortak logo LCP tavsiyesi E2E çıktısında var; kapsam dışı kabuk refactor'u yapılmadı.
