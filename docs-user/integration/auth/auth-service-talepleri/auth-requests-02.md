# USER-AUTH-REQ-02 — Hedef açılmadan önce kayıt olmuş hesaplar

Tarih: 2026-10-30. Gönderen: Kerem / user-service. Hedef: Kerem / auth-service.
Öncelik: MEDIUM. Durum: KARAR B (2026-10-30) — geriye dönük yayın yok; eski DEV hesapları yeniden kaydolur veya DEV DB sıfırlanır, `username` boş olabilir. Gerçek (üretim) hesap çıkarsa A için yeniden onay istenir.

**Gerekçe:** `AUTH_EVENT_TARGETS` boşken kayıt olan hesaplar için `UserRegistered` hiç yazılmamıştır; hedef sonradan açılsa da
bu olaylar geriye dönük üretilmez. User tarafında bu hesapların profili `GET /me` ile açılır ama `username` alanı **boş kalır**
(kullanıcı adı yalnız bu olayla gelir; Auth'ta User için bir hesap-okuma izni tanımlı değil).

**Seçenekler (Auth sahibi seçer):**
- **A (öneri):** `ACTIVE` hesaplar için tek seferlik `UserRegistered` yeniden yayını (yeni `eventId`). Alıcı idempotenttir,
  mevcut profili bozmaz, yalnız kullanıcı adı kopyasını doldurur.
- **B:** DEV/ortam sıfırlanır; mevcut hesaplar yeniden kayıt olur. Üretimde henüz hesap yoksa en ucuz yol budur.
- **C:** `account:read` benzeri dar bir iç okuma ucu (kullanıcı adı + durum). Bu yol User'a servis istemci gizli anahtarı ve ENV
  yükü getirir; önerilmez.

**Kabul:** Hedef açıldıktan sonra mevcut ACTIVE hesapların profilinde `username` dolu.

**Referans:** `AuthEventPublisher` (hedef listesi boşken yazım yok), `docs-user/integration/auth/auth-service-talepleri/auth-requests-01.md`.
