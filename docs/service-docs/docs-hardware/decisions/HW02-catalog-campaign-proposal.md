# LTZ-ALP-HW-02 katalog ve kampanya sözleşmesi — ONAYLANDI

Tarih: 2026-10-21. Kaynak: master §0.4, SECURITY.md §4 ve §19.
Bu belge uygulanmış API veya gerçek Auth sözleşmesi değildir.

## Güvenlik sınırı

- `/api/v1/hardware/catalog/brands`, `/catalog/products` ve `/campaigns` GET uçları
  yalnız doğrulanmış typed actor'a açık olacak; public endpoint eklenmeyecek.
- Marka, ürün ve kampanya oluşturma/güncelleme/arşivleme yalnız typed actor ve
  `hardware:catalog:write` uygulama izniyle yapılacak. Normal kullanıcı küresel
  kataloğu değiştiremeyecek. İzin adı JWT claim'i veya Auth rol adı değildir;
  gerçek Auth rol/claim → izin eşlemesi ayrıca mutabakat bekler (DEFERRED_AUTH).
- Üretimde doğrulayıcı eklenmeyecek; istekler fail-closed reddedilecek. Test aktörü
  ve izni yalnız testlerde sağlanacak. CSRF, 16 KiB gövde sınırı ve no-store korunacak.
- Yönetim mutasyonları aynı transaction'da hardware_db içinde actor UUID, eylem,
  hedef UUID, gerekçe ve UTC zamanla append-only audit kaydı oluşturacak.
- Silme API'si arşivleme uygulayacak; eski profil bileşenleri ve katalog referansları
  korunacak. Arşivli modeller/markalar yeni seçim ve normal listelerde görünmeyecek.

Alternatif: yönetim API'lerini gerçek Auth sözleşmesine kadar bloke tutmak.
Normal kullanıcıya yazma yetkisi vermek güvenli bir alternatif değildir.

## İş sözleşmesi ve kaynak

- Katalog modeli markadan bağımsız kalacak; bir modele sıfır veya birden fazla
  opsiyonel marka ilişkilendirilebilecek. Manuel profil girişi korunacak.
- Arama: model metni, kategori, opsiyonel marka, kayıtlı sayısal teknik alanların
  alt/üst sınırları ve kayıtlı metinsel teknik alanlar; sınırlı sayfalama ve sabit
  sıralama. Bilinmeyen teknik değer eşleşmiş sayılmayacak.
- Kampanyalar yalnız yetkili kişinin girdiği yerel kayıtlar olacak; scraping,
  harici API, otomatik fiyat doğrulaması veya fiyat/güncellik uydurma yapılmayacak.
- Ürün, başlık, kaynak adı ve HTTPS kaynak URL'si, başlangıç/bitiş, gözlem zamanı,
  ISO 4217 para birimi, orijinal ve indirimli pozitif fiyat zorunlu olacak.
  İndirimli fiyat orijinalden düşük olacak; yüzde bu iki fiyattan hesaplanacak.
  Kur dönüşümü yapılmayacak. URL yalnız veri olarak saklanıp dönecek; sunucu URL'ye
  istek atmayacak. URL'de kimlik bilgisi/fragment ve HTTPS dışı şema kabul edilmeyecek.
- Normal kampanya okuması yalnız arşivlenmemiş aktif ürüne bağlı, başlangıcı gelmiş
  ve bitişi geçmemiş kayıtları döndürecek. Gözlem zamanı yanıtta gösterilecek;
  kayıt anlık fiyat garantisi veya otomatik doğrulanmış veri diye sunulmayacak.
- Yeni bağımlılık, ENV, Compose, Gateway veya başka servis değişikliği gerekmiyor.
  Mevcut V1/V2 değişmeden, hardware V3 ve gerektiğinde V4 migration'ı eklenecek.

## Onay durumu

Kullanıcı 2026-10-21 tarihinde “Önerilen güvenlik sınırını onaylıyorum” ve
“Yerel kampanya sözleşmesini onaylıyorum” yanıtlarıyla iki kararı da onayladı.
Gerçek Auth sözleşmesi ve rol/claim eşlemesi bu onayla uygulanmış sayılmaz.
