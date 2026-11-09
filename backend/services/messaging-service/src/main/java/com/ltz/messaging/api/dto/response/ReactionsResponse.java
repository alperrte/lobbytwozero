package com.ltz.messaging.api.dto.response;

import java.util.List;

import com.ltz.messaging.application.service.ReactionCount;
import com.ltz.messaging.application.service.ReactionsView;

/** Mesajın tepki özeti (MSG-23): emoji başına sayı (çoktan aza) ve isteyenin kendi tepkisi ({@code mine}). */
public record ReactionsResponse(List<ReactionCountResponse> summary, String mine) {

    /** Bir emojinin tepki sayısı. */
    public record ReactionCountResponse(String emoji, long count) {

        static ReactionCountResponse from(ReactionCount count) {
            return new ReactionCountResponse(count.emoji(), count.count());
        }
    }

    public static ReactionsResponse from(ReactionsView view) {
        ReactionsView source = view == null ? ReactionsView.EMPTY : view;
        return new ReactionsResponse(source.summary().stream().map(ReactionCountResponse::from).toList(),
                source.mine());
    }
}
