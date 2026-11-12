-- AUTH-AGE: yönetici geri almasının kaydı (kim, neden). `revoked_by = 'ADMIN'` satırı kullanıcının yeniden beyanını
-- engeller; yönetici engeli kaldırınca satır silinir.
--
-- İçindekiler
--   1. Yönetici geri alma alanları : account_age_verifications

-- =====================================================================================================================
-- 1. YÖNETİCİ GERİ ALMA ALANLARI
-- =====================================================================================================================

-- revoked_by_account_id yöneticinin hesabıdır. Başka tabloya FK değildir: yönetici hesabı sonradan silinse de denetim
-- izi kalır (aynı yaklaşım rol geçmişinde). revoke_reason denetim gerekçesidir (en çok 500 karakter).
ALTER TABLE account_age_verifications
    ADD COLUMN revoked_by_account_id uuid,
    ADD COLUMN revoke_reason         varchar(500);

-- Yönetici geri almasında yönetici kimliği ve gerekçe zorunludur; kullanıcı kendi geri alırsa ikisi de boştur.
ALTER TABLE account_age_verifications
    ADD CONSTRAINT ck_age_verifications_admin_revocation CHECK (
        (revoked_by = 'ADMIN' AND revoked_by_account_id IS NOT NULL AND revoke_reason IS NOT NULL)
        OR (revoked_by IS DISTINCT FROM 'ADMIN' AND revoked_by_account_id IS NULL AND revoke_reason IS NULL));
