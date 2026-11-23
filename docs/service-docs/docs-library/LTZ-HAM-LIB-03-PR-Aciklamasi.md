# LTZ-HAM-LIB-03 — Kütüphane listeleme, filtreleme ve istatistik

* JWT sahibinin koleksiyonunu bayrakları ve oyun özetleriyle sunan GET /api/v1/library/entries eklendi.
* Favori/bitirildi filtrelerinde isteğe bağlı true/false ve birlikte AND davranışı uygulandı.
* Eklenme tarihi ve oyun adı için ASC/DESC, deterministik ikincil sıralama ve page/size desteği eklendi.
* Oyun adına sıralama bütün filtrelenmiş kümede, sayfalama öncesinde yapılır; eksik adlar iki yönde de sona yerleşir.
* Liste/toplam yerel snapshot tutarlılığı ve taşma güvenli offset hesabı sağlandı; Faz 2 liste DTO'ları korundu.
* GET /api/v1/library/entries/statistics ile toplam/favori/bitirildi sayıları tek owner SQL aggregate üzerinden sunuldu.
* Mevcut Game public batch API'siyle 50 kimlik sınırı, tekilleştirme, DTO eşleme ve metadata doğrulaması tamamlandı.
* 50 oyunluk tarih sayfası tek batch kullanır; ad sıralaması K kayıt için en fazla ceil(K/50) batch gerektirir.
* Katalog hatasında yerel kayıtlar korunur; unavailable/geçici hata durumları ve metadataPartial yanıta eklenir. İlk geçici hatadan sonra kalan batch çağrıları durur.
* Game HTTP çağrıları DB transaction'ı dışında yapılır; token aktarımı ve otomatik retry yoktur.
* PostgreSQL entegrasyon, JWT/sahiplik, sıralama/sayfalama, istatistik ve batch hata toleransı testleri genişletildi.
* Maven clean verify: 78 PASS, failure/error/skipped 0; LINE %98,84; Checkstyle/PMD 0 ihlal.
* ltz-dev Library imajı yenilendi; Library/Gateway ve mevcut Auth/Game sağlık kontrolleri dahil 36/36 HTTP PASS.
* Pozitif canlı kullanıcı E2E NOT VERIFIED; başka servislerde test hesabı/katalog verisi oluşturulmadı.
* Yeni migration, ENV veya bağımlılık eklenmedi; V41 ve mevcut veritabanı geçmişi korundu.
* docs/api.md LIBRARY sözleşmeleri ve Faz 3 sonuç raporu güncellendi.
* Faz 3: 6/6; genel ilerleme: 13/18.
