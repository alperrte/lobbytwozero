-- GAME-24: hangi fiyat pazarlarının gerçekten okunduğunu günlük sayaçla tutar. Arka plan yenilemesi yalnız sabit pazarlar ve son günlerde okunan pazarlar için
-- çalışır; diğer pazarlar yalnız sayfa açılışında yenilenir (ITAD kotası dar). Kişisel veri yoktur: yalnız pazar ve gün başına okuma sayısı tutulur.
create table game_price_market_demand (
    id uuid primary key,
    country varchar(2) not null check(country ~ '^[A-Z]{2}$'),
    currency varchar(3) not null check(currency ~ '^[A-Z]{3}$'),
    demand_date date not null,
    read_count bigint not null check(read_count > 0),
    version bigint not null default 0,
    unique(country,currency,demand_date)
);
create index ix_game_price_market_demand_date on game_price_market_demand(demand_date);
