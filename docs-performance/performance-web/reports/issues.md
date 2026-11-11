# Performance Web — açık bağımlılıklar

- PWEB-08/09 BLOCKED: `recommendation-service/src/main/java` yalnız `RecommendationServiceApplication.java` içeriyor; `docs/api.md` RECOMMENDATION bölümünde kayıt yok. Gerçek uyumluluk, kişiselleştirilmiş öneri, açıklama ve tahmin sözleşmesi bulunmuyor. Master §2.4 büyük yeni backend özelliklerini kapsam dışında tutuyor. Yeni Recommendation PDA geliştirmesi ve test edilmiş API sözleşmesi gerekiyor. Frontend puan/FPS/uyumluluk hesaplayarak bu eksikliği gizlemeyecek.
- Kök `npm run typecheck`, çalışma öncesinden kalan `.next/dev/types/validator.ts` içinde bulunmayan `community/mine/words/page.js` referansı nedeniyle FAIL. Kaynak kontrolü için `test-results/performance-web-tsconfig.json` mevcut tsconfig'i genişletip üretilmiş `.next` doğrulayıcısını dışarıda bırakır; kurallar gevşetilmez. Son build sonrası standart kontrol tekrar çalıştırılacak.
- Pre-push full ve mevcut Sonar runner gerçek `.env.dev` okur. Bu işlem için açık onay soruldu; onay gelmeden çalıştırılmaz. Mevcut Sonar runner yalnız backend tarar, frontend kapsamını doğrulamaz.
- `docs/SECURITY.md`/`architecture.md` içinde bazı eski NOT IMPLEMENTED Auth/Gateway ifadeleri güncel kodla çelişiyor. Güncel `CookieBearerFilter` cookie → Bearer, CSRF ve Origin denetimi uygular; Performance JWT/owner kontrolleri mevcut. Kaynak kod esas alındı, güven modeli değiştirilmedi.

Başlangıç dalı: `feature/performance-service`, HEAD `085af157`.
Önceden var olan değişiklikler: dört Auth talep belgesinin silinmiş görünmesi, `eski-requestler/` dizini ve master MD untracked; korunuyor.
