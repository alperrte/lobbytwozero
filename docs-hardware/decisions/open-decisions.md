# Hardware açık kararları

## HW-01 güvenlik sınırı — ONAYLANDI

Kaynak: master §0.4, §3 ve SECURITY.md §19. Kullanıcıya 2026-10-21 tarihinde soruldu.
Kullanıcı aynı oturumda “Bu sınırı onaylıyorum” yanıtını verdi; uygulama başladı.

Öneri: `/api/v1/hardware/profiles` kapsamındaki tüm iş uçları Owner/protected;
istemciden userId/rol kabul edilmez. Güvenilir aktör uygulama portundan alınır.
Auth sözleşmesi gelmeden üretimde doğrulayıcı eklenmez; HTTP iş istekleri reddedilir.
Test kimliği yalnız testlerden gelir. CSRF korunur; cookie, CORS, JWT claim ve rol
formatı belirlenmez. Mevcut health probe erişimi ayrıca korunur.

Alternatif: Gerçek Auth sözleşmesini beklemek. Bu, yerel HTTP/güvenlik testlerinin
ilerlemesini durdurur. Header veya sahte token bypass'ı güvenli alternatif değildir.

## Test bağımlılıkları — ONAYLANDI

Gerekirse yalnız test kapsamında parent tarafından yönetilen WebMvc test ve
PostgreSQL Testcontainers bağımlılıkları. Alternatif mevcut test/JDBC araçlarıdır.
Üretim bağımlılığı, ENV veya Compose değişikliği önerilmiyor.
Kullanıcı “Test bağımlılıklarını onaylıyorum” dedi. Parent tarafından yönetilen
WebMvc test ve testcontainers-postgresql (2.0.5) test scope ile eklendi.
Yerel yayımlanmış POM metadata'sı: Spring Boot WebMvc test Apache License 2.0;
Testcontainers MIT. Sürümler mevcut parent/BOM'dan gelir; üretim runtime'ına eklenmez.
Bu lisans/scope kontrolü bağımlılık güvenlik taraması veya sıfır zafiyet iddiası değildir.

## Gerçek Auth entegrasyonu — DEFERRED_AUTH

Issuer, audience, algoritma, JWKS/anahtar, UUID claim, roller, hesap durumu/iptal,
cookie taşıma ve CSRF protokolü Kerem ile mutabakat gerektirir. Sözleşme tahmin edilmez.
Yerel sahiplik testi gerçek token/E2E doğrulaması değildir.

## Final kalite komutları — DOĞRULANDI; PRE-PUSH PASS / SONAR PASS

Master §8: final pre-push ve Sonar taramasını kullanıcı çalıştırır veya ajan için
açık izin verir. İki kapı geçmeden commit mesajı ve PDA kapatma önerisi üretilmez.
Kod/doküman ve clean verify hazırlandıktan sonra 2026-10-21 tarihinde çalıştırma izni
soruldu. Bekleyen komutlar `.\pre-push\pre-push.cmd hardware-service` ve
`.\sonarqube\sonar.cmd scan dev`. İlki hardware modülünü, mevcut Sonar betiği tüm
backend'i kapsar. Yerel dal `chore/architecture`; betik ayrı Sonar branch parametresi göndermez.
Profil/token değerleri açılmaz/yazdırılmaz; betik tarafından içeride kullanılması istenir.

Kullanıcı “İki kalite komutunu çalıştır” onayını verdi. Pre-push hardware-service
2026-10-21 00:48:13 +03:00 tarihinde exit 0: 58 PASS, %96,68,
`BACKEND TEST CHECK PASSED`. Sonar scan dev 00:48:21 +03:00 tarihinde exit 1:
`SonarQube Access FAIL`, `fetch failed`; analiz ve gate SKIPPED. localhost:5199
salt okunur kontrolde erişilemiyor; `ltz-sonar` Compose projesine ait container bulunamadı.
Sunucu başlatılmadan tekrar taramak aynı bağlantı engelini çözmez.

Kullanıcı sonraki yanıtta önce Sonar token kurulumu gerektiğini bildirdi; `up dev`
başlatma onayı olarak yorumlanmadı. Token kullanıcı tarafından arayüzde oluşturulup
yerel `.env.dev` içindeki mevcut `SONAR_TOKEN` alanına kaydedilecek. Betik yalnız
scanner değil ölçüm/gate Web API çağrıları da yaptığı için User Token önerildi;
hesapta hedef projeye Browse + Execute Analysis yeterli olacak şekilde yetki sınırlandırılır.
Token değeri/gizli dosyalar ajan tarafından okunmaz. Hazır bildirimi sonrasında
önceden onaylı scan komutu tekrar çalıştırılır.

Token kaydedildi bildirimi sonrasında tarama tekrarlandı: Sonar Access PASS,
Backend Reports FAIL (16 servis contextLoads; PostgreSQL kapalıyken bağlantı reddi,
başlatıldıktan sonra kimlik doğrulama hatası). Ortak loader'ın uygulama DB ayarlarını
profilden Maven'e aktarmadığı kaynak kodda doğrulandı. Mevcut geliştirme PostgreSQL
başlatıldı ve healthy; secret değerleri okunmadı. Yeni açık karar:
[HW01-QG-01 ortak Maven test ortamı](quality-runner-env-proposal.md).
Runner veya başka servis koduna onaysız değişiklik yapılmadı.

Güncel sonuç: kullanıcı dar runner düzeltmesini onayladı; 44 regresyon testi geçti.
Son kod pre-push 58 test/%96,69 ile PASS (01:13:28 +03:00); Sonar bütün backend'de
76 test ve A/A/A + Quality Gate PASS (analiz 9be6f62f-5bf5-44f8-9d1d-53ca6d9350fc,
01:16:26 +03:00). Ek karmaşıklık bulgusu kapandı; açık kritik/vulnerability/hotspot 0.
Bu kalite kararı kapandı. Gerçek Auth mutabakatı DEFERRED_AUTH olarak açık kalır.
