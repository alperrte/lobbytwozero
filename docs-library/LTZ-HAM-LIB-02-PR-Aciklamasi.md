* Library Service LIB-03/04 kapsamında favoriden çıkarma, bitirildi durumunu geri alma ve iki owner listesi eklendi.
* Mevcut PUT otomatik kayıt uçları korundu; DELETE yalnız hedef bayrağı değiştirir, koleksiyon ve diğer bayrak kalır.
* Tekrar ve olmayan kayıtta 204/no-op davranışı; atomik UPDATE, version ve timestamp tutarlılığı sağlandı.
* JWT/rol/sahiplik denetimleri yeni yollar için korundu; liste sınırı 50 ve sabit addedAt/id sırası uygulandı.
* Liste/toplam aynı REPEATABLE_READ snapshot'ında okunur; Game HTTP/N+1 çağrısı yapılmaz, entity yerine DTO dönülür.
* Yeni migration, ENV veya bağımlılık yoktur; V41 ve uygulanmış şema geçmişi korunmuştur.
* Maven clean verify: 58 PASS, failure/error/skipped 0, LINE %98,40; Checkstyle/PMD raporlarında 0 ihlal.
* ltz-dev Library imajı güncellendi; gerçek HTTP sağlık/401/403/CSRF kontrolleri 32/32 PASS.
* Pozitif canlı kullanıcı E2E NOT VERIFIED: mevcut katalog boş; kullanıcı sınırları gereği başka servis kalıcı verisi oluşturulmadı.
* Yalnız Library backend, docs-library ve docs/api.md LIBRARY bölümü değişti; diğer backend/frontend ve ortak yapılandırma korunmuştur.
* Pre-push ve SonarQube çalıştırılmadı; kullanıcı tarafından manuel gerçekleştirilecektir.
* Faz 2: 2/2, genel: 7/18; Faz 3 için açık kullanıcı onayı bekleniyor.

* Faz 1 — Koleksiyon Ekle/Kaldır ve Veri Bütünlüğü
* [x] LIB-01 — Kişisel oyun kütüphanesi
* [x] LIB-02 — Koleksiyondan oyun kaldırma
* [x] LIB-12 — Mükerrer işlem kontrolü
* [x] LIB-13 — Kimlik ve oyun doğrulama
* [x] LIB-15 — Otomatik koleksiyon kaydı

* Faz 2 — Favori ve Bitirildi İşlemleri
* [x] LIB-03 — Favori oyunlar
* [x] LIB-04 — Bitirilen oyunlar

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
