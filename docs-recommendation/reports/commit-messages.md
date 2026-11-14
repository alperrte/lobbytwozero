# Commit taslakları

Gerçek commit/stage/push yapılmadı. Yalnız TASK 1 taslağı vardır.
Başlık/body bağlayıcı master v2 §12 ve kullanıcının Conventional Commit talebine göre hazırlanmıştır.

## TASK 1 / LTZ-ALP-REC-01

```text
feat(recommendation): LTZ-ALP-REC-01 sürümlü kural motoru ve analitik temelini kur

- REC-20: rules-v1 motoruna sınırlandırılmış dört sinyal, katkı tabanlı skor ve gerekçe, UUID eşitlik sırası, tekil aday ve sonuç limitleri ekle.
- REC-21: değişmez RecommendationAlgorithm input/output portunu ve sürüm doğrulayan execution servisini oluştur; alternatif algoritmayı aynı sözleşmeyle test et.
- REC-23: yalnız gerçek motor çalışması için GENERATED, EMPTY ve UNAVAILABLE gözlemlerini kaydet; skor/sonuç toplamlarını gösterim, tıklama veya memnuniyet ölçümü olarak sunma.
- Veri: V11__create_recommendation_observations.sql ile recommendation_db içinde PK tekilleştirme, sayım/durum CHECK'leri ve tarih indeksini oluştur; mevcut migration'ları koru.
- Saklama: 30 günlük özet/saklama penceresi ve dakikada 1000 satırlık expired temizliği uygula; tekrar teslimi tekilleştir ve değişen içerikte conflict üret.
- Güvenlik: analitikte kullanıcı/hedef kimliği, token, profil ve ham oyun sinyali saklama; yeni HTTP veya güven modeli açmadan başlangıç 401/CSRF 403 yüzeyini doğrula.
- Testler: disposable PostgreSQL 17.11 üzerinde Maven clean verify ve merkezi runner ile 29 testi geçir; JaCoCo toplam %98,57, değişen kod %100, Checkstyle/PMD 0 ihlal ve dependency gate PASS sonuçlarını kaydet.
- Test güvenliği: context ve DB testlerini DisposableDatabaseInitializer ile yalnız geçici CI veritabanına sınırla; DEV/production verisine geri dönüşü engelle.
- Entegrasyonlar: Auth/User/Game/Library/Review/Hardware/Performance/Social/Event/Gateway gerçek sözleşme ve izin envanterini belgele; canlı tüketici veya Python çalıştırıldığı iddiası ekleme.
- Belgeler: docs/api.md, database/ER/mimari/klasör/migration kayıtlarını; TASK 1 checklist, karar, test, ilerleme ve sorun raporlarını güncelle.
```

Bu checkpoint iş HTTP endpoint'i, kullanıcı feedback akışı, Python runtime veya merge-ready
teslimi değildir. Pre-push ve ayrı SonarQube final gate'i TASK 5 kapanışında uygulanacaktır.
