# HW-AUTH-02 — Onaylı D4 hesap temizliği ve yeniden yazma bariyeri

Tarih: 2026-10-24. Sorumlu: Alper. Durum: APPROVED / IMPLEMENTED / QUALITY_BLOCKED (ortak Sonar).

Kullanıcı onayı: “Kerem’le mutabık, politikayı onaylıyorum”. V8, hedefli kişisel audit
temizliği ve 17 dakikalık SHA-256 fence saklaması Auth sahibiyle mutabık olarak onaylandı.

## 1. Amaç
Auth'un DELETE /internal/v1/accounts/{accountId} komutuna, kişisel Hardware verisi gerçekten temizlenince 204 vermek.
## 2. Kapsam
Yalnız hardware_db. Onaylı V8 eklenir; uygulanmış V1–V7 değiştirilmez. Gerçek uygulama DB'sine ajan tarafından silme/reset komutu çalıştırılmaz; testler izole PostgreSQL'dedir.
## 3. Kaynaklar
Kullanıcı görevi §6.4: yeni migration/veri silen işlemden önce açık onay.
docs-auth/d4-ekip-notlari/01 §3: saklama istisnası yalnız Auth sahibiyle birlikte kararlaştırılır; 204 kişisel veri kalmadığını ifade eder.
## 4. Etkilenen uç
Yeni INTERNAL ONLY DELETE /internal/v1/accounts/{accountId}; Gateway route'u eklenmez.
## 5. Mevcut sözleşme
RS256/kid/JWKS, svc+jwt, issuer, tek aud=hardware-service, sub=svc:auth-service,
scope=account:erase, account_id=yol, ham iat/nbf/exp ve en fazla 60 saniye ömür, jti.
## 6. Onaylı temizleme politikası
Kalıcı kişisel audit saklanmaz: hesabın actor olduğu audit kayıtları ve
silinen kişisel yorumlara bağlı audit kayıtları kontrollü hesap-erasure işleminde kaldırılır.
Normal UPDATE/DELETE/TRUNCATE koruması sürer; genel bir audit silme API'si veya trigger kapatma yapılmaz.
Saklama istenirse, hukuki gerekçe ve Auth sahibi mutabakatı gelmeden alternatif uydurulmaz.
## 7. RestClient / Port / Saga
Koordinatör mevcut Auth'tur. Hardware yerel transaction + idempotent handler'dır.
HTTP 204 mevcut ACK'tir; yeni event/inbox veya ikinci Saga koordinatörü gerekmez.
Auth D4 rehberindeki büyük veri kuralı uygulanır: her transaction en fazla 20 profil,
20 hedef yorum ve ilgili audit/outbox/rapor türü başına 1000 satır işler. Bitmeyen paket
commit olur ve güvenli `503 ACCOUNT_ERASURE_PENDING` / Retry-After: 60 döner;
Auth ileri yönlü tekrar eder. Yalnız tüm kişisel kayıtlar temizlenince 204 verilir.
DB/statement/transaction hatası mevcut paketi ve o paketin fence yazımını rollback eder;
önceki başarılı paketlerle silinmiş kişisel veri geri yüklenmez. Fence kısmi temizleme
sırasında da kullanıcı yazmalarını reddeder. Bu yerel katılımcıdır; ikinci Saga değildir.
## 8. Onaylı yeniden yazma bariyeri
Erasure ile kullanıcı mutasyonları hesap bazında transaction kilidi paylaşır.
Yalnız SHA-256 hesap kimliği özeti + expiry içeren kısa ömürlü teknik fence tutulur;
Auth'un izin verdiği en uzun kullanıcı token ömrü 15 dakika ve tolerans göz önüne alınarak 17 dakika tutulur.
Özet anonim veri diye gösterilmez; geçici/pseudonymous güvenlik metadatasıdır.
Bu metadata 204 sonrası kısa süre tutulacağı için Auth sahibiyle ortak sözleşme kararı alındı.
17 dakika etkinliği biter; dakikalık scheduler fiziksel temizler. Normal çalışmada yaklaşık
bir ek dakika; scheduler kapalıysa bir sonraki çalışmada purge olur. JWT azami 15 dakika,
30 saniyelik gelecekte-iat ve doğrulama toleransı bu güvenlik penceresinin içindedir.
## 9. Veri sahipliği
Profil/component/privacy/consent/receipt; kendi yorumları/raporları; ilgili outbox;
kişisel actor/moderator/sync bucket'ları ve ilgili audit referansları temizlenir.
Başka hesapların ilgisiz kayıtları, global katalog/marka/kampanya ve ortak peer bucket'ları korunur.
## 10. Önerilen dosyalar
V8__add_hardware_account_erasure.sql; internal controller, application erasure service/port,
Infrastructure persistence guard/cleaner ve servis-token güvenlik yapılandırması.
## 11. Uygulama adımları
Politika onayı alındı → V8 → servis-token sınırı → atomik temizlik/fence → retry/race testleri.
## 12. Testler
Ortak rehberdeki 17 senaryo; audit ve outbox temizliği; A hesabını silerken B'nin korunması;
aynı komutun tekrarı; rollback/timeout; silme ile create/review/sync yarışı; eski JWT ile yeniden yazma reddi; fence expiry.
## 13. Kabul
Yalnız tamamlanan temizlikte 204; yanlış sub/account_id/scope 403, yanlış token 401.
Auth'un gerçek ServiceTokenIssuer biçimiyle uyumluluk. Metadata mutabakatı alındı; nihai kalite kapıları ayrıca doğrulanır.
## 14. Uyumluluk
Eski SQL checksum'ları korunur. Yeni endpoint sadece iç ağda doğrulanmış servis token'ına açıktır.
V8'e actor/reporter/outbox aggregate/owner lookup indeksleri de eklenir;
bu migration henüz gerçek uygulama DB'sine dağıtılmamıştır.
## 15. Riskler
Audit hard-delete ve geçici fence saklaması kullanıcı/Auth sahibi mutabakatıyla onaylandı.
Audit istisnası DB transaction-local bağlamıdır; uygulama DB credential'ı güven sınırındadır.
DB'ye doğrudan sınırsız erişimi olan kişi zaten tablo/trigger yönetebilir; bu mekanizma ona karşı ayrı bir yetki sistemi değildir.
Auth D3 ve diğer 15 servisin ACK'i bu Hardware değişikliğiyle uygulanmış sayılmaz.
## 16. Kanıt
V8 ve yerel D4 uygulandı. Gerçek Auth üreticisi ve PostgreSQL ile pozitif/negatif,
kişisel tablo temizliği, rollback, tekrar, fence ve yarış testleri geçti.
[Gerçek Auth worker → Hardware HTTP kabulü](auth-integration-04.md) PASS;
hardware görevi CONFIRMED, tekrar 204 ve eski JWT ile yeniden yazma reddi doğrulandı.
[Güncel çalışma ve nihai kapılar](auth-integration-quality.md).
Diğer 15 hedefin ACK'i ve ortak DEV dağıtılmış Saga E2E NOT_VERIFIED.
