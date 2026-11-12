package com.ltz.game.infrastructure.persistence.repository;

import com.ltz.game.application.port.GamePriceProvider.Market;
import com.ltz.game.application.port.GamePriceProvider.Quote;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository @RequiredArgsConstructor
public class GamePriceRepository {
    private static final String OWNER = "owner";
    private static final String COUNTRY = "country";
    private static final String CURRENCY = "currency";
    private static final String STATE_UPSERT = """
            insert into game_price_refresh_state(id,game_id,country,currency,status,checked_at,error_code) values (?,?,?,?,?,?,?)
            on conflict(game_id,country,currency) do update set status=excluded.status,checked_at=excluded.checked_at,error_code=excluded.error_code,next_attempt_at=null,version=game_price_refresh_state.version+1
            """;
    private final NamedParameterJdbcTemplate jdbc;
    /** Son aday sorgusunda birikim modunda (soğuk kademede hiç fiyatı olmayan çok oyun) bulunan pazarlar. */
    private final Set<Market> backlogMarkets=java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Pazar, son aday sorgusunda birikim modundaydı mı? Tur sırası birikimi olan pazarı öne alır (kota yalnız ona yetsin diye). */
    public boolean inBacklog(Market market) { return backlogMarkets.contains(market); }

    public boolean claim(UUID owner,Instant now) {
        return jdbc.update("update game_price_sync_lock set owner=:owner,expires_at=:until,version=version+1 where expires_at is null or expires_at < :now",
                Map.of(OWNER,owner,"until",java.sql.Timestamp.from(now.plusSeconds(120)),"now",java.sql.Timestamp.from(now)))==1;
    }
    public void release(UUID owner) { jdbc.update("update game_price_sync_lock set owner=null,expires_at=null,version=version+1 where owner=:owner",Map.of(OWNER,owner)); }
    /** Yenileme adayı ve kademesinin tazelik süresi (teklif bu süre boyunca taze sayılır). */
    public record Candidate(UUID id, Duration ttl) { }

    /**
     * Yenileme adayları, en çok 200 (ITAD arama sınırı). Kademe: editör seçimi veya son 90 günde çıkmış / 30 gün içinde çıkacak oyun
     * SICAK ({@code hot}); son 2 yılda çıkmış ILIK ({@code warm}); diğer (eski, tarihsiz, uzak gelecek) SOĞUK ({@code cold}). Bir oyun
     * kademesinin süresi dolduğunda (veya hiç denenmediğinde) adaydır. Sıra: editör seçimi, sıcak→ılık→soğuk, sonra en eski kontrol.
     * Gerçek popülerlik/talep verisi olmadığından popülerlik uydurulmaz; kademe yalnız çıkış tarihi ve editör seçiminden gelir.
     */
    public List<Candidate> candidates(Market market,Instant now,Duration hot,Duration warm,Duration cold) { return candidates(market,now,hot,warm,cold,200); }

