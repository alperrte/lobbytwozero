package com.ltz.game.infrastructure.persistence.repository;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountErasureRepository {
    private final JdbcTemplate jdbc;

    public AccountErasureRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void erase(UUID accountId) {
        jdbc.queryForObject("select public.erase_game_account_data(?)", String.class, accountId);
    }

    public boolean hasAccountData(UUID accountId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from games where editor_actor_id = ?
                    union all select 1 from game_identity_audits where actor_id = ? or position(? in evidence) > 0
                    union all select 1 from game_admin_audits where actor_id = ? or position(? in coalesce(reason, '') || coalesce(details, '')) > 0
                    union all select 1 from game_field_overrides where actor_id = ?
                    union all select 1 from game_developer_grants where developer_user_id = ? or granted_by = ? or revoked_by = ?
                    union all select 1 from game_developer_contents where author_id = ? or moderated_by = ?
                    union all select 1 from game_developer_media where author_id = ? or moderated_by = ?
                    union all select 1 from game_update_notes where author_id = ? or moderated_by = ?
                    union all select 1 from game_developer_feedback where author_id = ?
                )
                """, Boolean.class, accountId, accountId, accountId.toString(), accountId, accountId.toString(), accountId,
                accountId, accountId, accountId, accountId, accountId, accountId, accountId, accountId, accountId, accountId));
    }
}
