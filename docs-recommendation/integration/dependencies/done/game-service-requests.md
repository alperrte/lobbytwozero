# TASK 2 — Game toplu recommendation metadata talebi

Tarih: 2026-11-14 · Sahip: Hamza · Tüketici: Alper / recommendation-service
Durum: KAPANDI (2026-11-18) — Hamza GAME-15 ile herkese açık `POST /api/v1/game/games/recommendation-metadata/batch` ucunu teslim etti (tür/platform, adult/visible, alan bazlı `{value,state}` sistem gereksinimi, unavailable). Recommendation REC-06/16 bu ucu servis token'ı olmadan tüketir (`HttpGameMetadataSource`); Auth `game-metadata:read` kapsamı ve Game iç ucu kaldırıldı. Ayrıntı docs/api.md GAME-15 ve RECOMMENDATION kayıtları. Aşağıdaki metin tarihsel taleptir.

## Doğrulanmış mevcut durum

`POST /api/v1/game/games/batch` yalnız `GameCardResponse` verir: kanonik gameId, başlık,
origin/completeness/gameType, dış referanslar ve coverUrl. Tür/platform terimleri ve sistem
gereksinimleri kartta yoktur. `GET /api/v1/game/{gameId}/metadata` tek oyun içindir.
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

## TASK 3 / REC-06 — gereksinim batch ihtiyacı (2026-11-15)

Durum: OPEN / katalog genelinde donanıma uygun oyun önerisi ve REC-16 sistem filtresi PARTIAL.
Tek oyun metadata ucunda minimum/recommended `cpu`, `gpu`, `ramMb`, `storageMb`, `os` vardır;
TASK 3 bu gerçek uçla yalnız seçilen bir oyunu değerlendirir. Tüm adaylar için bu ucu ayrı ayrı
çağırarak N+1 oluşturulmadı. Task 2'deki kart batch yanıtı hâlâ requirement taşımaz.

Yukarıdaki batch önerisine şu alanlar eklenmelidir: kanonik gameId; minimum ve recommended
seviye/platform; CPU/GPU karşılaştırılabilir kimlik veya açık `UNCOMPARABLE`; RAM/depolama birim
sözleşmesi; varsa VRAM ve OS/driver gereksinimleri; alan bazlı eksik veri durumu ve provenance.
Metin CPU/GPU modellerine uydurma score/benchmark atanması istenmiyor. Desteklenen teknik
eşdeğerlik/sıralama verisinin sahibi ve kaynağı açık olmalı; yokluğunda UNKNOWN korunur.

Kabul: bounded 1–100 ID batch, canonical IDs, visible/adult/type politikaları, duplicate/unknown
ID/schema testleri; minimum ve recommended ayrımı; eksik CPU/GPU/RAM/free-space/OS alanında
tam uygunluk iddiası olmaması. Endpoint/izin modelini Game sahibi kesinleştirir; izinsiz kod/ENV
değişikliği yapılmadı. Başka servis DB'si okunmadı.
