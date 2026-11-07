package com.ltz.game.api.dto.response;
import com.ltz.game.domain.enums.GameModuleType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
/** totalElements=null: dış içerik bu yanıtta sorgulanmadı; boş ve tamamlanmış koleksiyon değildir. */
public record GameModuleResponse(UUID gameId, GameModuleType type, State state,
        List<Achievement> items, int page, int size, Long totalElements,
        @JsonInclude(JsonInclude.Include.NON_NULL) ContentSource contentSource) {
    public GameModuleResponse(UUID gameId, GameModuleType type, State state,
            List<Achievement> items, int page, int size, Long totalElements) {
        this(gameId,type,state,items,page,size,totalElements,null);
    }
    public enum State { AVAILABLE, EMPTY, INTEGRATION_PENDING, EXTERNAL }
    /** Kullanıcının kendi oturumuyla Gateway üzerinden okunacak kaynak; içerik veya toplam sayı değildir. */
    public record ContentSource(String service, String listPath) { }
    public record Achievement(UUID id,String code,String title,String description,boolean visible,
                              long version,String source,Instant updatedAt) { }
}
