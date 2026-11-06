# content-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Nisa.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- **Faz 1 (LTZ-NIS-CONT-01) uygulandı ve doğrulandı (2026-11-06):** izole PostgreSQL ile 170 test geçti; hata ve atlanan test yok. Maven `clean verify` başarılı; toplam satır coverage'ı %96,99, yeni/değişen executable satırlar %97,40; Checkstyle/PMD yeni ihlal kapısı geçti. Ayrıntı: `docs-content/LTZ-NIS-CONT-01-Rapor.md`.
- İzole CI komutunun test/coverage/kalite aşamalarından sonraki **ek Trivy bağımlılık taraması tamamlanamadı** (`Trivy execution failed; raw output withheld`). Bağımlılık taraması **NOT VERIFIED**; CI komutunun bütünü PASS değildir. Bu hata 170 başarılı servis testinden ayrıdır.
- **Faz 2 (LTZ-NIS-CONT-02) uygulandı (2026-11-06) ve teslim öncesi düzeltildi (2026-11-07):** ilk teslimde 290 test, `verify`, Checkstyle/PMD ve SonarQube (0 smell, kapsama %95,5) yeşildi; düzeltmelerden sonra **297 test geçti**, pre-push kontrolleri geçti ve SonarQube Quality Gate geçti (0 bug, 0 güvenlik açığı, 0 hotspot, 0 code smell, kapsama %95,8). Ayrıntı: `docs-content/LTZ-NIS-CONT-02-Rapor.md`.
- **Herkese açık okuma (CONT-02 ürün kararı):** yalnız `GET /contents`, `GET /contents/{id}`, `GET /contents/{id}/images/{imageId}` ve `GET /categories` anonimdir (`SecurityConfig`). Servis katmanı yalnız yayımlanmış ve silinmemiş içeriği verir; başkasının taslağı 404'tür. Geçersiz `Authorization` başlığı bu uçlarda da 401'dir. Gateway tarafında bu yolların anonim geçirilmesi Gateway sahibinin ayarıdır (**NOT VERIFIED**).
- `/internal/**` altında henüz uç yoktur ve tamamı `denyAll`. **Hesap silme ucu yok**: Auth `content` görevini `PENDING` tutar ve hesap silme tamamlanmaz. Ayrı görev: `docs-content/LTZ-NIS-CONT-AUTH-01-Hesap-Silme-Gorevi.md`.

## Kesinleşen kararlar (LTZ-NIS-CONT-01)

