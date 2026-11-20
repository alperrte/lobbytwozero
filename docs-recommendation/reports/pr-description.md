# PR taslağı — DRAFT / BLOCKED

## PR Title

```text
feat(recommendation): öneri motoru, owner API'leri ve Python yardımcısını ekle
```

## Summary

Recommendation için sürümlü rules-v1 motoru, kimlik taşımayan motor analitiği, rızaya bağlı
tür önerileri ve owner geçmiş/feedback API'leri ekler. Donanım değerlendirmesi gerçek owner
profil ile tek oyunun minimum/recommended alanlarını karşılaştırır; eksik kanıtı UNKNOWN
tutar. Yeni Python yardımcısı kapalı intent ve sabit tr/en şablonlarıyla oyun seçimi,
donanım değerlendirmesi ve LTZ kullanım rehberliği sağlar.

**PR merge-ready değildir.** Belgelenmiş desteklenen kapsamda 14/23 REC DONE; native
pre-push ve SonarQube final kapıları BLOCKED. Eksik feature'lar ve canlı E2E kabulü açık kalır.
Bu dosya yalnız taslaktır; gerçek PR oluşturulmadı.

## Tasks and scope

| Task | Gerçek durum | Teslim / açık |
| --- | --- | --- |
| LTZ-ALP-REC-01 | DONE, 3/3 | Rules-v1, sürümlü port ve servis içi analitik temeli; Python/ürün event analitiği tamamlandı anlamına gelmez |
| LTZ-ALP-REC-02 | PARTIAL, 5/9 | Tür kişiselleştirme, neden, owner history/feedback; REC-02/03/05 metadata/rating, REC-16 sistem filtresi açık |
| LTZ-ALP-REC-03 | PARTIAL, 5/7 | Alan bazlı minimum/recommended, koşullu grafik, kanıtlı RAM/disk yükseltme, socket/DDR; REC-06 katalog ve REC-09 FPS açık |
| LTZ-ALP-REC-04 | BLOCKED, 0/3 | User/Social/Event sözleşme talepleri ve denyAll erişim sınırı testleri; sosyal/etkinlik önerisi uygulanmadı |
| LTZ-ALP-REC-05 | Feature 1/1, final PARTIAL | Gerçek Python REC-22; final pre-push/Sonar engelleri nedeniyle NOT READY |

## Architecture and integrations

- Java25/Boot4.1.1 servis katmanları; RecommendationAlgorithm ve AssistantEngine ayrı use-case
  portları. Mevcut ranking sözleşmeleri korunur, yeni assistant endpoint'i API kataloğuna kayıtlıdır.
- Auth üretim RS256/JWKS ve kanonik kullanıcı/servis ayrımı; role/owner, User recommendation
  rızası ve Auth account-bound ACTIVE denetimi. Gateway cookie/CSRF modeli değişmedi.
- User kendi türleri + Game canonical discovery + Library account-bound sinyaller; eksik
  metadata/kişisel rating sinyalleri uydurulmadı. Bounded aday/sinyal ve deterministik sıralama.
- Hardware owner batch, Game tek oyun metadata, Performance exact account/profile/game reference.
  reliable bayrağı karşılaştırılabilir FPS kanıtı değildir; tahmin INSUFFICIENT_DATA/null.
- REC-22, açık kullanıcı onaylı ADR 0003 ile aynı serviste sabit CPython script'i çalıştırır.
  LLM/sidecar/pip ve serbest metin yorumlama yoktur. GAME rehberi aday listesi üretmez;
  HARDWARE yalnız owner assessment durum kodlarını, LTZ_USAGE sabit kullanım adımlarını kullanır.
- Process stdin 8 KiB, stdout/stderr ayrı 16 KiB, deadline 2 sn, en çok 2 process; temiz
  environment, sabit argv/script, bounded ve bağlama bağlı JSON/şablon/step doğrulaması.
  Kullanıcı adına profil/arkadaşlık/satın alma/feedback mutasyonu veya dış fetch yapılmaz.

## Database and migrations

