# TASK 1 doğrulaması — 2026-11-11

- `npm.cmd run test -- src/features/performance src/shared/config/navigation.test.ts`: PASS, 3 dosya / 34 test, exit 0.
- `npm.cmd exec eslint -- src/features/performance 'src/app/[locale]/(public)/performance/page.tsx' src/shared/config/navigation.ts src/shared/config/navigation.test.ts src/shared/config/pages.ts src/shared/lib/i18n/routing.ts`: PASS, exit 0 (test Link mock'u düzeltildikten sonra).
- `git diff --check`: PASS, exit 0; yalnız Git CRLF bilgilendirmeleri.
- Standart `npm.cmd run typecheck`: FAIL, önceden kalan `.next/dev/types/validator.ts` olmayan Community rotasına referans veriyor. Temiz kaynak kontrolü ve final build ayrıca raporlanacak; bu hata PASS sayılmadı.
- Cihaz UI testleri: misafir istek atmaz; loading → boş; 401/403/404/409/429/500/503; ağ hatasından retry; HTML etiketinin düz metin sunumu; hesap değişiminde eski verinin gizlenmesi.
- API consumer testi: gerçek `/api/v1/performance/devices` path'i, no-store, AbortSignal.
- Canlı Auth/Gateway/Performance tarayıcı entegrasyonu bu checkpoint'te NOT VERIFIED; component mock'ları canlı test değildir.
