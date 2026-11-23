# TASK 3 — LTZ-ALP-PWEB-03

Durum: **PARTIAL — 1/3 uygulanmış ve yerel doğrulanmış (PWEB-10); PWEB-08/09 BLOCKED. Final kalite denemeleri tamamlandı, NOT READY FOR MERGE.**
Başlangıç HEAD `5cfe014f`, çalışma ağacı temizdi. TASK 1 `df308dfa`, TASK 2 `5cfe014f` kullanıcı tarafından commit edildi.

- PWEB-08 BLOCKED: Recommendation iş API/sözleşmesi yok. Doğrulanmış uyumluluk sonucu üretilmedi; arayüz kabiliyetin bu sürümde kullanılamadığını açıklar.
- PWEB-09 BLOCKED: kişiselleştirilmiş oyun/donanım önerisi veya tahmini FPS endpoint'i yok. Sahte sonuç, boş başarılı proxy veya istemci algoritması eklenmedi. Master §2.4 gereği büyük yeni Recommendation backend geliştirmesi bu işin dışında.
- PWEB-10: gerçek senkronizasyon izni GET/PUT/DELETE, açık izin onayı/geri alma, mevcut profil gizlilik ayarlarına bağlantı; seçilen oturumu veya tüm oturumları onayla silme. Backend cevabından önce başarı/yerel silme yok. Oturum silme rapor/örnekleri mevcut CASCADE sözleşmesiyle kapsar; donanım/cihaz/izin ayrı tutulur. Başarı sonrası geçmiş/detay yenilenir; belirsiz sonuçta güncel durum okunmadan tekrar mutasyon yapılamaz.

Yeni dosyalar: `features/performance/hooks/use-performance-mutation.ts`, `components/delete-performance-data.tsx`, `components/performance-privacy.tsx` ve testleri.
Değişenler: Performance API/tipleri, dashboard/history, TR/EN metinleri, E2E fixture/senaryoları.
Backend/DTO/migration/endpoint sözleşmesi değişmedi. Auth mevcut cookie/Gateway/refresh/CSRF akışı; yeni talep gerekmedi. Token/userId taklidi yok. Mutasyonlarda no-store, timeout ve unmount iptali; çift gönderimde ref kilidi var. Yalnız açık `CSRF_TOKEN_INVALID` reddi bir kez tekrarlanır; belirsiz hata otomatik tekrarlanmaz.

`docs/api.md` yalnız PERFORMANCE bölümünde kullanılan dokuz mevcut endpoint'in consumer durumu PWEB kodlarıyla güncellendi; canlı tarayıcı kabulü NOT VERIFIED olarak korundu. HTTP sözleşmesi değişmedi.

Hedefli sonuçlar: 53 unit/component test PASS; son 10/10 Performance E2E PASS; typecheck ve değişen kod ESLint PASS. İlk E2E koşusunda metin seçicisi iki butona uydu, `exact` ile düzeltildi; TypeScript union dönüşü de await ile düzeltildi. Başarısız ilk koşular PASS sayılmadı.
Final tam regresyon, build, gerçek pre-push ve ayrı SonarQube sonuçları `testings/pre-merge-quality.md` içine kaydedildi. 1563 frontend test PASS / mevcut 1 SKIP; Performance 10/10 E2E PASS; son tam browser 305 PASS/1 FAIL/56 mevcut SKIP, tek Ctrl+K hatası izole tekrarda PASS. 4270 backend test ve coverage PASS; pre-push genel gate FAIL (mevcut Hardware kalite borcu + ilk eksik Gitleaks; sonradan ayrı secret taramaları PASS). Gerçek backend Sonar gate FAIL, C/C/A, coverage %90,4, duplication %3,3; frontend Sonar BLOCKED. Canlı tarayıcı servisi kabulü fixture E2E'den ayrı NOT VERIFIED.

```text
feat(performance-web): add PWEB-10 privacy controls and confirmed data deletion

- PWEB-10: mevcut senkronizasyon iznini oku, açık onayla kaydet ve geri al; profil gizlilik ayarlarına erişim sun
- Veri yönetimi: owner oturumunu veya tüm performans oturumlarını rapor ve ölçümleriyle birlikte onaylı silme akışına bağla
- Güvenlik: CSRF başlığı, no-store, timeout, unmount iptali ve çift gönderim kilidi uygula
- Hata yönetimi: sunucu onayından önce başarı gösterme; belirsiz mutasyon sonrası yeniden okumayı zorunlu tut ve geçmiş detaylarını güvenle yenile
- UI: TR/EN gizlilik metinleri, erişilebilir ortak onay penceresi ve mevcut olmayan uyumluluk/öneri kabiliyetlerinin açık durumunu ekle
- Testler: onay, vazgeçme, başarısız silme, çift gönderim, unmount ve CSRF senaryolarını ekle; 1563 frontend testini ve 10 Performance E2E senaryosunu doğrula
- Regresyon: Hardware tarayıcı fixture'ına mevcut CSRF cookie sözleşmesini ekle; misafir menüsü ve etkin Performans navigasyonu beklentilerini güncelle
- API kataloğu: mevcut dokuz Performance endpoint'inin PWEB consumer durumunu gerçek entegrasyon kapsamıyla güncelle
- Dokümantasyon: PWEB-08/09 sözleşme bağımlılıklarını ve TASK 3 test/kalite kanıtlarını gerçeğe uygun kaydet
```

Commit/push yapılmadı. Kullanıcı commit'i için bu checkpoint'te duruldu. Sonuç PARTIAL/NOT READY; PWEB-08/09 veya kalite gate'leri tamamlanmış gibi gösterilmedi.
