# content-service: notlar (elle tutulur)

> Bu dosya **elle** yazılır ve otomatik üretilen `README.md` tarafından ezilmez. Sahip: Nisa.
> Servise özgü bilinen durumlar, test/ortam notları, ertelenen güvenlik kararları buraya yazılır.

## Bilinen durumlar

- **Faz 1 (LTZ-NIS-CONT-01) uygulandı ve doğrulandı (2026-11-06):** izole PostgreSQL ile 170 test geçti; hata ve atlanan test yok. Maven `clean verify` başarılı; toplam satır coverage'ı %96,99, yeni/değişen executable satırlar %97,40; Checkstyle/PMD yeni ihlal kapısı geçti. Ayrıntı: `docs-content/LTZ-NIS-CONT-01-Rapor.md`.
- İzole CI komutunun test/coverage/kalite aşamalarından sonraki **ek Trivy bağımlılık taraması tamamlanamadı** (`Trivy execution failed; raw output withheld`). Bağımlılık taraması **NOT VERIFIED**; CI komutunun bütünü PASS değildir. Bu hata 170 başarılı servis testinden ayrıdır.
- Bu fazda **anonim (herkese açık) uç yoktur**; tüm uçlar kimlik ister. Genel okuma CONT-01/02 fazında gelecek ve `SecurityConfig` o zaman bilinçli olarak genişletilecek.
- `/internal/**` altında henüz uç yoktur ve tamamı `denyAll`. **Hesap silme ucu yok**: Auth `content` görevini `PENDING` tutar ve hesap silme tamamlanmaz. Ayrı görev: `docs-content/LTZ-NIS-CONT-AUTH-01-Hesap-Silme-Gorevi.md`.

## Kesinleşen kararlar (LTZ-NIS-CONT-01)

- **Resmî duyuru:** `ANNOUNCEMENT` oluşturma ve yayımlama yalnız `ADMIN`. `MODERATOR` yetkisi kapsamında içeriği **yayından kaldırabilir** (CONT-10, sonraki faz); duyuru oluşturamaz. Kayıtlı kullanıcılar kendi `NEWS` içeriklerini yayımlayabilir (CONT-09, sonraki faz).
- **MVP limitleri:** kullanıcı başına saatte 10 yeni içerik (silinenler dahil, silme kotayı sıfırlamaz), aynı anda 20 etkin taslak, içerik başına 6 görsel (2 MiB, 6000 px, 12 milyon piksel).
- **Yayın asgarisi:** başlık en az 5, gövde en az 30 karakter; baştaki/sondaki boşluklar sayılmaz; yalnız yayın sırasında uygulanır, taslak kaydetmeyi engellemez (`ContentPublishRules`). Özet için asgari yok.
- **Görsel depolama:** `content_db` içinde `bytea` (`docs/SECURITY.md` §13'e Content Service kararı olarak işlendi).
- **Silme:** tek transaction'da `contents` satırına silme işareti, `content_translations` ve görsellerin kalıcı silinmesi, parmak izinin nötrlenmesi; saklama süresi 0 gün.
- **Hesap silme politikası:** kullanıcının taslakları, yayımlanmış içerikleri ve önceden silinmiş içeriklere ait kalan kayıtlar kalıcı silinir (yayımladığı haberler de kaldırılır). Uygulama ayrı görevdir.

## Açık noktalar

- **Rate limit:** kullanıcı bazlı sınır servis içinde (oluşturma); IP bazlı ve dosya yükleme sınırı Gateway/edge'e aittir (Alper), burada uygulanmadı.
- **Metin düz metindir** (HTML/Markdown yorumlanmaz); zengin metin kararı verilirse allowlist sanitizer gerekir (`docs/SECURITY.md` §5).
- **Parmak izi Türkçe `I`/`ı` farkını eşitlemez** (`Locale.ROOT` küçük harf): "AYNI" ile "Aynı" mükerrer sayılmaz. Kabul edilebilir sınır; gerekirse Türkçe katlama eklenir.
- **Alt metin (erişilebilirlik) ve çoklu dil** (CONT-15) bu fazda yok; görsellerde `alt` alanı sonraki fazda eklenecek.
- **Eşzamanlı oluşturmada kota** birkaç istek payıyla aşılabilir (sayım-sonra-yaz); mükerrer ise veritabanı indeksiyle kesin engellenir.
- **Taslak listesi** (`GET /contents/mine`) yok; yayın/akış fazıyla birlikte önerilir.
