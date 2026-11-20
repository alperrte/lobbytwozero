package com.ltz.recommendation.application.port;

import java.util.List;

public interface AssistantEngine {
    Reply advise(Input input);

    record Input(String contractVersion, String topic, String intent, String locale, boolean genreProvided,
            List<String> missingFields, String minimum, String recommended, String compatibility) {
        public Input { missingFields = List.copyOf(missingFields); }
    }
    record Reply(String contractVersion, String algorithmVersion, String topic, String intent, String locale,
            String status, String template, String message, List<String> steps, List<String> missingFields) {
        public Reply { steps = List.copyOf(steps); missingFields = List.copyOf(missingFields); }
    }
}
