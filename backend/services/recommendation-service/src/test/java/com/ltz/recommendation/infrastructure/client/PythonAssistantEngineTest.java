package com.ltz.recommendation.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ltz.recommendation.application.port.AssistantEngine.Input;
import com.ltz.recommendation.application.service.AssistantQuery;
import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class PythonAssistantEngineTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PYTHON = PythonTestRuntime.executable();
    @TempDir private Path scripts;

    private PythonAssistantEngine engine() {
        return new PythonAssistantEngine(PYTHON, 2000);
    }

    private byte[] runScript(String script, byte[] input, long timeoutMs) {
        try {
            Path file = Files.createTempFile(scripts, "fixture-", ".py");
            Files.writeString(file, script, StandardCharsets.UTF_8);
            return BoundedPythonProcess.run(PYTHON, file, input, timeoutMs);
        } catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }

    @Test
    void threeTopicsRunRealPythonDeterministicallyWithVersionedTemplates() throws Exception {
        try (var engine = engine()) {
            for (Input input : List.of(input("GAME", "SELECT_GAME", List.of(), "UNKNOWN"),
                    input("HARDWARE", "ASSESS_GAME", List.of(), "BELOW"), input("LTZ_USAGE", "VIEW_HISTORY", List.of(), "UNKNOWN"),
                    input("LTZ_USAGE", "GIVE_FEEDBACK", List.of(), "UNKNOWN"))) {
                var reply = engine.advise(input);
                assertThat(reply).isEqualTo(engine.advise(input));
                assertThat(reply.contractVersion()).isEqualTo("assistant-v1");
                assertThat(reply.algorithmVersion()).isEqualTo("assistant-rules-v1");
                assertThat(reply.steps()).isNotEmpty();
                assertThat(reply.message()).isNotBlank();
            }
        }
    }

    @Test
    void missingInputAndUnknownHardwareNeverBecomeFpsOrCompatibilityGuarantees() throws Exception {
        try (var engine = engine()) {
            var missing = engine.advise(new Input("assistant-v1", "GAME", "SELECT_GAME", "tr", false,
                    List.of("GENRE"), "UNKNOWN", "UNKNOWN", "UNKNOWN"));
            assertThat(missing.status()).isEqualTo("NEEDS_INPUT");
            assertThat(missing.missingFields()).containsExactly("GENRE");
            var hardware = engine.advise(input("HARDWARE", "ASSESS_GAME", List.of(), "UNKNOWN"));
            assertThat(hardware.template()).isEqualTo("HARDWARE_UNKNOWN");
            assertThat(hardware.status()).isEqualTo("INSUFFICIENT_DATA");
            assertThat(engine.advise(new Input("assistant-v1", "HARDWARE", "ASSESS_GAME", "tr", false,
                    List.of(), "UNKNOWN", "UNKNOWN", "INCOMPATIBLE")).template()).isEqualTo("HARDWARE_DEFICIT");
            assertThat(engine.advise(input("HARDWARE", "ASSESS_GAME", List.of("GAME_ID"), "UNKNOWN")).status()).isEqualTo("NEEDS_INPUT");
        }
    }

    @Test
    void pythonRejectsUnknownContractAndUntrustedCodeInsteadOfExecutingIt() throws Exception {
        try (var engine = engine()) {
            assertThatThrownBy(() -> engine.advise(new Input("wrong", "GAME", "SELECT_GAME", "tr", true,
                    List.of(), "UNKNOWN", "UNKNOWN", "UNKNOWN"))).isInstanceOf(RecommendationRequestException.class);
            for (String payload : List.of("rpg; calc.exe", "$(whoami)", "__import__('os')", "https://example.invalid", "rpg\n")) {
                assertThatThrownBy(() -> new AssistantQuery("assistant-v1", "GAME", "SELECT_GAME", "tr", payload, null, null, null))
                        .isInstanceOf(IllegalArgumentException.class);
            }
            assertThatThrownBy(() -> engine.advise(new Input("assistant-v1", "GAME", "SELECT_GAME", "tr", true,
                    List.of("GENRE", "GENRE"), "UNKNOWN", "UNKNOWN", "UNKNOWN"))).isInstanceOf(RecommendationRequestException.class);
        }
    }

    @Test
    void unavailableInterpreterAndNoProcessSlotFailClosed() throws Exception {
        try (var unavailable = new PythonAssistantEngine("missing-python-runtime", 2000);
                var busy = new PythonAssistantEngine(PYTHON, 2000, new Semaphore(0))) {
            assertThatThrownBy(() -> unavailable.advise(input("GAME", "SELECT_GAME", List.of(), "UNKNOWN")))
                    .isInstanceOf(RecommendationRequestException.class);
            assertThatThrownBy(() -> busy.advise(input("GAME", "SELECT_GAME", List.of(), "UNKNOWN")))
                    .isInstanceOf(RecommendationRequestException.class).extracting("code").isEqualTo("ASSISTANT_BUSY");
            assertThatThrownBy(() -> invalidConfiguration(PYTHON, 2001)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> invalidConfiguration("", 2000)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static void invalidConfiguration(String executable, long timeoutMs) throws Exception {
        try (var invalid = new PythonAssistantEngine(executable, timeoutMs)) {
            invalid.advise(input("GAME", "SELECT_GAME", List.of(), "UNKNOWN"));
        }
    }

    @Test
    void timeoutOutputLimitsAndExitFailuresAreBoundedAndDoNotLeakProcessOutput() {
        for (String script : List.of("import time; time.sleep(5)", "import sys; sys.stdout.write('x'*17000)",
                "import sys; sys.stderr.write('x'*17000)", "import sys; sys.exit(3)", "import sys; sys.stderr.write('private-context')")) {
            assertThatThrownBy(() -> runScript(script, "{}".getBytes(StandardCharsets.UTF_8), 200))
                    .isInstanceOf(RecommendationRequestException.class).hasMessageNotContaining("private-context");
        }
        assertThatThrownBy(() -> runScript("pass", new byte[8193], 200))
                .isInstanceOf(RecommendationRequestException.class);
    }

    @Test
    void childDoesNotInheritApplicationEnvironmentAndInterruptedCallsRestoreTheFlag() {
        byte[] output = runScript("import os,json; print(json.dumps(sorted(os.environ)))", new byte[0], 2000);
        assertThat(new String(output, StandardCharsets.UTF_8)).doesNotContain("POSTGRES", "AUTH_", "SONAR_", "PYTHONPATH", "JAVA_TOOL_OPTIONS");
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> runScript("import time; time.sleep(5)", new byte[0], 2000))
                    .isInstanceOf(RecommendationRequestException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally { Thread.interrupted(); }
    }

    @Test
    void outputReaderRejectsMalformedVersionBindingSchemaAndUnsafeMessages() throws Exception {
        Input input = input("GAME", "SELECT_GAME", List.of(), "UNKNOWN");
        try (var engine = engine()) {
            var valid = JSON.valueToTree(engine.advise(input));
            assertThat(AssistantReplyReader.read(JSON.writeValueAsBytes(valid), input).template()).isEqualTo("GAME_SELECTION");
            for (String malformed : List.of("{broken", "[]", "null", valid + " {}")) {
                assertThatThrownBy(() -> AssistantReplyReader.read(malformed.getBytes(StandardCharsets.UTF_8), input))
                        .isInstanceOf(RecommendationRequestException.class);
            }
            for (String field : List.of("contractVersion", "algorithmVersion", "topic", "intent", "locale", "status", "template", "message")) {
                var changed = (tools.jackson.databind.node.ObjectNode) valid.deepCopy();
                changed.put(field, "<invalid>");
                assertThatThrownBy(() -> AssistantReplyReader.read(JSON.writeValueAsBytes(changed), input))
                        .isInstanceOf(RecommendationRequestException.class);
            }
            var extra = (tools.jackson.databind.node.ObjectNode) valid.deepCopy();
            extra.put("extra", true);
            assertThatThrownBy(() -> AssistantReplyReader.read(JSON.writeValueAsBytes(extra), input)).isInstanceOf(RecommendationRequestException.class);
        }
    }

    private static Input input(String topic, String intent, List<String> missing, String minimum) {
        return new Input("assistant-v1", topic, intent, "en", true, missing, minimum, "UNKNOWN", "UNKNOWN");
    }
}
