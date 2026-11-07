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
    private final NamedParameterJdbcTemplate jdbc;
    public boolean claim(UUID owner,Instant now) {
        return jdbc.update("update game_price_sync_lock set owner=:owner,expires_at=:until,version=version+1 where expires_at is null or expires_at < :now",
                Map.of(OWNER,owner,"until",java.sql.Timestamp.from(now.plusSeconds(120)),"now",java.sql.Timestamp.from(now)))==1;
    }
    public void release(UUID owner) { jdbc.update("update game_price_sync_lock set owner=null,expires_at=null,version=version+1 where owner=:owner",Map.of(OWNER,owner)); }
    public List<UUID> candidates(Market market,Instant now,Duration ttl) {
        return jdbc.query("""
                select g.id from games g left join game_price_refresh_state s on s.game_id=g.id and s.country=:country and s.currency=:currency
                where g.status='ACTIVE' and g.game_type='MAIN_GAME' and exists(select 1 from game_external_refs r where r.game_id=g.id and r.provider='STEAM')
                and (s.checked_at is null or s.checked_at < :before)
                and (s.next_attempt_at is null or s.next_attempt_at <= current_timestamp)
                order by s.checked_at asc nulls first,g.id limit 50
                """,Map.of(COUNTRY,market.country(),CURRENCY,market.currency(),"before",java.sql.Timestamp.from(now.minus(ttl))),
                (rs,index) -> rs.getObject("id",UUID.class));
    }
    /** Ağ beklerken transaction yoktur. Kiralama kaybında eski worker hiçbir snapshot yayımlayamaz. */
    @Transactional
    public boolean publish(UUID owner,Set<UUID> ids,Market market,List<Quote> quotes,Instant now) {
        if(!owns(owner,now)) return false;
        if(quotes.stream().anyMatch(quote -> !ids.contains(quote.gameId()))) throw new IllegalArgumentException("İstenmeyen oyun fiyatı.");
        for(UUID id:ids) {
            var params=parameters(id,market);
            jdbc.update("delete from game_price_quotes where game_id=:id and country=:country and currency=:currency",params);
            var selected=quotes.stream().filter(quote -> quote.gameId().equals(id)).toList();
            for(Quote quote:selected) {
                if(!quote.market().equals(market) || quote.url()==null || quote.discountPercent()==null || quote.sourceUpdatedAt()==null)
                    throw new IllegalArgumentException("Geçersiz fiyat snapshot'ı.");
                jdbc.getJdbcTemplate().update("""
                        insert into game_price_quotes(id,game_id,country,currency,store,regular_price,current_price,discount_percent,source,url,source_updated_at,fetched_at,expires_at)
                        values (?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """,UUID.randomUUID(),id,market.country(),market.currency(),quote.store(),quote.regularPrice(),quote.currentPrice(),
                        quote.discountPercent(),quote.source(),quote.url(),java.sql.Timestamp.from(quote.sourceUpdatedAt()),
                        java.sql.Timestamp.from(quote.fetchedAt()),java.sql.Timestamp.from(quote.expiresAt()));
            }
            state(id,market,selected.isEmpty()?"NO_OFFER":"AVAILABLE",now,null);
        }
        return true;
    }
    @Transactional
    public void failed(UUID owner,Set<UUID> ids,Market market,Instant now,String code,Duration retryAfter) {
        if(owns(owner,now)) for(UUID id:ids) {
            state(id,market,"FAILED",now,code);
            if(retryAfter!=null) jdbc.update("update game_price_refresh_state set next_attempt_at=:next where game_id=:id and country=:country and currency=:currency",
                    Map.of("id",id,COUNTRY,market.country(),CURRENCY,market.currency(),"next",java.sql.Timestamp.from(now.plus(retryAfter))));
        }
    }
    private boolean owns(UUID owner,Instant now) {
        return !jdbc.query("select owner from game_price_sync_lock where owner=:owner and expires_at>:now for update",
                Map.of(OWNER,owner,"now",java.sql.Timestamp.from(now)),(rs,index)->rs.getObject(OWNER,UUID.class)).isEmpty();
    }
    private void state(UUID id,Market market,String status,Instant now,String code) {
        jdbc.getJdbcTemplate().update("""
                insert into game_price_refresh_state(id,game_id,country,currency,status,checked_at,error_code) values (?,?,?,?,?,?,?)
                on conflict(game_id,country,currency) do update set status=excluded.status,checked_at=excluded.checked_at,error_code=excluded.error_code,next_attempt_at=null,version=game_price_refresh_state.version+1
                """,UUID.randomUUID(),id,market.country(),market.currency(),status,java.sql.Timestamp.from(now),code);
    }
    public List<Quote> quotes(Set<UUID> ids,Market market) {
        if(ids.isEmpty()) return List.of();
        return jdbc.query("select * from game_price_quotes where game_id in (:ids) and country=:country and currency=:currency order by current_price,store",Map.of("ids",ids,COUNTRY,market.country(),CURRENCY,market.currency()),
                (rs,index) -> new Quote(rs.getObject("game_id",UUID.class),rs.getString("store"),market,rs.getBigDecimal("regular_price"),rs.getBigDecimal("current_price"),
                        rs.getString("source"),instant(rs,"fetched_at"),instant(rs,"expires_at"),rs.getString("url"),rs.getInt("discount_percent"),instant(rs,"source_updated_at")));
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
