-- SOC-01..06: arkadaşlık istekleri ve karşılıklı arkadaşlık ilişkisi.
-- Kullanıcılar başka servise ait olduğu için sender_id / receiver_id / user_*_id yalnız REF'tir (FK yok).

-- Arkadaşlık isteği. İstek kaydı yanıtlandıktan sonra da (ACCEPTED/REJECTED/CANCELLED) geçmiş olarak kalır.
CREATE TABLE friend_requests (
    id           uuid        PRIMARY KEY,
    sender_id    uuid        NOT NULL,
    receiver_id  uuid        NOT NULL,
    status       varchar(32) NOT NULL,
    created_at   timestamptz NOT NULL,
    responded_at timestamptz,
    version      bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_friend_requests_distinct_users CHECK (sender_id <> receiver_id),
    CONSTRAINT ck_friend_requests_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_friend_requests_responded CHECK ((status = 'PENDING') = (responded_at IS NULL))
);

-- Aynı kullanıcı çifti arasında (yönden bağımsız) en fazla bir bekleyen istek olabilir. Uygulama kontrolü yetmez:
-- eşzamanlı iki istek (A→B ve B→A dahil) bu indeksle tekilleştirilir.
CREATE UNIQUE INDEX uq_friend_requests_pending_pair
    ON friend_requests (LEAST(sender_id, receiver_id), GREATEST(sender_id, receiver_id))
    WHERE status = 'PENDING';

-- Gelen / gönderilen bekleyen istek listeleri (keyset sayfalama: created_at, id azalan).
CREATE INDEX ix_friend_requests_receiver_pending
    ON friend_requests (receiver_id, created_at DESC, id DESC)
    WHERE status = 'PENDING';

CREATE INDEX ix_friend_requests_sender_pending
    ON friend_requests (sender_id, created_at DESC, id DESC)
    WHERE status = 'PENDING';

-- Karşılıklı arkadaşlık: çift başına tek satır. user_low_id < user_high_id sırası uygulamada baytsal
-- (PostgreSQL uuid sıralamasıyla aynı) karşılaştırmayla sağlanır; CHECK bunu garanti eder.
CREATE TABLE friendships (
    user_low_id  uuid        NOT NULL,
    user_high_id uuid        NOT NULL,
    created_at   timestamptz NOT NULL,
    CONSTRAINT pk_friendships PRIMARY KEY (user_low_id, user_high_id),
    CONSTRAINT ck_friendships_order CHECK (user_low_id < user_high_id)
);

-- PK yalnız user_low_id ile başlayan aramaları karşılar; user_high_id tarafı için ayrı indeks.
CREATE INDEX ix_friendships_user_high_id ON friendships (user_high_id);
