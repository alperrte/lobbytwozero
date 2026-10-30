* Library Service Faz 1: kullanıcı JWT'si ve doğrulanmış kanonik gameId ile koleksiyona ekleme/kaldırma eklendi.
* V41 library_entries migration'ı, kullanıcı/oyun UNIQUE kısıtı ve atomik idempotent upsert ile eşzamanlı işlemler tekilleştirildi.
* Favori/bitirildi aksiyonunda yalnız hedef bayrağı açan otomatik kayıt uçları eklendi; Faz 2 geri alma/listeleme kapsamı açılmadı.
* Library sınırında RS256/JWKS, at+jwt, issuer/audience/zaman/rol/sahiplik kontrolleri uygulandı; Game kesintisinde kayıt yazılmıyor.
* Mevcut D4 hesap silme akışı gerçek koleksiyon verisini tek transaction'da temizleyecek şekilde tamamlandı.
* docs/api.md LIBRARY sözleşmeleri ve docs-library Faz 1 sonuç raporu güncellendi.
* Son kontrolde, erken başlayan transaction'ın updated_at değerini added_at öncesine çekebildiği hata regresyon testiyle kanıtlanıp yalnız SQL zaman ifadesinde düzeltildi.
* Doğrulama: 45/45 Maven testi, 0 failure/error/skipped, %98,13 satır kapsamı ve merkezi coverage PASS; Maven Checkstyle/PMD raporlarında 0 ihlal.
* ltz-dev Library imajı güncellendi; mevcut V41 ve migration geçmişi korundu, Flyway/Hibernate doğrulaması başarılı.
* Gerçek Auth oturumları → Gateway → Library → Game → PostgreSQL E2E kontrolü 62/62 PASS; iki kullanıcı izolasyonu, otomatik kayıt, tekrar ve gerçek Game kesintisinde 503/no-write sınandı.
* Test Library/Game verileri ve yalnız test e-postaları temizlendi. İki sentetik hesap Auth API'sinden silme sürecine alındı; mevcut 14 günlük bekleme politikası korundu.
* Diğer servislerin backend/frontend kodları değiştirilmedi; hiçbir frontend dosyasına dokunulmadı.
* Pre-push ve SonarQube bu turda çalıştırılmadı; kullanıcı tarafından manuel gerçekleştirilecektir. Bunlar Codex geliştirme eksiği değildir.
* Faz 1: 5/5; genel: 5/18. Faz 2'ye teknik geçiş engeli saptanmadı; açık kullanıcı onayı bekleniyor.

* Faz 1 — Koleksiyon Ekle/Kaldır ve Veri Bütünlüğü
* [x] LIB-01 — Kişisel oyun kütüphanesi
* [x] LIB-02 — Koleksiyondan oyun kaldırma
* [x] LIB-12 — Mükerrer işlem kontrolü
* [x] LIB-13 — Kimlik ve oyun doğrulama
* [x] LIB-15 — Otomatik koleksiyon kaydı

* Faz 2 — Favori ve Bitirildi İşlemleri
* [ ] LIB-03 — Favori oyunlar
* [ ] LIB-04 — Bitirilen oyunlar

* Faz 3 — Kütüphane Listeleme, Filtreleme ve İstatistik
* [ ] LIB-05 — Kütüphane listeleme
* [ ] LIB-06 — Kütüphane filtreleme
* [ ] LIB-07 — Kütüphane sıralama
* [ ] LIB-08 — Sayfalama
* [ ] LIB-09 — Kütüphane istatistikleri
* [ ] LIB-16 — Toplu oyun özeti entegrasyonu

* Faz 4 — Gizlilik ve Servis Entegrasyonları
* [ ] LIB-10 — Profil entegrasyonu
* [ ] LIB-11 — Gizlilik kontrolleri
* [ ] LIB-14 — Recommendation entegrasyonu
* [ ] LIB-18 — Yetki, gizlilik ve tutarlılık testleri

* Faz 5 — Arşivlenen Oyunlarda Dayanıklılık
* [ ] LIB-17 — Katalog değişikliklerine dayanıklılık
