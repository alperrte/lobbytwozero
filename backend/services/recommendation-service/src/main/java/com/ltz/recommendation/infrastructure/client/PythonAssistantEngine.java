package com.ltz.recommendation.infrastructure.client;

import static com.ltz.recommendation.infrastructure.client.BoundedPythonProcess.failure;

import com.ltz.recommendation.application.port.AssistantEngine;
import java.io.IOException;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PythonAssistantEngine implements AssistantEngine, AutoCloseable {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final String executable;
    private final long timeoutMs;
    private final Path script;
    private final Semaphore slots;

    @Autowired
    public PythonAssistantEngine(@Value("${ltz.recommendation.assistant.python-executable:python3}") String executable,
            @Value("${ltz.recommendation.assistant.timeout-ms:2000}") long timeoutMs) {
        this(executable, timeoutMs, new Semaphore(2));
    }

    PythonAssistantEngine(String executable, long timeoutMs, Semaphore slots) {
        if (executable == null || executable.isBlank() || timeoutMs < 1 || timeoutMs > 2000) {
            throw new IllegalArgumentException("Invalid assistant runtime configuration");
        }
        this.executable = executable;
        this.timeoutMs = timeoutMs;
        this.slots = slots;
        try (var resource = new ClassPathResource("assistant/assistant_rules.py").getInputStream()) {
            script = Files.createTempDirectory("ltz-assistant-").resolve("assistant_rules.py");
            Files.copy(resource, script);
        } catch (IOException exception) { throw new IllegalStateException("Assistant resource missing", exception); }
    }

    @Override
    public Reply advise(Input input) {
        if (!slots.tryAcquire()) throw failure("ASSISTANT_BUSY");
        try {
            byte[] output = BoundedPythonProcess.run(executable, script, JSON.writeValueAsBytes(input), timeoutMs);
            return AssistantReplyReader.read(output, input);
        } finally { slots.release(); }
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        Files.deleteIfExists(script);
        Files.deleteIfExists(script.getParent());
    }
}
