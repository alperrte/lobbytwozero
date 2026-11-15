# TASK 2 — Game toplu recommendation metadata talebi

Tarih: 2026-11-14 · Sahip: Hamza · Tüketici: Alper / recommendation-service
Durum: OPEN / REC-02 ve REC-05 BLOCKED; Task 2'de başka servis kodu değiştirilmedi.

## Doğrulanmış mevcut durum

`POST /api/v1/game/games/batch` yalnız `GameCardResponse` verir: kanonik gameId, başlık,
origin/completeness/gameType, dış referanslar ve coverUrl. Tür/platform terimleri ve sistem
gereksinimleri kartta yoktur. `GET /games/{gameId}/metadata` tek oyun içindir.
Discovery tür/platform filtrelidir ancak kişisel favori/bitirilen oyun ID kümesinin metadatasını
toplu vermez. İnceleme puan özeti de kişisel oyun zevki metadatası değildir.

Favori ve tamamlanan oyunların Library'den alınan UUID'lerinden tür yakınlığı çıkarmak için
oyun başına metadata çağrısı yapmak N+1 sınırını ihlal eder. Recommendation başka DB'ye erişmez,
provider ID'yi gameId yapmaz ve kartın eksik türlerini tahmin etmez.

## Önerilen sözleşme — henüz uygulanmış endpoint değildir

`POST /internal/v1/recommendation/game-metadata/batch`

- Request: `gameIds: UUID[]` (1–100; tekil). Tercihen mevcut public batch genişletmesiyle de
  çözülebilir; kesin path ve görünürlük kararının sahibi Game'dir.
- Response: `items: [{gameId, gameType, adult, visible, genres:[{code}], platforms:[{code}]}]`,
  `unavailable: UUID[]`. Her istenen ID tam bir kez item veya unavailable'dadır.
- Yalnız kanonik Game UUID; token/email, kullanıcı listesi, ham sağlayıcı payload'ı veya private
  geliştirici içeriği yok. Eksik metadata açık `null`/boş durumla ifade edilir.
- Public veriden fazlası verilecekse Auth sahibinin (Kerem) onayladığı hedef/scope/caller modeli
  gerekir; mevcut Recommendation izni varmış gibi svc token üretilmez.
- Çağrı request kapsamında, en çok 5 batch / 500 oyun; 3000/5000 ms timeout, redirect/retry yok.

## Kabul testleri

- Arşivli/görünmez oyun, unknown/adult ve unsupported type sızmaz; unavailable enumeration açmaz.
- Mükerrer/100 üstü/geçersiz ID reddi; 401/403 (internal seçilirse), schema ve boyut limitleri.
- Aynı seed setindeki favori/tamamlanan türlerinin Recommendation sonuçlarına ayrı etkisi;
  hiç metadata yoksa sahte benzerlik üretilmemesi; 503'te görünür eksik-veri davranışı.

TASK 2 mevcut uygulaması gerçek tür tercihleriyle sınırlı discovery yapar. Favori/tamamlanan
kimlikleri yalnız tekrar öneriyi dışlamakta kullanılır; bu **REC-02/05 analizinin tamamlanması değildir**.
