-- Steam kaynaklı hesaplarda zorunlu 2FA kurulumu: kurulum adımı için challenge amacı.
--
-- İçindekiler
--   1. Challenge amacı : mfa_challenges.purpose (LOGIN | ENROLLMENT)

-- =====================================================================================================================
-- 1. CHALLENGE AMACI
-- =====================================================================================================================

-- LOGIN: parola/harici giriş doğrulandı, TOTP etkin, ikinci faktör bekleniyor (mevcut davranış).
-- ENROLLMENT: giriş doğrulandı ama hesap 2FA'ya zorunlu ve TOTP henüz etkin değil; challenge yalnız TOTP kurulumunu
-- tamamlamaya yarar, kurulum bitince oturum açılır. Mevcut satırlar LOGIN kalır.
ALTER TABLE mfa_challenges ADD COLUMN purpose varchar(12) NOT NULL DEFAULT 'LOGIN';
ALTER TABLE mfa_challenges ADD CONSTRAINT ck_mfa_challenges_purpose CHECK (purpose IN ('LOGIN', 'ENROLLMENT'));