- **Resmî duyuru:** `ANNOUNCEMENT` oluşturma ve yayımlama yalnız `ADMIN`. `MODERATOR` yetkisi kapsamında içeriği **yayından kaldırabilir** (CONT-10, sonraki faz); duyuru oluşturamaz. Kayıtlı kullanıcılar kendi `NEWS` içeriklerini yayımlayabilir (CONT-09, sonraki faz).
- **MVP limitleri:** kullanıcı başına saatte 10 yeni içerik (silinenler dahil, silme kotayı sıfırlamaz), aynı anda 20 etkin taslak, içerik başına 6 görsel (2 MiB, 6000 px, 12 milyon piksel).
- **Yayın asgarisi:** başlık en az 5, gövde en az 30 karakter; baştaki/sondaki boşluklar sayılmaz; yalnız yayın sırasında uygulanır, taslak kaydetmeyi engellemez (`ContentPublishRules`). Özet için asgari yok.
- **Görsel depolama:** `content_db` içinde `bytea` (`docs/SECURITY.md` §13'e Content Service kararı olarak işlendi).
- **Silme:** tek transaction'da `contents` satırına silme işareti, `content_translations` ve görsellerin kalıcı silinmesi, parmak izinin nötrlenmesi; saklama süresi 0 gün.
- **Hesap silme politikası:** kullanıcının taslakları, yayımlanmış içerikleri ve önceden silinmiş içeriklere ait kalan kayıtlar kalıcı silinir (yayımladığı haberler de kaldırılır). Uygulama ayrı görevdir.

## Kesinleşen kararlar (LTZ-NIS-CONT-02)

- **Yayın:** ön moderasyon yok; sahibi `POST /{id}/publish` der demez yayındadır. `ANNOUNCEMENT` yalnız `ADMIN` yayımlar. Yayın kuralları tüm dillerdeki çeviriler için denetlenir ve yayımlanmış içeriğin metni değişirken yeniden uygulanır. **Yayımlanmış içerik sahibi tarafından düzenlenebilir** (varsayılan karar; metin, kategori, kaynak, oyun, çeviri, görsel).
- **Öne çıkarma yalnız `ADMIN`** (`PUT/DELETE /{id}/featured`); yalnız yayımlanmış içerik; işlemi yapan yönetici ve zaman kaydedilir.
- **Kategoriler sabit liste** (V103 ile 14 kayıt; platform + konu), yönetim ucu yok; içerik başına en çok 5 kategori (çoka-çok `content_category_assignments`).
- **Arama ILIKE benzeri "içerir"** (`lower(...) like`, `%`/`_`/`!` kaçışlı, bağlı parametre); ayrı search-service yok. İçeriğin tüm dillerinde aranır. V105 ile `pg_trgm` + GIN indeksleri (başlık, özet, gövde) eklendi; sorgu `lower(sütun) like lower(:q)` biçimindedir ve indeksi kullanabilir (gerçek PostgreSQL'de `EXPLAIN` ile doğrulandı; `set enable_seqscan=off` altında üç indeks de planda). 2 karakterlik aramalar trigram üretmez, sıralı taramaya düşer. `create extension pg_trgm` için servis kullanıcısında veritabanı `CREATE` yetkisi gerekir (eklenti PostgreSQL 13+ `trusted`); yetki yoksa DB sahibi eklentiyi bir kez elle açmalıdır.
- **Kaynak bağlantısı** yalnız `https://`; sunucu o adrese asla istek atmaz (SSRF yok); DB'de CHECK ile de zorlanır.
- **Oyun ilişkisi** game-service `POST /api/v1/game/games/batch` ile transaction dışında doğrulanır; game-service cevap vermezse 503 ve ilişki yazılmaz.
- **Silme** artık kategori bağlantılarını ve kaynakları da kalıcı siler, öne çıkarmayı kaldırır.
- **Görsel önbelleği:** tüm görsel yanıtları `Cache-Control: no-store` (önceki `public, max-age=300` kararı geri alındı). Her istekte içeriğin yayımlanmış ve silinmemiş olduğu yeniden denetlenir; silinen/kaldırılan içeriğin görseli aynı URL'de hemen 404 verir (Gateway üzerinden de doğrulandı).

## Açık noktalar

- **Rate limit:** kullanıcı bazlı sınır servis içinde (oluşturma); IP bazlı ve dosya yükleme sınırı Gateway/edge'e aittir (Alper), burada uygulanmadı.
- **Metin düz metindir** (HTML/Markdown yorumlanmaz); zengin metin kararı verilirse allowlist sanitizer gerekir (`docs/SECURITY.md` §5).
- **Parmak izi Türkçe `I`/`ı` farkını eşitlemez** (`Locale.ROOT` küçük harf): "AYNI" ile "Aynı" mükerrer sayılmaz. Kabul edilebilir sınır; gerekirse Türkçe katlama eklenir.
- **Görsel alt metni (erişilebilirlik)** yok; sonraki fazda eklenecek. Çoklu dil (CONT-15) CONT-02 ile geldi.
- **Arama harf katlaması** yalnız veritabanının `lower()` fonksiyonuyla yapılır (sütun ve desen). Gözlem (postgres:17-alpine, `en_US.utf8`, libc): `İ`→`i`, `I`→`i`, `ı` değişmez. `istanbul`/`İstanbul`/`ISTANBUL` eşleşir; **noktasız `ı` ile `I` eşleşmez** (`ışık` ≠ `Işık`/`IŞIK`). Önceki sürümde Java `toLowerCase` ile ikinci bir katlama vardı ve `İ` aramaları kaçırıyordu (`İ` → `i` + birleştirici nokta); kaldırıldı. Farklı collation/ICU kullanan bir sunucuda davranış değişebilir.
- **Kullanım hakkı (CONT-16):** kaynak adı/bağlantısı/tarihi tutulur ve gösterilir; içerik kullanım hakkı denetimi bir politika/moderasyon konusudur, serviste otomatik denetim yoktur.
- **Olay/outbox yok:** yayın, öne çıkarma ve silme olayları üretilmez (broker kararı bekliyor); notification-service entegrasyonu yok.
- **Eşzamanlı oluşturmada kota** birkaç istek payıyla aşılabilir (sayım-sonra-yaz); mükerrer ise veritabanı indeksiyle kesin engellenir.
- **Taslak listesi** (`GET /contents/mine`) yok; yayın/akış fazıyla birlikte önerilir.