    /**
     * Aynı sorgu, en çok {@code limit} aday. Sorgu üç büyük tabloyu tarar (oyunlar, yenileme durumu, dış kimlikler); bu yüzden tur, tek sorguda birkaç parti
     * aday alıp 200'lük partilere böler: parti başına bir tam tarama yerine tur başına bir tarama yapılır.
     */
    public List<Candidate> candidates(Market market,Instant now,Duration hot,Duration warm,Duration cold,int limit) {
        // Kademeler arası pay: sıkı öncelikte (sıcak→ılık→soğuk) sıcak kademenin talebi (süresi çabuk dolar) tüm kapasiteyi yer, ılık ve soğuk oyunlar
        // hiç sıraya gelmezdi. Her kademe payını alır; bir kademede payı dolduracak aday yoksa artan pay sonraki kademeye devredilir (ılık, sonra soğuk),
        // böylece ITAD kotasına ayrılmış istek boş gitmez.
        // Birikim modu: soğuk kademenin normal payı kadar HİÇ FİYATI OLMAYAN oyun bulunuyorsa (ilk dolum, uzun kesinti, yeni pazar) soğuk pay büyür ve
        // sıcak/ılık küçülür; hiç fiyatı olmayan oyun kalmayınca pay kendiliğinden normale döner (elle geri almak gerekmez).
        List<Row> coldRows=limit>0 ? tierRows(market,now,hot,warm,cold,cold,limit) : List.of();
        boolean backlog=isBacklog(coldRows,limit);
        if(backlog) backlogMarkets.add(market);
        else backlogMarkets.remove(market);
        Shares shares=backlog ? Shares.backlog(limit) : Shares.normal(limit);
        List<Row> hotRows=shares.hot()>0 ? tierRows(market,now,hot,warm,cold,hot,shares.hot()) : List.of();
        int warmQuota=shares.warm()+shares.hot()-hotRows.size();
        List<Row> warmRows=warmQuota>0 ? tierRows(market,now,hot,warm,cold,warm,warmQuota) : List.of();
        int coldQuota=Math.max(0,shares.cold()+warmQuota-warmRows.size());
        Map<UUID,Candidate> merged=new java.util.LinkedHashMap<>();
        for(List<Row> rows:List.of(hotRows,warmRows,coldRows.stream().limit(coldQuota).toList()))
            rows.forEach(row -> merged.putIfAbsent(row.candidate().id(),row.candidate()));
        return List.copyOf(merged.values());
    }

    /** Soğuk kademenin normal payı kadar hiç fiyatı olmayan oyun var mı (soğuk sıralamanın başındaki birikim penceresinde)? */
    private static boolean isBacklog(List<Row> coldRows,int limit) {
        int coldShare=Shares.normal(limit).cold();
        return coldShare>0 && coldRows.stream().limit(percent(limit,BACKLOG_COLD_SHARE_PERCENT)).filter(Row::never).count()>=coldShare;
    }

    /** Kademelere ayrılan aday sayıları. */
    private record Shares(int hot,int warm,int cold) {
        static Shares normal(int limit) {
            int hot=percent(limit,HOT_SHARE_PERCENT);
            int warm=percent(limit,WARM_SHARE_PERCENT);
            return new Shares(hot,warm,Math.max(0,limit-hot-warm));
        }
        static Shares backlog(int limit) { return new Shares(percent(limit,BACKLOG_HOT_SHARE_PERCENT),percent(limit,BACKLOG_WARM_SHARE_PERCENT),percent(limit,BACKLOG_COLD_SHARE_PERCENT)); }
    }

    /**
     * Fiyatı olmayan ({@code NO_OFFER}) oyunun yeniden kontrol aralığı, kademe süresinin bu katıdır. Teklifi olmayan oyunların çoğu (özellikle TR gibi dar kapsamlı
     * pazarlarda) teklif kazanmaz; her kademe süresinde yeniden sormak ITAD kotasını boşa harcar. Sayfası açılan oyun yine hemen yenilenir (ipucu yolu).
     * {@code GamePriceService.read} aynı çarpanı "teklif yok" gösterme süresine uygular.
     */
    public static final int NO_OFFER_INTERVAL_FACTOR = 6;

    /** Kademeler arası aday payı (yüzde): normalde sıcak %40, ılık %30, soğuk kalanı (%30). */
    static final int HOT_SHARE_PERCENT = 40;
    static final int WARM_SHARE_PERCENT = 30;
    /** Birikim modu payı (yüzde): sıcak %20, ılık %20, soğuk %60. */
    static final int BACKLOG_HOT_SHARE_PERCENT = 20;
    static final int BACKLOG_WARM_SHARE_PERCENT = 20;
    static final int BACKLOG_COLD_SHARE_PERCENT = 60;

    private static int percent(int limit,int percent) { return (limit*percent+99)/100; }

    /** Bir aday ve "hiç fiyatı olmayan (hiç başarıyla yenilenmemiş)" işareti. */
    private record Row(Candidate candidate,boolean never) { }

