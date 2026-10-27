-- SOC-09/10/11: kullanıcı engelleme. blocker_id / blocked_id başka servise ait kimliklerdir (REF, FK yok).
-- Engel yönlüdür: A'nın B'yi engellemesi ile B'nin A'yı engellemesi ayrı kayıtlardır; kısıtlama iki yönde de uygulanır.

CREATE TABLE user_blocks (
    blocker_id uuid        NOT NULL,
    blocked_id uuid        NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT pk_user_blocks PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT ck_user_blocks_distinct_users CHECK (blocker_id <> blocked_id)
);

-- "Beni kim engelledi" (ters yön) sorguları ve silinen hesabın kayıtlarını temizleme için.
CREATE INDEX ix_user_blocks_blocked_id ON user_blocks (blocked_id);

-- Engellenenler listesi (keyset sayfalama: created_at, blocked_id azalan).
CREATE INDEX ix_user_blocks_blocker_created ON user_blocks (blocker_id, created_at DESC, blocked_id DESC);
