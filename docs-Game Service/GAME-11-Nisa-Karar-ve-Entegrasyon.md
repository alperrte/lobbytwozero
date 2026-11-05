# GAME-11 — Nisa ile içerik sahipliği ve entegrasyon kararı

Tarih: 5 Kasım 2026  
İlgililer: Hamza (Game), Nisa (Community / Content)  
Durum: Öneri ve karar formu; Nisa ile henüz ortak karar alınmadı.

## Neden bu karar gerekiyor?

Oyun detayında başarım, rehber, easter egg ve oyuncu deneyimi modülleri planlanıyor.
Game şu anda yalnız başarım tanımlarını yönetiyor. Diğer üç modül için
`GET /api/v1/game/games/{gameId}/modules/{type}` yanıtı
`state=INTEGRATION_PENDING, items=[], totalElements=null` oluyor. Bu yanıt içerik
olmadığı anlamına gelmiyor; veri sahibi ve entegrasyon henüz belirlenmedi.

Güncel Nisa görevlerinde Content haber/duyuru, Community ise oyuna bağlı kullanıcı
gönderilerini kapsıyor (COM-14). Eski genel gereksinim belgesi rehberleri Content'e
bağlıyor. Bu çelişki nedeniyle Content'e kendiliğinden yeni sorumluluk eklemiyoruz.

## Önerilen karar

- Game, kanonik oyun kimliği ve başarım tanımlarının sahibi olarak kalsın.
- Rehber, easter egg ve oyuncu deneyimi Community içinde oyuna bağlı içerik
  türleri olarak değerlendirilsin. Yazarlık, görünürlük, düzenleme, raporlama ve
  moderasyon aynı veri sahibinde kalsın. Bu, COM-14 ile uyumlu bir genişletme
  önerisidir; mevcut Community API'sinin bunları desteklediği iddia edilmez.
- Content'in mevcut haber/duyuru kapsamı korunsun. Nisa uzun biçimli rehberleri
  Content'te tutmayı tercih ederse GUIDES için ayrı sahiplik açıkça kaydedilsin.
- Yeni servis kurulmasın. Game'e UGC tablosu veya başka servisin DB erişimi eklenmesin.
- Kullanıcıya özel görünürlük/engelleme varsa frontend, içeriği doğrudan sahibinin
  Gateway API'sinden kendi oturumuyla alsın. Game bu veriyi public cache'e koymasın.
  Game üzerinden toplama ancak kimlik aktarımı ve görünürlük sözleşmesi ayrıca
  onaylanırsa açılsın; kullanıcıya özel içeriğe servis token'ıyla erişim varsayılmasın.

Bu maddeler Hamza'nın önerisidir; iki tarafın onayı olmadan alınmış karar değildir.

## Nisa'dan beklenen sözleşme

Mevcut uç varsa gerçek yol ve kayıt kullanılmalı. Aşağıdakiler endpoint ilanı değil,
kararlaştırılacak sözleşme gereksinimleridir:

1. Kanonik `gameId`, içerik türü, `page` ve `size` ile sayfalı okuma; maksimum size 50.
2. Eşit tarihlerde içerik UUID'siyle kararlı ikincil sıralama; toplam sayının anlamı.
3. Özet alanları: `id`, `gameId`, `type`, `title`, düz metin `excerpt`, `authorId`,
   `publishedAt`, `updatedAt` ve detay yolu. Spoiler gösterimi gerekiyorsa ayrı alan.
4. Yalnız çağırana görünür ve yayımlanmış içerikler. Gizlenen/silinen/taslak içerik,
   engellenen yazar ve gizli topluluk kuralları liste, detay ve toplam sayıda tutarlı olmalı.
5. İçerik yazma/yönetme uçlarında Auth rolü ve sahiplik kontrolü veri sahibinde olmalı.
   Moderasyon ve raporlama mevcut iş akışına bağlanmalı; Game'e kopyalanmamalı.
6. HTTP 200 + boş liste ile geçici erişilemezlik ayrılmalı. Kimlik/yetki ve kaynak
   hataları 401/403/404; kota 429 + Retry-After; geçici hata 503 ile belgelenmeli.
7. Gövde düz metin olmalı veya zengin metin sanitizasyon sözleşmesi ayrıca tanımlanmalı.
   Dosya ve dış bağlantı kuralları SECURITY.md'ye uymalı.

Game'in mevcut modül DTO'su `List<Achievement>` taşır; rehber nesnesi buraya
doğrudan yerleştirilemez. Karardan sonra türlere uygun DTO ve eski istemcileri
koruyan sürümleme/deprecation planı Hamza tarafından hazırlanacak.

## İş paylaşımı ve kabul

- Nisa: sahiplik, içerik türleri, görünürlük/engelleme, moderasyon ve gerçek API
  sözleşmesini netleştirir; kendi servis kodu, migration'ı ve api.md bölümünü günceller.
- Hamza: Game modül sözleşmesini karara uyarlar; gerekiyorsa uygulama portu,
  RestClient adaptörü, timeout ve durum eşlemesini ekler. İstek başına sınırlı sayıda
  çağrı kullanır; içerik başına HTTP sorgusu oluşturmaz.
- Birlikte: gerçek servislerle görünür içerik, boş sonuç, engellenmiş yazar,
  moderasyonla gizleme, yetkisiz istek, sayfalama ve kesinti senaryolarını doğrularız.
- Sağlayıcı API hazır ve bu kontroller başarılı olmadan GAME-11 bütünüyle tamamlandı
  işaretlenmez. Mevcut INTEGRATION_PENDING davranışı korunur.

## Birlikte doldurulacak karar

- GUIDES sahibi: …
- EASTER_EGGS sahibi: …
- EXPERIENCES sahibi: …
- Okuma yolu: frontend → veri sahibi / Game üzerinden toplama: …
- Gerçek endpoint ve docs/api.md bağlantısı: …
- Görünürlük, engelleme ve spoiler kuralı: …
- Auth/ENV ihtiyacı ve sorumlusu: …
- Kapsam, öncelik ve hedef tarih: …
- Hamza onayı / tarih: …
- Nisa onayı / tarih: …

Kaynaklar: AGENTS.md; docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Hamza.md
(GAME-11); docs/servis-gereksinimleri/LTZ_Servis_Icerikleri_Nisa.md (COM-14 ve
Content kapsamı); docs/api.md (GAME — Genişletilebilir modül okuma).