    private List<Row> tierRows(Market market,Instant now,Duration hot,Duration warm,Duration cold,Duration only,int limit) {
        Map<String,Object> params=new java.util.HashMap<>();
        params.put(COUNTRY,market.country());params.put(CURRENCY,market.currency());params.put("now",java.sql.Timestamp.from(now));params.put("limit",limit);
        params.put("noOfferFactor",NO_OFFER_INTERVAL_FACTOR);
        params.put("hot",hot.toSeconds());params.put("warm",warm.toSeconds());params.put("cold",cold.toSeconds());params.put("only",only.toSeconds());
        return jdbc.query("""
                select id, tier, (checked_at is null or checked_at < timestamptz '2000-01-01') as never from (
                    select g.id, g.editor_rank, s.checked_at, s.next_attempt_at, s.status,
                        case when g.editor_rank is not null or g.release_date between current_date - 90 and current_date + 30 then :hot
                             when g.release_date >= current_date - 730 and g.release_date < current_date - 90 then :warm
                             else :cold end as tier
                    from games g left join game_price_refresh_state s on s.game_id=g.id and s.country=:country and s.currency=:currency
                    where g.status='ACTIVE' and g.game_type in ('MAIN_GAME','DLC') and g.delisted_at is null
                      and exists(select 1 from game_external_refs r where r.game_id=g.id and r.provider='STEAM')
                ) c
                where tier = :only
                  and (checked_at is null
                       or checked_at < cast(:now as timestamptz) - make_interval(secs => tier * (case when status='NO_OFFER' then :noOfferFactor else 1 end)))
                  and (next_attempt_at is null or next_attempt_at <= cast(:now as timestamptz))
                order by (editor_rank is null), checked_at asc nulls first, id
                limit :limit
                """,params,(rs,index) -> new Row(new Candidate(rs.getObject("id",UUID.class),Duration.ofSeconds(rs.getLong("tier"))),rs.getBoolean("never")));
    }
    /**
     * Verilen oyunların kademesi ({@link #candidates} ile aynı kural): yalnız yenilenebilir (aktif ana oyun veya DLC, mağazadan kaldırılmamış, Steam referanslı) oyunlar döner.
     * Sayfası açılan oyunların anlık yenilemesi, planlı turdaki kademe süresiyle aynı gizleme süresini kullanabilsin diye gerekir.
     */
    public Map<UUID,Duration> tiers(Set<UUID> ids,Duration hot,Duration warm,Duration cold) {
        if(ids.isEmpty()) return Map.of();
        Map<String,Object> params=new java.util.HashMap<>();
        params.put("ids",ids);params.put("hot",hot.toSeconds());params.put("warm",warm.toSeconds());params.put("cold",cold.toSeconds());
        Map<UUID,Duration> result=new java.util.HashMap<>();
        jdbc.query("""
                select g.id, case when g.editor_rank is not null or g.release_date between current_date - 90 and current_date + 30 then :hot
                                  when g.release_date >= current_date - 730 and g.release_date < current_date - 90 then :warm
                                  else :cold end as tier
                from games g
                where g.id in (:ids) and g.status='ACTIVE' and g.game_type in ('MAIN_GAME','DLC') and g.delisted_at is null
                  and exists(select 1 from game_external_refs r where r.game_id=g.id and r.provider='STEAM')
                """,params,(org.springframework.jdbc.core.RowCallbackHandler) rs -> result.put(rs.getObject("id",UUID.class),Duration.ofSeconds(rs.getLong("tier"))));
        return Map.copyOf(result);
    }
    /**
     * Steam'in "ücretsiz" veya "henüz çıkmamış" dediği oyunlar: bu oyunların satın alma fiyatı yoktur, bu yüzden ITAD boşluğu için Steam'den
     * fiyat sorulmaz (gereksiz Steam çağrısı olmaz).
     */
    public Set<UUID> withoutSteamPrice(Set<UUID> ids) {
        if(ids.isEmpty()) return Set.of();
        return Set.copyOf(jdbc.query("select id from games where id in (:ids) and (free is true or coming_soon is true)",Map.of("ids",ids),
                (rs,index) -> rs.getObject("id",UUID.class)));
    }
    /** Ağ beklerken transaction yoktur. Kiralama kaybında eski worker hiçbir snapshot yayımlayamaz. */
    @Transactional
    public boolean publish(UUID owner,Set<UUID> ids,Market market,List<Quote> quotes,Instant now) {
        if(!owns(owner,now)) return false;
        if(quotes.stream().anyMatch(quote -> !ids.contains(quote.gameId()))) throw new IllegalArgumentException("İstenmeyen oyun fiyatı.");
        for(Quote quote:quotes) {
            if(!quote.market().equals(market) || quote.url()==null || quote.discountPercent()==null || quote.sourceUpdatedAt()==null)
                throw new IllegalArgumentException("Geçersiz fiyat snapshot'ı.");
        }
        // Parti başına tek silme + toplu ekleme + toplu durum yazımı (200 oyun için ≈800 tek tek istek yerine 3 istek); anlamı oyun başına döngüyle aynıdır.
        Map<String,Object> params=new java.util.HashMap<>();
        params.put(COUNTRY,market.country());params.put(CURRENCY,market.currency());params.put("ids",ids);
        jdbc.update("delete from game_price_quotes where game_id in (:ids) and country=:country and currency=:currency",params);
        List<Object[]> inserts=quotes.stream().map(quote -> new Object[]{UUID.randomUUID(),quote.gameId(),market.country(),market.currency(),quote.store(),quote.regularPrice(),
                quote.currentPrice(),quote.discountPercent(),quote.source(),quote.url(),java.sql.Timestamp.from(quote.sourceUpdatedAt()),
                java.sql.Timestamp.from(quote.fetchedAt()),java.sql.Timestamp.from(quote.expiresAt())}).toList();
        if(!inserts.isEmpty()) jdbc.getJdbcTemplate().batchUpdate("""
                insert into game_price_quotes(id,game_id,country,currency,store,regular_price,current_price,discount_percent,source,url,source_updated_at,fetched_at,expires_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,inserts);
        Set<UUID> withOffers=quotes.stream().map(Quote::gameId).collect(java.util.stream.Collectors.toSet());
        List<Object[]> states=ids.stream().map(id -> new Object[]{UUID.randomUUID(),id,market.country(),market.currency(),withOffers.contains(id)?"AVAILABLE":"NO_OFFER",
                java.sql.Timestamp.from(now),null}).toList();
        jdbc.getJdbcTemplate().batchUpdate(STATE_UPSERT,states);
        return true;
    }
    /**
     * Başarısız yenileme: oyun {@code FAILED} işaretlenir ve {@code next_attempt_at} verilmişse o zamana kadar aday olmaz. {@code checked_at} İLERLETİLMEZ:
     * başarısız deneme "taze kontrol" sayılsaydı oyun kademe süresi (soğukta 8 saat) boyunca yeniden denenmezdi. Mevcut satırda önceki kontrol zamanı
     * korunur, yeni satırda epoch yazılır (kolon boş olamaz); böylece bekleme bitince oyun hemen yeniden aday olur.
     */
    @Transactional
    public void failed(UUID owner,Set<UUID> ids,Market market,Instant now,String code,Duration retryAfter) {
        if(!owns(owner,now) || ids.isEmpty()) return;
        java.sql.Timestamp next=retryAfter==null ? null : java.sql.Timestamp.from(now.plus(retryAfter));
        List<Object[]> rows=ids.stream().map(id -> new Object[]{UUID.randomUUID(),id,market.country(),market.currency(),java.sql.Timestamp.from(Instant.EPOCH),code,next}).toList();
        jdbc.getJdbcTemplate().batchUpdate("""
                insert into game_price_refresh_state(id,game_id,country,currency,status,checked_at,error_code,next_attempt_at) values (?,?,?,?,'FAILED',?,?,?)
                on conflict(game_id,country,currency) do update set status='FAILED',error_code=excluded.error_code,next_attempt_at=excluded.next_attempt_at,version=game_price_refresh_state.version+1
                """,rows);
    }
    private boolean owns(UUID owner,Instant now) {
        return !jdbc.query("select owner from game_price_sync_lock where owner=:owner and expires_at>:now for update",
                Map.of(OWNER,owner,"now",java.sql.Timestamp.from(now)),(rs,index)->rs.getObject(OWNER,UUID.class)).isEmpty();
    }
    public List<Quote> quotes(Set<UUID> ids,Market market) {
        if(ids.isEmpty()) return List.of();
        return jdbc.query("select * from game_price_quotes where game_id in (:ids) and country=:country and currency=:currency order by current_price,store",Map.of("ids",ids,COUNTRY,market.country(),CURRENCY,market.currency()),
                (rs,index) -> new Quote(rs.getObject("game_id",UUID.class),rs.getString("store"),market,rs.getBigDecimal("regular_price"),rs.getBigDecimal("current_price"),
                        rs.getString("source"),instant(rs,"fetched_at"),instant(rs,"expires_at"),rs.getString("url"),rs.getInt("discount_percent"),instant(rs,"source_updated_at")));
    }
    /** Pazar başına okuma sayısını günlük satıra ekler; aynı gün tekrar yazılırsa toplanır. */
    @Transactional
    public void recordDemand(Map<Market,Long> reads,java.time.LocalDate day) {
        if(reads.isEmpty()) return;
        List<Object[]> rows=reads.entrySet().stream().map(entry -> new Object[]{UUID.randomUUID(),entry.getKey().country(),entry.getKey().currency(),java.sql.Date.valueOf(day),entry.getValue()}).toList();
        jdbc.getJdbcTemplate().batchUpdate("""
                insert into game_price_market_demand(id,country,currency,demand_date,read_count) values (?,?,?,?,?)
                on conflict(country,currency,demand_date) do update set read_count=game_price_market_demand.read_count+excluded.read_count,version=game_price_market_demand.version+1
                """,rows);
    }
    /** {@code since} gününden beri en az {@code minReads} kez okunmuş pazarlar, en çok okunandan başlayarak. */
    public List<Market> demandMarkets(java.time.LocalDate since,int minReads) {
        return jdbc.query("""
                select country, currency from game_price_market_demand where demand_date >= :since
                group by country, currency having sum(read_count) >= :minReads
                order by sum(read_count) desc, country, currency
                """,Map.of("since",java.sql.Date.valueOf(since),"minReads",minReads),(rs,index) -> new Market(rs.getString(COUNTRY),rs.getString(CURRENCY)));
    }
    /** {@code before} gününden eski sayaç satırlarını siler. */
    @Transactional
    public int pruneDemand(java.time.LocalDate before) {
        return jdbc.update("delete from game_price_market_demand where demand_date < :before",Map.of("before",java.sql.Date.valueOf(before)));
    }
    public Set<UUID> noOffers(Set<UUID> ids,Market market,Instant now,Duration ttl) {
        if(ids.isEmpty()) return Set.of();
        return Set.copyOf(jdbc.query("select game_id from game_price_refresh_state where game_id in (:ids) and country=:country and currency=:currency and status='NO_OFFER' and checked_at>:before",
                Map.of("ids",ids,COUNTRY,market.country(),CURRENCY,market.currency(),"before",java.sql.Timestamp.from(now.minus(ttl))),
                (rs,index)->rs.getObject("game_id",UUID.class)));
    }
    private static Map<String,Object> parameters(UUID id,Market market) {
        Map<String,Object> params=new java.util.HashMap<>();params.put("id",id);params.put(COUNTRY,market.country());params.put(CURRENCY,market.currency());return params;
    }
    private static Instant instant(ResultSet rs,String column) throws SQLException { return rs.getTimestamp(column).toInstant(); }
}
