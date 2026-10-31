# GAME–REVIEW toplu puan özeti entegrasyon talebi

## 1. Talep eden servis

review-service — Hamza. Bu belge gereksinim bildirir; hedef servise kesin geliştirme emri değildir.

## 2. Hedef servis

game-service — Hamza. Nihai tüketim tasarımı Game/Auth/Review sahiplerince belirlenir. Game kodu değiştirilmedi.

## 3. İlgili task

REV-12 (Review API kabulü tamamlandı; Game→Review entegrasyonu bekliyor), REV-10. Game kataloğunda puan bağımlılığı açık GAME-04/05 ile ilişkilidir.

## 4. Talebin amacı

Game listelerinde LTZ puan ortalaması ve puan veren kullanıcı sayısını sayfa başına tek toplu çağrıyla okuyabilmek. Steam oranları bu verinin yerine kullanılamaz.

## 5. Mevcut durum

docs/api.md Game liste/arama sözleşmelerinde minRating/RATING sıralaması desteklenmiyor; GameExceptionHandler RATING_INTEGRATION_PENDING üretir. Game public POST /api/v1/game/games/batch yalnız ACTIVE kartlar sunar; LTZ puanı üretmez ve yeni Review API'sini tüketmez.

Review'da GET /api/v1/review/games/{gameId}/rating-summary ve POST /api/v1/review/games/rating-summaries uygulandı/test edildi; bugün yalnız kullanıcı at+jwt ve mevcut rollerle erişilir. Auth application.properties içinde Game→Review hedef/kapsamı yok; Review svc+jwt decoder'ı D4 hesap silme içindir. Game sunucu çağrısı yetkili değildir.

## 6. Eksik gereksinimler

Game RestClient tüketimi, Auth hedefe özel servis kimliği/izin sözleşmesi ve Review alıcı doğrulaması gerekir. Auth talebi [auth-review-integration.md](auth-review-integration.md) içine eklendi. Bunlar bu fazda uygulanmadı. Kullanıcı JWT'si servis kimliği yerine taşınmayacak; anonim erişim açılmayacak.

## 7. Önerilen entegrasyon sözleşmesi

**Mevcut kullanıcı API veri sözleşmesi:** POST /api/v1/review/games/rating-summaries; application/json `{gameIds: UUID[]}`. Ham dizi en fazla 50; tekrarlar ilk görülme sırasıyla tekilleşir; boş dizi 200/items=[]. Yanıt `{items:[{gameId,averageRating,ratingCount}]}`; iki basamak HALF_UP, puansız oyun null/0. Bilinmeyen ve puansız oyun ayrılmaz; katalog varlık/görünürlük sahibi Game'dir. Mevcut hatalar: 400 validation, 401 kimlik, 403 rol, 413 gövde, 500 sunucu.

**Henüz uygulanmamış öneri:** Bu veri modeli için en az yetkili svc+jwt okuması tasarlansın. Mevcut namespace'e dar erişim mi, ayrı internal read yolu mu olacağı karşılıklı kararlaştırılmalı; yeni internal endpoint mevcut değildir.

En fazla 50 oyunluk sayfa tek çağrıyla zenginleştirilebilir. Ancak global minRating filtresi/RATING sıralaması yalnız alınmış sayfaya puan eklenerek doğru uygulanamaz. Tüm aday kümede filtre/sıralama ile sayfalama ilişkisi ayrıca tasarlanmalı; bu belge veya sınırlı batch API global sıralamayı çözmüş sayılmaz.

## 8. Kimlik doğrulama ve yetkilendirme

Öneri: Auth D3 uyumlu, tek aud=review-service, sub=svc:game-service ve dar okuma kapsamı. Scope adı/yol henüz kesinleşmedi. RS256/JWKS, typ/issuer/audience/imza/zaman/jti ve tam çağıran/izin allowlist'i ortak sözleşmeyle doğrulanmalı. Kullanıcı rolleri, body/header servis ismi veya account:erase yetkisi bunun yerine geçmez. Secret/ENV veya yeni yetki oluşturulmadı.

## 9. Hata ve güvenilirlik davranışı

Bounded connect/read timeout, sınırlı retry ve token yenileme bütçesi netleştirilmeli. 401/403'te kontrolsüz retry veya kullanıcı token'ına fallback olmamalı. Kesintide puan 0 gösterilmemeli; erişilemeyen veri ile doğrulanmış null/0 ayrılmalı. Cache seçilirse tazelik ve puan güncelleme/hesap silme gecikmesi belgelenmeli. Salt okunur sorgu için outbox/teslim ACK'i gerekmez; rapor altyapısı eklenmedi.

## 10. Kabul kriterleri

- Gerçek Auth token'ıyla yetkili Game çağrısı; yanlış audience/scope/sub/typ, expired token ve kullanıcı-token fallback reddi doğrulanır.
- Sayfa başına tek Review çağrısı; 50 oyun için tek aggregate SQL, N+1 yoktur.
- Tekrar/boş/puansız kimlikler, sıra, yuvarlama, timeout ve yetkisizlik karşılıklı test edilir.
- Puan güncelleme ve hesap silme sonrası gerçek Game görünümü doğrulanır; canlı entegrasyon öncesinde PASS iddiası kurulmaz.
- Global puan filtresi/sıralaması hedeflenirse toplamlar ve sayfa doğruluğu ayrıca test edilir.

## 11. Bağımlılık ve etki

REV-08/REV-12 Review backend kabulünü engellemez. Game tüketimi ve REV-10 kapanışı Auth/alıcı/tüketici sözleşmesine bağlıdır; REV-07 Admin bağımlılığı sürer.

## 12. Durum

**TALEP / ÖNERİ — Henüz uygulanmadı; SÖZLEŞME BEKLENİYOR.** Review kullanıcı API'si IMPLEMENTED; Game→Review servis entegrasyonu NOT IMPLEMENTED / NOT VERIFIED.

## 13. Karar bekleyen konular

Servis okuma yolu/kapsamı, token dağıtımı ve ömrü, timeout/retry bütçesi, cache tazeliği, hata sunumu, sayfa büyüklüğü uyumu ve global puan filtre/sıralama mimarisi netleştirilmeli. Review onaylanan sözleşmeye sonradan uyarlanacak; nihai tasarım ilgili servis sahiplerine aittir.
