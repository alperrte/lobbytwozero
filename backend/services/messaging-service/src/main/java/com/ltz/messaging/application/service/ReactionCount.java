package com.ltz.messaging.application.service;

/** Bir emoji ile verilmiş tepki sayısı (MSG-23). */
public record ReactionCount(String emoji, long count) {
}
