package com.ltz.game.api.dto.response;
import com.ltz.game.domain.enums.GameModuleType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
/** totalElements=null: dış modül henüz sorgulanamaz; boş ve tamamlanmış koleksiyon değildir. */
public record GameModuleResponse(UUID gameId, GameModuleType type, State state,
        List<Achievement> items, int page, int size, Long totalElements) {
    public enum State { AVAILABLE, EMPTY, INTEGRATION_PENDING }
    public record Achievement(UUID id,String code,String title,String description,boolean visible,
                              long version,String source,Instant updatedAt) { }
}
