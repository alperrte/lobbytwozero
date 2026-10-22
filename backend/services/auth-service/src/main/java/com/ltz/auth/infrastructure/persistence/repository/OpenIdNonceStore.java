package com.ltz.auth.infrastructure.persistence.repository;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ltz.auth.infrastructure.security.SecureTokens;

import lombok.RequiredArgsConstructor;

/** Steam response_nonce replay koruması; birincil anahtar çakışması ikinci kullanımı atomik olarak reddeder. */
@Repository
@RequiredArgsConstructor
public class OpenIdNonceStore {

    private final JdbcTemplate jdbc;
    private final SecureTokens secureTokens;

    /** @return nonce ilk kez görülüyorsa true; daha önce kullanılmışsa false */
    public boolean firstUse(String responseNonce, Instant now) {
        return jdbc.update("insert into openid_nonces (nonce_hash, seen_at) values (?, ?) on conflict do nothing",
                secureTokens.sha256Hex(responseNonce), Timestamp.from(now)) == 1;
    }
}
