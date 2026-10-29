# HW-AUTH-01 — Kullanıcı JWT ve güvenilir Hardware aktörü

Tarih: 2026-10-24. Sorumlu: Alper. Durum: IMPLEMENTED / QUALITY_BLOCKED (ortak Sonar).
Kullanıcı Aşama 1 analizinden sonra tüm Hardware–Auth entegrasyonunu ve bu MD düzenini onayladı.

## 1. Amaç
Mevcut Auth kullanıcı JWT'sini Hardware içinde doğrulamak; test actor'ını gerçek kimlik doğrulaması yerine kullanmamak.
## 2. Kapsam
Yalnız Hardware güvenliği, testleri ve kendi belgeleri. Auth/Gateway/Game/Social kodu, gerçek ENV ve eski SQL değişmez.
## 3. Kaynaklar
AGENTS.md, SECURITY.md, Auth entegrasyon rehberi, docs-auth/servis-sozlesmeleri.md ve d4-ekip-notlari/00, 01, 05; Aşama 1 kaynak analizi.
## 4. Etkilenen uçlar
Mevcut 46 iş endpoint'inin kullanıcı kimliği sınırı. Dış policy bağımlılıkları veya cihaz kimliği bu parçayla sağlanmış sayılmaz.
## 5. Mevcut Auth sözleşmesi
GET /api/v1/auth/.well-known/jwks.json; RS256, kid, typ=at+jwt, iss=JWT_ISSUER,
aud=JWT_AUDIENCE, UUID sub, exp/nbf/iat. roles yalnız kanonik isim listesidir.
## 6. Mimari karar
JWT yerel doğrulanır; Auth'a her iş isteğinde durum sorgusu yapılmaz. Yalnız JWKS açık anahtarları alınır.
Auth private key'i tüketiciye aktarılmaz. Mevcut AUTH_SERVICE_URL/JWT_ISSUER/JWT_AUDIENCE kullanılır.
## 7. Port / RestClient / Saga
CurrentActor portu korunur, doğrulanmış JWT typed AuthenticatedActor'a çevrilir.
JWKS transportu bounded, redirect izlemeyen RestClient ile Infrastructure'da kurulur. İş kimliği için Saga yoktur.
Yalnız yapılandırılmış Auth origin/path kullanılır; JWT'deki jku/x5u URL'si tüketilmez.
JWKS gövdesi en fazla 64 KiB, cache TTL 5 dakika, refresh rate sınırı 1 saniyedir;
otomatik transport retry, refresh-ahead ve outage toleransı açılmaz. Cache içindeki
bilinen anahtar kullanılabilir; bilinmeyen anahtar/erişilemeyen yeni JWKS kimlik oluşturmaz.
Mevcut REST_CLIENT_CONNECT_TIMEOUT_MS/READ_TIMEOUT_MS açıkça bu transporta bağlanır.
## 8. Güvenlik
Eksik ham zorunlu claim dönüşümden önce reddedilir; framework'ün iat varsayımı kontrolü gevşetemez.
Cookie doğrudan Hardware'da kimlik oluşturmaz. CSRF bütünüyle kapatılmaz; Bearer akışı ve Gateway CSRF/Origin birlikte test edilir.
## 9. Sahiplik
sub yalnız UUID kimliğidir; mevcut owner/profile/component/reporter sorguları ve transaction/version kontrolleri korunur.
## 10. Dosyalar
Hardware infrastructure/security JWT properties/configuration/converter/JWKS transport sınıfları,
HardwareSecurityConfiguration, application.properties ve test destek/senaryoları.
## 11. Uygulama
JWT decoder + raw claim doğrulama → actor dönüşümü → güvenlik zinciri → negatif/gerçek token testleri.
## 12. Testler
RS256/kid/typ/issuer/audience/zaman/UUID; none/HS256/yanlış imza; JWKS cache/refresh/error;
owner/IDOR, sahte header, cookie-only ret ve Gateway CSRF/Origin. Test anahtarları geçicidir.
## 13. Kabul
Gerçek imzalı JWT ile owner akışı, anonim 401, yetersiz izin 403, yabancı kaynak 404;
özel veri/sır sızıntısı yok. Maven/coverage ve iki nihai kalite kapısı gerçek sonuçla kaydedilir.
## 14. Uyumluluk
Endpoint/body sözleşmeleri korunur; önceden yalnız testlerde kullanılabilen actor üretimde doğrulanmış JWT'den gelir.
## 15. Açık riskler
Auth D3 anlık iptal/durum yayılımı yoktur. Yerel JWT erişimi token ömrü + saat toleransı kadar gecikmeli olabilir.
Gerçek kullanıcı cihaz credential'ı ve User/Social policy sözleşmeleri ayrı açık bağımlılıklardır.
## 16. Kanıt
Gerçek Auth AccessTokenIssuer/JwksController ve Gateway filtreleri değiştirilmeden
test dizininde derlendi; 3072 bit geçici RSA ile owner/RBAC/CSRF ve negatif token
testleri geçti. JWKS cache/rotation/redirect/boyut/hata + takılan gövde timeout'u test edildi.
[Gerçek paketli uygulama HTTP kabulü](auth-integration-04.md): Auth kayıt/giriş,
SMTP doğrulama, Gateway routing/cookie/CSRF ve Hardware erişimi izole ortamda PASS.
[Nihai kalite kaydı](auth-integration-quality.md). Ortak Compose/TLS/tarayıcı kabulü değildir.
