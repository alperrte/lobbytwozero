# Game Service — güncel kalan işler

Tarih: 5 Kasım 2026 · Sahibi: Hamza. Bu belge mevcut kodu esas alır; eski faz
raporlarındaki bekleme ifadeleri tarihsel olabilir. API kaydı: `docs/api.md` → GAME.

## 1. GAME-04 / GAME-05 / GAME-15 — Review puanları

Kod hazır: AuthServiceTokenClient, ReviewRatingClient, V172 puan projeksiyonu,
kalıcı tarama/kiralama, minRating, RATING sıralaması ve rating durumları mevcut.
Game toplu POST çağırmaz; imleçli GET dışa aktarmayı tarar. Görünüm, filtre ve
sıralama aynı yerel projeksiyondan gelir. İlk başarılı taramadan önce puan
UNAVAILABLE; puan filtresi/sıralaması RATING_INTEGRATION_PENDING verir.

Auth'un mevcut çiftinde `rating-summaries:read,game-references:read` vardır;
ikincisi tek başına puan okuma yetkisi değildir. Game'in gerçek-Auth testindeki
tek kapsam varsayımı kaldırıldı. Alıcı sözleşmesinin uyumu Game kabul betiğinde
sınanır; Review test dosyalarında bu göreve ait değişiklik bırakılmadı.

Gerçek servis kabulü için `node ops/game-review-e2e.mjs` eklendi. Üç ayrı geçici
PostgreSQL ve gerçek Auth/Game/Review jar'ları kullanır; DEV veya ENV dosyası
okumaz. Kullanıcı/admin ve D4 kimlikleri yalnız fixture anahtarıyla imzalanır;
Game → Review servis token'ı gerçek Auth HTTP üreticisinden gelir. Bu test gerçek
kullanıcı login'i veya Auth silme worker'ının tüm orkestrasyonunu kanıtlamaz.
İlk gerçek servis koşusunda 11 kabul kontrolü geçti; Windows süreç ağacı temizliği düzeltildi. Çalıştırma sınırları api.md GAME puan projeksiyonu kaydındadır.

## 2. GAME-17 / GAME-23 — IGDB detay ve dil

Adaptör, Steam AppID eşlemesi, dil türleri, provenance ve manuel kilit koruması
mevcut. Kimlik bilgilerinin ikisi de yoksa IGDB kurulmaz. Yeni
IgdbEnrichmentIntegrationTest gerçek adaptör → yenileme → PostgreSQL → public API
zincirini, UNKNOWN dil semantiğini, TTL'yi, manuel kilidi ve kesintiyi sınar;
yalnız dış IGDB/Twitch sunucusu sahtedir.

Canlı IGDB'nin Game uygulaması üzerinden doğrulanması hâlâ ayrı iştir. Mevcut
DEV kataloğu bu incelemede 0 kayıt, IDLE ve lastSuccessfulAt=null döndü. Agent
sürecinde IGDB kimlik bilgileri yok; gerçek .env dosyaları okunmadı. Önceki salt
okunur IGDB doğrulaması, yeni uygulama/DB zincirinin canlı kabulü değildir.
Gerçek sağlayıcı erişimi kullanıcı ortamında sağlanıp kontrollü pilot yapılmalıdır.

## 3. GAME-11 — ortak içerik kararı bekleniyor

Başarım tanımları hazır. GUIDES/EASTER_EGGS/EXPERIENCES sahibinin API'si
kararlaştırılmadığından INTEGRATION_PENDING korunur; boş liste kabul edilmez.
Nisa'ya iletilecek öneri ve karar formu:
[GAME-11-Nisa-Karar-ve-Entegrasyon.md](GAME-11-Nisa-Karar-ve-Entegrasyon.md).
Kullanıcı talebiyle dosyanın bir kopyası masaüstüne kondu; Nisa'ya gönderilmedi.
Community önerisi henüz ortak karar değildir. Nisa'nın servisleri değiştirilmedi.

## 4. GAME-24 / GAME-04 indirim filtresi — IsThereAnyDeal seçildi

Kullanıcı bu oturumda ITAD'ı seçti; eski erteleme kararı bu seçimle değişti.
ItadPriceClient resmî toplu Steam kimliği eşleme ve fiyat uçlarını kullanır.
V173 fiyat snapshot, pazar yenileme durumu ve kalıcı tek worker kiralaması ekler.
Yeni public GET /api/v1/game/games/prices en çok 50 oyun için yerel fiyat sunar;
keşif/arama discountedOnly filtresini aynı SQL'de ve yalnız güncel tekliflerle uygular.
Ülke/para birimi açıkça belirlenir; ilk arka plan pazarı TR/USD, dönüşüm yoktur.
NO_OFFER, UNAVAILABLE ve STALE fiyat=0 veya ücretsiz oyun anlamına gelmez.

Anahtar boşken dış fiyat çağrısı yoktur. Kullanıcı yalnız boş ITAD_API_KEY şablon
satırını ve game-service Compose aktarımını onayladı; gerçek ENV okunmadı/değişmedi.
Gerçek ITAD API anahtarı ve LTZ kullanım uygunluğu teyidi bekleniyor. Canlı ITAD,
frontend fiyat gösterimi/atıf ve gerçek ortama V173 uygulanması NOT VERIFIED.
Kodun varlığı canlı fiyatın hazır olduğu anlamına gelmez.

