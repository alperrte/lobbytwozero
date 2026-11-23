# HW-AUTH-REQ-01 — Hesap durumu ve access-token iptal yayılımı

Tarih: 2026-10-24. Gönderen: Alper / hardware-service. Hedef: Kerem / auth-service.
Öncelik: HIGH. Durum: OPEN — D3 sözleşmesi bekleniyor; uygulanmış API talebi değildir.

**Gerekçe:** Hardware imzalı access token'ı yerel doğrular. Logout, ban/suspend, rol kaldırma,
parola/oturum iptali eski JWT'yi TTL + toleranstan önce kendiliğinden geçersiz yapmaz.
D4 fence yalnız tamamlanan hesap temizliğini korur; genel D3 yerine geçmez.

**Mevcut/eksik:** at+jwt; sid/ver/jti/roles claim'leri ve Auth'un kendi SessionStateValidator'ı var.
Hardware'ın Auth DB'sini okuması yasaktır. Yayınlanmış D3 tüketici/iptal-cache veya introspection sözleşmesi yok.

**Beklenen sözleşme:** Kerem transportu ve fail-closed/staleness politikasını seçsin; olay tabanlıysa
eventId, accountId/sessionId/tokenVersion, monoton revision, occurredAt, replay/retention ve başlangıç
senkronizasyonu tanımlansın. HTTP seçilirse endpoint, method, timeout, response ve scope önce API kataloğunda yayımlansın.
Hardware kendiliğinden yeni Auth endpoint'i veya sürekli polling kurmayacak.

**Güvenlik/RBAC:** Yalnız Auth kaynaklı karar; doğrulanmış servis kimliği ve tüketici kapsamı;
token/parola taşıma yok; istemci header/body'si güven kaynağı değil. Cache anahtarı/retention minimizasyonu gerekli.

**Başarı/hata/HTTP:** Şu anda tüketilecek uç yok, HTTP kodu atanmadı. Yeni HTTP modelinde 200 yalnız
doğrulanmış geçerli durum; 401/403, 5xx, timeout ve eski kararın davranışı Kerem'in sözleşmesinde kesinleştirilmeli.
Olay modelinde durable ACK/yeniden oynatma ve sıra dışı teslim kuralı tanımlanmalı.

**Sahiplik/idempotency:** Auth hesap/oturum sahibidir; Hardware yalnız yerel yetki kararını uygular.
Tekrarlı veya eski olay yeni bir yetki vermemeli; aynı eventId idempotent olmalı.

**Uyumluluk:** Mevcut JWT/JWKS uyumu korunur. Şimdiki gecikme üst sınırı kullanıcı JWT ömrü
(Auth en fazla 15 dakika) ve 30 saniye doğrulama toleransıdır; anlık iptal iddiası yoktur.

**Kabul:** Silinme dışındaki revoke/ban/rol düşürme de anlaşılmış süre içinde Hardware'da reddedilir;
restart, ağ kesintisi, tekrar/sıra bozukluğu ve eski cache senaryoları entegrasyon testiyle kanıtlanır.

**Referans:** docs-auth/acik-guvenlik-borclari.md; docs-auth/servis-sozlesmeleri.md;
HardwareJwtValidators ve auth-integration-01.md. Talep henüz dışarı gönderilmedi.
