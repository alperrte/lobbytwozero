# USER-AUTH-REQ-03 — Silinen hesabın bekleyen olayları (kod doğrulaması, soru değil)

Tarih: 2026-10-30. Gönderen: Kerem / user-service. Hedef: Kerem / auth-service.
Öncelik: LOW. Durum: **CLOSED — Auth kodundan doğrulandı**, Auth tarafında iş yok.

**Soru:** Hesap silme tamamlanınca o hesaba ait bekleyen `UserRegistered`/`AccountStatusChanged` olayları teslim edilmeye devam
eder mi? Devam ederse silinmiş bir hesabın profili User'da yeniden oluşabilir.

**Bulgu:** `auth_event_outbox.account_id` sütunu `REFERENCES accounts (id) ON DELETE CASCADE` ile hesaba bağlıdır
(`V68__create_auth_event_outbox.sql`); `AccountDeletionCoordinator.finish` hesap satırını sildiğinde bekleyen olaylar da silinir.
Yalnız silme anında hâlihazırda HTTP yolunda olan olay (dar bir yarış) User'a ulaşabilir; User bu durumda yalnız `userId` içeren
boş bir satır açabilir (kişisel veri yoktur). `docs-security/user-service/NOTLAR.md` bunu izlenecek sınır olarak kaydeder.

**Açık kalan küçük soru:** `DELETION_REQUESTED` durumundaki hesap bekleme süresinde (14 gün) access token alabiliyor mu?
Alabiliyorsa User bu hesabı normal kullanıcı olarak görür. Auth tarafı bu konuda User'dan bir şey beklemez; bilgi amaçlıdır.
