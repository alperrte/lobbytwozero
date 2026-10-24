# HW-AUTH-04 — Gerçek uygulamalarla izole HTTP kabulü

Tarih: 2026-10-24. Sorumlu: Alper. Durum: **HARDWARE RUNTIME VERIFIED**.

## 1. Amaç
Hardware'ın gerçek Auth girişinden üretilen kimliği, gerçek Gateway yönlendirmesini ve
Auth worker'ının D4 çağrısını ayrı çalışan uygulamalar üzerinden kabul etmek.

## 2. Kapsam
Hardware–Auth–Gateway sınırı; gerçek paketli JAR'lar, iki geçici PostgreSQL 17 ve Mailpit.
Auth/Gateway/Social kaynak kodu değiştirilmez. Mevcut Compose ortamı ve verileri kullanılmaz.

## 3. Kaynaklar
Auth/Gateway application.properties; Auth kayıt, doğrulama, giriş, CSRF ve hesap silme
API'leri; docs-auth/servis-sozlesmeleri.md §3; Hardware V8 ve onaylı temizleme politikası.

## 4. Kullanılan uçlar
Gateway üzerinden Auth register → email-verification/confirm → csrf → login;
Hardware profiles, katalog yazma reddi ve Auth account/deletion/request → confirm.
Auth worker → yalnız iç D4 relay → gerçek Hardware DELETE /internal/v1/accounts/{accountId}.
Relay istek kimliğini değiştirmez; gerçek Auth token'ını yalnız bellekte gözlemler.

## 5. Önkoşullar
Java 25 (`JAVA_HOME`), Node 22+, Docker; güncel backend JAR'ları.
Önce onaylı tam pre-push ile derleme yapılır. Betik src/main veya POM'u JAR'dan
daha yeni olan serviste eski artefact'la kanıt üretmeyi reddeder.
Kullanılan image'lar mevcut repo ile aynıdır: postgres:17-alpine, axllent/mailpit:v1.31.0.

## 6. Mimari sınır
Yeni production client, API, servis veya kimlik sözleşmesi eklenmez.
Bu operasyonel kabul betiği servisler arası DB erişimi kurmaz. Auth görev durumu ve
Hardware satır sayısı yalnız betiğin kendi geçici DB'lerinde read-only SELECT ile doğrulanır.
Hesaplar Auth API'siyle kaydedilir ve e-posta koduyla etkinleşir; Auth DB'sine seed/UPDATE yapılmaz.

## 7. Çalıştırma
```powershell
node --test ops/hardware-auth-e2e.test.mjs
node ops/hardware-auth-e2e.mjs
```
Betik gerçek ENV dosyalarını okumaz ve hiçbir ENV dosyası üretmez/değiştirmez.
Geçici şifre ve 3072 bit RSA anahtarı bellekte üretilir; mevcut konfigürasyon anahtarları
yalnız fixture alt süreçlerine verilir. Değerler CLI argümanına veya rapora yazılmaz.

## 8. Güvenlik
Alt süreçlere host uygulama sırları, DEBUG veya JAVA_TOOL_OPTIONS aktarılmaz.
Çıktılar credential/JWT maskelemesinden geçer; yalnız kontrol adı ve sonuç yazılır.
Auth secure/HttpOnly cookie öznitelikleri doğrulanır. Test HTTP istemcisi cookie'leri
elle taşır; gerçek tarayıcı/TLS dağıtım testi iddiası yoktur. Refresh cookie'si Path
kuralıyla Hardware'a gönderilmez. Gateway CSRF ve Origin koruması kapatılmaz.

## 9. Sahiplik ve olumsuz kontroller
İki sentetik hesapla owner profil oluşturma; yabancı kaynak 404; body userId 400;
anonim erişim 401; eksik CSRF/kötü Origin 403; doğrudan Hardware cookie kimliği 401;
PLAYER katalog yazması 403; kullanıcı token'ıyla iç D4 çağrısı 401.

