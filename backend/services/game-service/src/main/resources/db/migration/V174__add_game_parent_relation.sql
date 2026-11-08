-- DLC -> ana oyun ilişkisi. Yalnız Game Service'in kendi games tablosu: yeni nullable kolon, kısıtlar, indeks ve iki tetikleyici.
-- Mevcut satırlar değişmez (parent_game_id hepsinde NULL başlar); oyun kimlikleri ve dış referanslar korunur.
alter table games add column parent_game_id uuid;

-- Ana oyun silinirse DLC silinmez, ilişki kopar (kayıt ve kimlik korunur).
alter table games add constraint fk_games_parent_game foreign key (parent_game_id) references games(id) on delete set null;
-- Kendine bağlanma yasak; yalnız DLC kaydı ana oyuna bağlanabilir.
alter table games add constraint games_parent_not_self check (parent_game_id is null or parent_game_id <> id);
alter table games add constraint games_parent_only_dlc check (parent_game_id is null or game_type = 'DLC');

-- Ana oyunun DLC listesi (sayfalı) ve DLC sayısı için; yalnız bağlı kayıtlar indekslenir.
create index ix_games_parent_game on games(parent_game_id) where parent_game_id is not null;

-- Ana oyun, var olan bir MAIN_GAME olmalıdır. Ana oyunun kendisi DLC olamayacağı (yukarıdaki kısıt yalnız DLC'ye ilişki izni verir)
-- ve ana oyunun üst oyunu olamayacağı için döngü ve zincir oluşamaz.
create function enforce_game_parent() returns trigger language plpgsql as $$
begin
    if not exists (select 1 from games parent where parent.id = new.parent_game_id and parent.game_type = 'MAIN_GAME') then
        raise exception 'DLC parent must be an existing MAIN_GAME' using errcode = '23514';
    end if;
    return new;
end;
$$;
create trigger games_parent_valid before insert or update of parent_game_id, game_type on games
for each row when (new.parent_game_id is not null) execute function enforce_game_parent();

-- Ana oyun MAIN_GAME olmaktan çıkarsa ona bağlı DLC'lerin ilişkisi kopar (kayıtlar korunur).
create function release_game_children() returns trigger language plpgsql as $$
begin
    update games set parent_game_id = null where parent_game_id = new.id;
    return new;
end;
$$;
create trigger games_release_children after update of game_type on games
for each row when (old.game_type = 'MAIN_GAME' and new.game_type <> 'MAIN_GAME') execute function release_game_children();
