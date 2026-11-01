package com.ltz.messaging.application.service;

/** Birebir sohbet açma sonucu: {@code created} yeni sohbet mi, yoksa mevcut sohbet mi döndü (201 / 200). */
public record DirectConversationResult(ConversationView conversation, boolean created) {
}