- V11 recommendation_observations: yalnız gerçek motor çalışma/sonuç özeti; kullanıcı/hedef
  kimliği ve private ham sinyal saklamaz, retention 30 gün.
- V12 recommendation_runs/items/feedback: user/game REF, yerel FK/CASCADE, tekillik/check/index,
  owner concurrency/version, cursor, 30 gün/500 run ve idempotent D4/withdrawal purge.
- Mevcut uygulanmış migration'lar değiştirilmedi. Task 3–5 yeni tablo/migration eklemedi;
  hardware snapshot, assistant chat/history veya private profil kopyası saklanmaz.
- Üretici servis DB'lerine erişim/değişiklik yoktur; DEV/production migration/deploy yapılmadı.

## Tests and quality gate

| Kontrol | Sonuç |
| --- | --- |
| Maven clean verify / Recommendation regresyon | 80 PASS / 0 failure-error-skipped, exit 0 |
| Saf Python unittest | 7 PASS, exit 0 |
| Auth / PostgreSQL | Gerçek Auth issuer/JWKS, disposable PostgreSQL17.11/Flyway/JPA |
| Coverage | LINE %97,51; merkezi ek/değişen %97,70; origin/main'e göre tüm taskları kapsar |
| Checkstyle/PMD | 0 yeni ihlal, PASS |
| JAR dependency / final image HIGH-CRITICAL | 0 bulgu, PASS |
| Docker build / packaged Python smoke | Exit 0; script hash eşleşir, non-root/read-only/network-none |
| Native pre-push | BLOCKED; korumalı giriş noktası iç exit 1, gerçek ENV/uygulama DB fallback engellendi |
| Sonar scanner / server gate / A'lar | BLOCKED / UNKNOWN; güncel analiz gönderilmedi |
| Çok servisli canlı E2E | NOT VERIFIED; HTTP contract fixture E2E yerine sayılmadı |

[Tam final kapı raporu](../testings/pre-merge-quality.md),
[TASK 5 testleri ve tüm komutlar](../testings/task-05-tests.md).
Pre-push/Sonar gerçek profil gerektirdiği için agent oturumunda korumasız çalıştırılmadı;
disposable CI başarıları bu kapıların geçtiği anlamına gelmez.

## Known limitations / blockers

- Hamza: Game metadata/requirements batch ve Review kişisel rating batch sözleşmeleri.
- Alper: Performance resolution/preset/provenance/ölçümdeki profil sürümü bağı; gerçek FPS yolu açık.
- Kerem/Umut: izinli User aday/tercih ve Recommendation için Social friend/block/erişim eligibility,
  gerekirse dar Auth grant; private enumeration olmadan tasarlanmalı.
- Nisa: Event iş API'si ve viewer time/capacity/katılım uygunluğu.
- Alper/kalite sahibi: native pre-push kanıtı ve Sonar sunucu/token/analysisId/gate/A doğrulaması;
  ardından feature açıkları/E2E kabulüyle yeniden readiness değerlendirmesi.
- Rollout yeni Python runtime paketleri gerektirir; mevcut client secret aktarımı TASK 2'de,
  runtime/Docker modeli TASK 5'te açık onaylandı. Gerçek ENV okunmadı/değişmedi.

## Checklist

- [x] Uygulanan feature'lar testleri ve API/veri/güvenlik sınırlarıyla belgeli.
- [x] Gerçek Python ve sınır/hata testleri geçti; LLM veya ayrı AI servisi yok.
- [x] Flyway/JPA/constraint regresyonu ve docs güncel.
- [x] Beş ayrı commit taslağı hazır; gerçek Git yazma/kimlik işlemi yapılmadı.
- [ ] 23/23 REC alt taskı gerçek testlerle kapatıldı.
- [ ] Sosyal eligibility/iki yönlü block ve Event feature kabulü tamamlandı.
- [ ] Native pre-push PASS.
- [ ] SonarQube güncel Quality Gate PASS ve hedef üç A doğrulandı.
- [ ] Canlı çok servis E2E tamamlandı.
- [ ] PR kod incelemesi ve merge onayı kullanıcı tarafından tamamlandı.
