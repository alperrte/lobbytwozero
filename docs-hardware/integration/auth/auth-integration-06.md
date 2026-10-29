# HW-AUTH-06 — İkinci aşama doğrulama kaydı

Tarih: 2026-10-29. Sorumlu: Alper. Durum: HARDWARE KAPSAMI VERIFIED;
Performance aktif cihaz sözleşmesi DEFERRED_CONTRACT, ortak DEV/tüm Saga NOT VERIFIED.

## Kapsam

Hardware kullanıcı TTL sınırı, cihaz JWT doğrulayıcısı/dönüştürücüsü ve route ayrımı;
Auth gerçek üreticisiyle sözleşme ve negatif testler; izole gerçek HTTP betiği ve Node regresyonları.
Auth/Gateway kaynakları yalnız okunur ve test/paketleme için derlenir.
Frontend değişmediği için frontend testleri bu görevin doğrulama kapsamına alınmadı.

## İlk mevcut-kod koşusu

`node pre-push/ci/backend.mjs --modules services/hardware-service`: ilk sandbox denemesinde
Docker named pipe erişimi reddedildi. İzinli yeniden koşuda Maven/JDK25 ve geçici PostgreSQL
başladı; mevcut 356 testin 98'i Mockito agent başlangıç hatasıyla ERROR verdi:
`Could not self-attach to current VM using external process`; agent JAR yolu Türkçe karakterli
depo yolunda açılamadı. Bu sonuç PASS değildir.

Yeniden doğrulamada yalnız test süreçleri için mevcut Mockito agent'ı ASCII geçici dizinden
`JAVA_TOOL_OPTIONS=-javaagent:<geçici-yol>` ile başlatılır. JaCoCo, kalite kuralları, testler
ve coverage politikası kapatılmaz; gerçek ENV dosyası okunmaz/değiştirilmez.

## Sonuçlar

| Kontrol | Gerçek sonuç |
| --- | --- |
| JDK 25 + Maven `clean verify`, `services/hardware-service` | **405 PASS**, failure/error/skipped 0; 49 yeni senaryo |
| Merkezi coverage politikası | **PASS**; 1615/1633 executable satır = **%98,90**; değişen tracked satırlar 110/111 = %99,10; ek executable kaynaklar merkezi minimumu sağladı |
| Checkstyle/PMD ratchet | **PASS**; mevcut 213 yıldızlı import borcu aynı, yeni ihlal 0; baseline/politika değiştirilmedi |
| `LOG_LEVEL=DEBUG`, Auth GoogleLoginTest + SteamLoginTest | **22/22 PASS**; sentetik sır/PII log regresyonları dahil |
| Aynı DEBUG koşusunda Auth LoggingPrivacyPinTest | **9/9 PASS**, dokuz logger açık INFO |
| Aynı DEBUG koşusunda Auth API DeviceCredentialTest | **15/15 PASS**; toplam Auth seçili test **46 PASS** |
| `node pre-push/ci/node-tests.mjs` | **155/155 PASS**, fail/skipped 0; ops/kalite yardımcıları |
| Auth/Gateway JAR paketleme | **PASS**; `-DskipTests package` yalnız E2E artefaktı hazırlığıdır, tam Auth/Gateway kalite kabulü değildir |
| `node ops/hardware-auth-e2e.mjs` | **20/20 PASS**, gerçek üç JAR + iki geçici PostgreSQL 17 + Mailpit; 3 süreç ve 3 fixture container temizlendi |
| Taze Hardware runtime JAR Trivy taraması | **PASS**, tam BOOT-INF/lib envanteri; HIGH/CRITICAL bulgu 0 |
| `git diff --check` | **PASS** |

Hardware Maven bitişi: `2026-10-29 03:34:57 +03`.
Yerel, Git dışı loglar: `logs/hardware-auth-v2-backend.log`,
`logs/hardware-auth-v2-auth-debug.log`, `logs/hardware-auth-v2-node.log`.
E2E yalnız kontrol adı/sonuç yazar; token/anahtar/parola rapora alınmaz.

İzole backend runner test/coverage/ratchet'i geçtikten sonra Trivy kurulu olmadığı için
ilk bütün koşu exit 1 verdi (`Trivy execution failed; raw output withheld`). Pinned resmî
Trivy 0.76.0 arşivi SHA-256 doğrulanarak kuruldu; **aynı taze JAR** ayrı dependencyCheck
ile tarandı ve PASS alındı. Ara tekrarın zaman damgası ondalıklı olduğu için gate
`Fresh build timestamp required` ile reddetti; tam sayı başlangıç zamanı ile gerçek tarama
tamamlandı. Bu ara hatalar atlanarak ilk runner exit 0 gibi sunulmaz.

Standart `pre-push.cmd hardware-service` runner'ı `backendTestEnvironment` üzerinden
gerçek DEV ENV okuyabildiğinden bu görevde kullanılmadı. ENV okumayan CI runner'ın
Maven/coverage/ratchet ve kalan bağımlılık kapıları yukarıdaki gerçek sonuçlarla doğrulandı.
Tam `full dev`, frontend, bütün backend ve Sonar bu oturumda tekrar çalıştırılmadı;
bu görev için `SAFE TO COMMIT` çıktısı üretildiği iddia edilmez.

## Kabul edilen sınırlar ve test kapsamı

Gerçek Auth `DeviceTokenIssuer` kaynağı Hardware test desteğinde derlenir; ayrı
JWT fixture'ıyla üretilmiş varsayımsal claim sözleşmesi gerçek üretici yerine geçmez.
32 sabit saatli cihaz validator senaryosu ve gerçek üreticiyle 17 yeni HTTP güvenlik
senaryosu (toplam 49) eklendi. Bozuk imza/claim, yanlış tür/scope/UUID, süre sınırı,
kullanıcı/cihaz/servis ayrımı, silinmiş hesap fence'i ve aktif cihaz policy reddi doğrulandı.

Gerçek E2E: kullanıcı token'ı 60 sn, Auth cihaz bağlama/token, geçerli cihazın Hardware
snapshot policy'sine ulaşması ve 404 ile kapalı kalması, kullanıcı/D4 uçlarında 401,
cihaz iptalinden sonra yeni token alamama, Auth worker 204→CONFIRMED, owner veri temizliği,
idempotent tekrar, yabancı hesap reddi ve eski JWT ile yeniden yazma engeli geçti.
16 hedef korunur; izole fixture sonucu 1 CONFIRMED + 15 PENDING'dir. Repo genelindeki
eksik silme ucu sayısı 11'dir; hazır dört katılımcı bu fixture'da başlatılmaz.

## Teslim sınırı

Performance policy üretimde deny eder. Gerçek consent→snapshot başarı E2E'si,
ortak DEV/TLS/tarayıcı ve bütün Saga **NOT VERIFIED** olarak kalır.
Önceki Sonar sonucu [v1 arşivinde](entegrasyon-v1/auth-integration-quality.md) tarihçedir;
bu oturumda Sonar'ın yeniden geçtiği iddia edilmez.
