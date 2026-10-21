package com.ltz.auth.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * Hesap silinirken Auth'un KENDİ veritabanındaki kişisel/kimlik verilerini kalıcı olarak siler (KVKK).
 * <p>
 * Silinenler: harici kimlikler, TOTP sırrı, kurtarma kodları, MFA challenge'ları, oturumlar ve refresh token'lar
 * (CASCADE), doğrulama/sıfırlama/silme token'ları.
 * <p>
 * Bilerek SAKLANANLAR (hukuki yükümlülük ve denetim): güvenlik olayları (docs/database.md: 1 yıl), durum ve rol
 * geçmişi ile yönetici kimlikleri, silme talebi/görev kayıtları, anonimleştirilmiş hesap iskeleti (diğer servislerin
 * REF bütünlüğü için). Hesap satırının e-posta, kullanıcı adı ve parolası {@code Account#anonymize} ile silinir.
 */
@Repository
@RequiredArgsConstructor
public class AuthDataEraser {

    /** Hesap kimliğine bağlı, kişisel veri taşıyan tablolar; ifadeler tamamen sabittir (dinamik SQL yok). */
    private static final List<String> DELETE_STATEMENTS = List.of(
            "delete from external_identities where account_id = ?",
            "delete from totp_credentials where account_id = ?",
            "delete from recovery_codes where account_id = ?",
            "delete from mfa_challenges where account_id = ?",
            "delete from auth_sessions where account_id = ?",
            "delete from email_verification_codes where account_id = ?",
            "delete from password_reset_tokens where account_id = ?",
            "delete from account_deletion_tokens where account_id = ?");

    private final JdbcTemplate jdbc;

    public void erase(UUID accountId) {
        DELETE_STATEMENTS.forEach(statement -> jdbc.update(statement, accountId));
    }
}
