package com.ltz.messaging.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ltz.messaging.application.service.MessageReadsView;
import com.ltz.messaging.domain.enums.ReadStatus;

/** Mesajı görenler (MSG-12): okuyanlar saatiyle, okumayanlar yalnız kimlikle. */
public record MessageReadsResponse(UUID messageId, long seq, ReadStatus readStatus, List<ReaderResponse> readers,
                                   List<PendingResponse> pending) {

    /** @param readAt okuma saati; migration öncesi okumalarda {@code null} */
    public record ReaderResponse(UUID userId, Instant readAt) {
    }

    public record PendingResponse(UUID userId) {
    }

    public static MessageReadsResponse from(MessageReadsView view) {
        return new MessageReadsResponse(view.messageId(), view.seq(), view.readStatus(),
                view.readers().stream().map(reader -> new ReaderResponse(reader.userId(), reader.readAt())).toList(),
                view.pending().stream().map(PendingResponse::new).toList());
    }
}