## 5. GAME-26 — kimlik mutasyonu güvenlik kapısı

Manuel düzeltme kilitleri, audit ve arşivleme/geri alma mevcut kabul kapsamını
karşılar. HTTP reassign yalnız no-op durumda 200; gerçek reassign/split
409 FOREIGN_REFERENCE_SAFETY_UNVERIFIED verir. Auth'un okuma kapsamı mevcut olsa
da tüketicilerde eşzamanlı yazmaları durduracak protokol hazır değildir.

Tam tüketici envanteri, prepare/commit/telafi ve Auth mutasyon kapsamlarını içeren
[karar taslağı](../docs/decisions/0002-oyun-dis-kimlik-duzeltme.md) hazırlandı.
Bu protokol yalnız Library/Review sayımı eklenerek güvenli hale gelmez; başka
sahiplerin servisleri de gameId saklar. Koruma kaldırılmadı ve gerçek taşıma
uygulanmış sayılmadı. Kimlik mutasyonu için migration eklenmedi; fiyat V173 sonrası boş Game aralığı V174–V180.

## 6. Diğer sınırlar

- GAME-07/12/13/14 yerel iş akışları ve önceki canlı Bearer kontrolleri raporlarda
  mevcut. Bu çalışma tüm Gateway çerez yolculuklarını yeniden doğrulamadı.
- Game'in trusted-proxies listesi boş; Gateway'den gerçek istemci IP aktarımı ve
  Game tarafındaki güvenilir proxy ayarı birlikte doğrulanmalı. Gateway'de güncel
  CORS/çerez→Bearer/kimlik başlığı temizliği kodu var; eski “hiç uygulanmadı”
  ifadeleri geçerli değildir. Ortak Gateway kodu bu çalışmada değiştirilmedi.
- Frontend yönetim/geliştirici/moderasyon ekranları, Admin/Notification iş akışları
  bu backend kabulünün tamamlandığı anlamına gelmez; ilgili sahiplik kararı gerekir.
- Toplu kart en fazla 50 kimlik alır; LTZ puanı taşımaz. Puan keşif listelerindedir.
- GAME-25 büyük katalog ve puan performans testleri mevcut; üretim garantisi yok.

## 7. Güvenli doğrulama

Genel kalite komutu: `node pre-push/ci/backend.mjs --modules services/game-service,services/review-service`.
Windows'ta daraltılmış ortam Docker Compose eklentisini bulamıyorsa yerel çalıştırma
uyarlaması gerekir. Maven Central 429 bu oturumda yerel Maven önbelleğiyle aşıldı;
bağımlılık sürümü değiştirilmedi. Başarısız/atlanmış test PASS sayılmaz.

Gerçek servis kabulü: güncel Auth/Game/Review jar'ları hazırken
`node ops/game-review-e2e.mjs`. Yalnız kendisinin etiketlediği geçici konteynerler
silinir; DEV/prod veritabanları ve çalışan servisler değiştirilmez.

## 8. Son doğrulama — 5 Kasım 2026

- Game: **425 test**, failure/error/skipped 0/0/0; Maven clean verify PASS.
  JaCoCo LINE **%98,00**, değişen executable satırlar **%98,04**; merkezi coverage
  ve Checkstyle/PMD yeni ihlal kapısı PASS. Günlük: logs/game-final-verify.log.
- Review belgeleri, testi ve api.md REVIEW bölümüne bu oturumda yapılan kapsam
  dışı değişiklikler kullanıcı uyarısıyla geri alındı. Review için bu görevde
  geliştirme veya güncel test sayısı iddiası tutulmaz.
- Gerçek Auth/Game/Review uygulamaları + ayrı geçici PostgreSQL: **11/11** kabul
  kontrolü PASS. Son koşuda 3 test süreç ağacı ve 3 konteyner temizlendi.
  Kullanıcı/admin/D4 fixture token'ı ile gerçek Auth'tan alınan servis token'ı
  ayrımı yukarıda belirtilmiştir; gerçek login veya tam hesap silme Saga'sı değildir.
- Node fixture kontrolleri: **10/10 PASS**. Secretsiz Compose şablonu, yalnız
  komuta verilen sahte Auth yer tutucularıyla config --quiet doğrulamasını geçti.
- 100.000 oyun: liste p95 43,30 ms, arama 11,55 ms, birleşik filtre 142,70 ms,
  eşzamanlı arama 17,72 ms; puanlı sorgularda en yüksek p95 137,84 ms. Test
  ortamı önceki GAME-25 ile aynı; üretim garantisi ve yeni fiyatlar için hacim SLO'su değildir.
- **Tam CI kapısı PASS değildir:** Trivy bağımlılık taraması `Trivy execution failed;
  raw output withheld` hatasıyla tamamlanamadı. Uygulama testlerinin başarılı olması
  HIGH/CRITICAL bağımlılık taramasını geçmiş sayılmaz; scanner/registry erişimi ayrıca çözülmelidir.
- Auth kaynağı değiştirilmedi. İmaj hazırlamak için yapılan ilk koşuda bir Mailpit
  testi 15 sn timeout verdi; tekrarında **533/533 test PASS** oldu. O tekrarın
  coverage adımı çalışma sürerken değişen global Git bağlamını reddetti; bu koşu
  tam Auth verify PASS olarak raporlanmaz. Game ve Review'ın son kapsamları ayrı doğrulandı.
