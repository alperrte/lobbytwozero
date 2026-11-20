package com.ltz.recommendation.api.dto.response;

import com.ltz.recommendation.application.port.AssistantEngine.Reply;
import java.util.List;

public record AssistantResponse(String contractVersion, String algorithmVersion, String topic, String intent, String locale,
        String status, String template, String message, List<String> steps, List<String> missingFields) {
    public static AssistantResponse from(Reply reply) {
        return new AssistantResponse(reply.contractVersion(), reply.algorithmVersion(), reply.topic(), reply.intent(), reply.locale(),
                reply.status(), reply.template(), reply.message(), reply.steps(), reply.missingFields());
    }
}
