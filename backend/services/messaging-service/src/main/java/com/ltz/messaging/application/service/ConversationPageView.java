package com.ltz.messaging.application.service;

import java.util.List;

public record ConversationPageView(List<ConversationSummaryView> items, String nextCursor) {
}
