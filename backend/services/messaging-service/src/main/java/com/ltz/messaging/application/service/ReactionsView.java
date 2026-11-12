package com.ltz.messaging.application.service;

import java.util.List;

/**
 * Mesajın tepki özeti (MSG-23): emoji başına sayı (çoktan aza, eşitlikte ilk tepki önce) ve isteyenin kendi tepkisi
 * ({@code mine}, yoksa {@code null}).
 */
public record ReactionsView(List<ReactionCount> summary, String mine) {

    public static final ReactionsView EMPTY = new ReactionsView(List.of(), null);

    public ReactionsView {
        summary = List.copyOf(summary);
    }
}
