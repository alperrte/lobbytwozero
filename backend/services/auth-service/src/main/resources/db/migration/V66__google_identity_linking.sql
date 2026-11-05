-- AUTH-20 devamı: oturumdaki hesaba Google bağlama.
--
-- İçindekiler
--   1. Bağlama akışı : oauth_link_flows sağlayıcı listesine GOOGLE eklenir

-- =====================================================================================================================
-- 1. BAĞLAMA AKIŞI
-- =====================================================================================================================

-- V65 yalnız STEAM'e izin veriyordu. Google bağlama aynı akış tablosunu kullanır (hesap akışa yazılır, state tek
-- kullanımlıktır); PKCE doğrulayıcısı ve nonce state'ten türetildiği için ek sütun gerekmez.
ALTER TABLE oauth_link_flows DROP CONSTRAINT ck_oauth_link_flows_provider;
ALTER TABLE oauth_link_flows ADD CONSTRAINT ck_oauth_link_flows_provider CHECK (provider IN ('STEAM', 'GOOGLE'));