## 10. D4 ve yeniden yazma bariyeri
Gerçek Auth request/confirm API'si ve gerçek AccountDeletionWorker kullanılır.
Hardware 204 verir, Auth hardware görevini kalıcı CONFIRMED yapar; owner profili silinir.
Aynı gerçek servis token'ıyla tekrar 204, başka accountId ile 403 doğrulanır.
Eski kullanıcı JWT'siyle okuma/yeniden oluşturma 401; diğer hesabın profili erişilebilir kalır.

## 11. Diğer katılımcılar
Auth'un 16 hedefi korunur. Diğer 15 hedef yalnız bu fixture'da 503 dönen unavailable
sunucusuna yönelir. Sahte ACK yok; 15 görev PENDING, hesap DELETION_REQUESTED kalmalıdır.
Bu, Hardware katılımcısının gerçek HTTP kabulüdür; bütün dağıtılmış Saga tamamlandı denmez.

## 12. Yardımcı testler
Yedi Node testi: ortam allowlist'i, cookie Path sınırı, silinen cookie davranışı,
credential/JWT redaction, eksik/eski artefact reddi, Docker image pull stderr'inin
başarılı container ID'sine karışmaması ve başarısız alt süreç çıktısının maskelenmesi.
Paylaşılan runner regresyonlarıyla birlikte **72 test PASS**,
`logs/hardware-auth-runner-runtime-final-tests.log`.

## 13. Canlı kabul sonucu
Son pre-push ile üretilen güncel JAR'larla **16 kontrol PASS / exit 0**,
2026-10-24 **16:40:10 +03** log bitişi. Kayıt: `logs/hardware-auth-runtime-e2e-final.log`.
İlk başarılı kabul 16:21:02 +03, `logs/hardware-auth-runtime-e2e-confirmation.log`.
Gerçek Auth kayıt/giriş, SMTP doğrulama, Gateway routing/cookie/CSRF ve Hardware D4
katılımı VERIFIED. Önceki yalnız MockMvc/üretici sözleşmesi kanıtına ek gerçek HTTP kanıtıdır.

## 14. Temizlik
Üç Java alt süreci durdurulur; yalnız benzersiz fixture etiketi doğrulanan üç
container ve kendi geçici volume'ları kaldırılır. Sonuç CLEANUP 3/3.
Mevcut container, DB, volume, ENV veya gerçek kullanıcı hesabına müdahale edilmez.

## 15. Düzeltilen ilk deneme ve açık sınırlar
İlk koşuda Gateway'in beklenen CORS 403 düz metin yanıtını betik JSON sandığı için
durdu; fixture kaynakları temizlendi. Content-Type kontrolü düzeltildi ve canlı koşu
yeniden PASS aldı (`logs/hardware-auth-runtime-e2e.log` başarısız geçmiş kaydıdır).
Ortak Compose dağıtımı/TLS/tarayıcı ve diğer 15 servisin ACK'i NOT_VERIFIED.
Auth D3 iptal yayılımı, cihaz credential ve PERF/User/Social policy sözleşmeleri
mevcut açık taleplerdir; bu işte uydurularak açılmaz.

## 16. Kalite ve teslim
Backend üretim kaynakları son 356-test/1138-test koşularından sonra değişmedi.
Sonuçların otoritesi [kalite kaydı](auth-integration-quality.md) ve master checkpoint'tir.
Hardware Sonar bulgusu 0; ortak taramadaki Social engeli dış kapsamdadır ve kullanıcı
tekrar müdahaleyi reddetmiştir. Genel kapı PASS olmadan hazır commit/PDA teslimi denmez.
Son iki kapı: full dev 16:38:24 +03 PASS (1138 test/%95,88/18 HTTP), scan dev
16:59:25 +03 genel FAIL (A/C/A; Hardware 0, Social 17). Sonuç EXTERNAL_QUALITY_BLOCKED.
