-- LIB-10: User profil özeti için LibrarySummaryChanged olayının transactional outbox'ı.
-- Satır "bu kullanıcının özeti değişti" işaretidir (kullanıcı başına en çok bir satır). Mutlak sayılar yazma anında değil
-- gönderim anında okunur; böylece teslim sırası bozulsa bile son olay güncel toplamı taşır. Kişisel veri tutulmaz.
-- change_seq her işaretlemede artar; teslimden sonra yalnız teslim edilen sürüm silinir, arada değişiklik olduysa satır kalır.
CREATE TABLE library_summary_outbox (
    user_id uuid CONSTRAINT pk_library_summary_outbox PRIMARY KEY,
    change_seq bigint NOT NULL DEFAULT 1,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_library_summary_outbox_seq CHECK (change_seq >= 1),
    CONSTRAINT ck_library_summary_outbox_attempts CHECK (attempts >= 0)
);

-- Relay'in "zamanı gelmiş" sorgusu.
CREATE INDEX ix_library_summary_outbox_next ON library_summary_outbox (next_attempt_at);

-- Başlangıç aktarımı: var olan koleksiyon sahipleri bir kez işaretlenir; relay User'a mutlak sayıları yavaş yavaş gönderir.
INSERT INTO library_summary_outbox (user_id) SELECT DISTINCT user_id FROM library_entries;
