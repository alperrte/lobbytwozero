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
 * (CASCADE), doğrulama/sıfırlama/silme token'ları ve hesabın Steam kimliğine bağlı bekleyen kayıt/kanıt token'ları.
 * <p>
 * Bilerek SAKLANANLAR (hukuki yükümlülük ve denetim): güvenlik olayları (docs/database.md: 1 yıl), durum ve rol
 * geçmişi ile yönetici kimlikleri, silme talebi/görev kayıtları, anonimleştirilmiş hesap iskeleti (diğer servislerin
 * REF bütünlüğü için). Hesap satırının e-posta, kullanıcı adı ve parolası {@code Account#anonymize} ile silinir.
 */
@Repository
@RequiredArgsConstructor
public class AuthDataEraser {

    private static final String STEAM_PROOF_DELETE = "delete from steam_registrations where steam_id in "
            + "(select provider_subject from external_identities where account_id = ? and provider = 'STEAM')";

    /** Hesap kimliğine bağlı, kişisel veri taşıyan tablolar; ifadeler tamamen sabittir (dinamik SQL yok). */
    private static final List<String> DELETE_STATEMENTS = List.of(
            // external_identities silinmeden ÖNCE: Steam kimliği buradan çözülür.
            STEAM_PROOF_DELETE,
            "delete from external_identities where account_id = ?",
            "delete from oauth_link_flows where account_id = ?",
            "delete from totp_credentials where account_id = ?",
            "delete from recovery_codes where account_id = ?",
            "delete from mfa_challenges where account_id = ?",
            "delete from auth_sessions where account_id = ?",
            "delete from email_verification_codes where account_id = ?",
            "delete from password_reset_tokens where account_id = ?",
            "delete from account_deletion_tokens where account_id = ?");

    /** Silme talebi onaylandığında çalışır: hesabın hâlâ kullanılabilir geçici kimlik doğrulama artıklarını yok eder. */
    private static final List<String> EPHEMERAL_STATEMENTS = List.of(
            STEAM_PROOF_DELETE,
            "delete from oauth_link_flows where account_id = ?",
            "delete from mfa_challenges where account_id = ?",
            "delete from email_verification_codes where account_id = ?",
            "delete from password_reset_tokens where account_id = ?");

    private final JdbcTemplate jdbc;

    public void erase(UUID accountId) {
        DELETE_STATEMENTS.forEach(statement -> jdbc.update(statement, accountId));
    }

    /**
     * Silme onayında: bekleyen şifre sıfırlama/doğrulama kodu, MFA challenge ve Steam kanıtları geçersiz kılınır.
     * Silme token'ları dokunulmaz (onaylanan token kaydı aynı işlemde kullanılıyor); kalıcı kimlik verisi
     * görevler tamamlanınca {@link #erase} ile silinir.
     */
    public void purgeEphemeral(UUID accountId) {
        EPHEMERAL_STATEMENTS.forEach(statement -> jdbc.update(statement, accountId));
    }
}
