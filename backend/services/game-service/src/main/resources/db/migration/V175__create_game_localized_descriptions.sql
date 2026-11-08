-- Dile göre oyun açıklaması. Varsayılan (İngilizce, sağlayıcı) açıklama games.description'da kalır ve ASLA değiştirilmez; bu tablo yalnız
-- sağlayıcının o dilde GERÇEKTEN verdiği ek metni saklar (çeviri yok; kaynak İngilizce ise satır oluşturulmaz). Başka dil eklemek şema
-- değişikliği gerektirmez (language kolonu). Yalnız Game Service'in kendi tabloları.
create table game_localized_descriptions (
    id uuid primary key,
    game_id uuid not null references games(id) on delete cascade,
    language varchar(35) not null check(language ~ '^[a-z]{2,3}(-[a-z0-9]{2,8}){0,3}$'),
    description varchar(16000) not null check(length(btrim(description)) > 0),
    source varchar(32) not null check(length(btrim(source)) > 0),
    fetched_at timestamptz not null,
    version bigint not null default 0,
    -- Aynı oyun ve dil için tek satır (yinelenen kayıt DB düzeyinde engellenir).
    constraint uq_game_localized_descriptions_game_language unique(game_id, language)
);
