# HW-AUTH-REQ-03 — Gerçek DEV D4 katılımcı kabulü ve kalan servis ACK'leri

Tarih: 2026-10-24. Gönderen: Alper / hardware-service. Hedef: Kerem / auth-service.
Öncelik: HIGH (dağıtılmış hesap silme tesliminden önce).
Durum: HARDWARE PARTICIPANT VERIFIED / ortak DEV ve diğer 15 ACK OPEN.

**2026-10-24 doğrulaması:** Gerçek paketli Auth/Gateway/Hardware, geçici PostgreSQL ve
Mailpit ile HTTP kabulü 16 kontrol PASS. Gerçek Auth worker token'ı Hardware'dan 204 aldı;
Auth hardware görevi CONFIRMED, owner profili silindi, tekrar 204 ve eski JWT ile
yeniden yazma reddi doğrulandı. 16 hedef korunarak diğer 15 görev PENDING bırakıldı;
hesap DELETION_REQUESTED kaldı. [Kanıt ve sınır](../auth-integration-04.md).
Ortak Compose dağıtımı/TLS/tarayıcı ve bütün Saga'nın tamamlanması hâlâ doğrulanmadı.

**Gerekçe/mevcut sözleşme:** Auth AccountDeletionCoordinator 16 hedefi kalıcı görev/lease/retry ile yönetir.
Hardware aynı iç DELETE protokolüne katılır; kendi yerel temizliği tüm Saga'nın tamamlandığını kanıtlamaz.
Yeni Auth API'si istenmiyor. Eksik olan birlikte yürütülmüş dağıtım kabul kanıtıdır.

**Beklenen:** Güncel Hardware imajı V8 ile kurulduktan sonra Kerem'le yalnız sentetik test hesabı üzerinde
gerçek D4 komutu çalıştırılsın; hardware hedefi CONFIRMED, kişisel satırlar temiz ve tekrar komutu 204 görülmeli.
Diğer 15 katılımcının eksik ACK'i bu işte atlanmamalı veya sahte başarıyla kapatılmamalı.

**Güvenlik/RBAC:** Auth RS256/kid/svc+jwt, tek aud=hardware-service, tam sub=svc:auth-service,
scope=account:erase, account_id=yol; exp/iat/nbf/jti. İç route Gateway'e açılmaz.

**Başarı/hata/HTTP:** Tam yerel temizliğe 204; 200/204 Auth ACK ölçütüdür.
202/3xx/404/401/403/5xx/timeout tamamlanma değildir. Auth ileri yönlü tekrar eder, kişisel veriyi restore etmez.

**Sahiplik/idempotency:** Auth orkestratör, Hardware yalnız kendi DB'sinin katılımcısıdır.
Tekrarda 204 ve kısa fence yenilemesi; başka hesap/global katalog korunur.

**Saklama mutabakatı:** Kullanıcı “Kerem’le mutabık, politikayı onaylıyorum” dedi.
Kalıcı kişisel audit tutulmaz; SHA-256 hesap özeti + expiry güvenlik fence'i 17 dakika etkindir,
periyodik süpürmede kaldırılır. Pseudonymous metadata anonim diye sunulmaz.

**Uyumluluk/kabul:** Auth/Gateway kaynağı değiştirilmeden üretici sözleşmesi ve 17 ortak senaryo test edilir.
DEV kabulü ayrıca kaydedilir; bu belge gerçek hesap silme yetkisi veya yapılmış DEV E2E kanıtı değildir.

**Referans:** docs-auth/d4-ekip-notlari/00, 01, 05; auth-integration-02.md.
Talep henüz dışarı gönderilmedi.
