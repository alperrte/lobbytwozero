package com.ltz.recommendation.infrastructure.client;

import com.ltz.recommendation.domain.exception.RecommendationRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class BoundedPythonProcess {
    private BoundedPythonProcess() { }

    static byte[] run(String executable, Path script, byte[] input, long timeoutMs) {
        if (input.length > 8192) throw failure("ASSISTANT_INPUT_LIMIT");
        var builder = new ProcessBuilder(executable, "-I", "-S", "-B", script.toAbsolutePath().toString());
        builder.directory(script.toAbsolutePath().getParent().toFile());
        builder.environment().clear();
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null) builder.environment().put("SystemRoot", systemRoot);
        try {
            return exchange(builder.start(), input, timeoutMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("ASSISTANT_INTERRUPTED", exception);
        } catch (IOException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw failure("ASSISTANT_UNAVAILABLE", exception);
        }
    }

    private static byte[] exchange(Process process, byte[] input, long timeoutMs)
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
                Future<byte[]> output = executor.submit(() -> bounded(process.getInputStream()));
                Future<byte[]> errors = executor.submit(() -> bounded(process.getErrorStream()));
                Future<?> written = executor.submit(() -> {
                    try (var stream = process.getOutputStream()) { stream.write(input); }
                    return null;
                });
                if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) throw failure("ASSISTANT_TIMEOUT");
                written.get(remaining(deadline), TimeUnit.NANOSECONDS);
                byte[] bytes = output.get(remaining(deadline), TimeUnit.NANOSECONDS);
                byte[] stderr = errors.get(remaining(deadline), TimeUnit.NANOSECONDS);
                if (process.exitValue() != 0 || stderr.length != 0) throw failure("ASSISTANT_UNAVAILABLE");
                return bytes;
            } finally {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.getInputStream().close();
                process.getErrorStream().close();
                process.getOutputStream().close();
            }
        }
    }

    private static long remaining(long deadline) { return Math.max(1, deadline - System.nanoTime()); }

    private static byte[] bounded(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(16385);
        if (bytes.length > 16384) throw new IOException("Assistant output limit");
        return bytes;
    }

    static RecommendationRequestException failure(String code) { return new RecommendationRequestException(503, code); }

    static RecommendationRequestException failure(String code, Throwable cause) {
        var exception = failure(code);
        exception.initCause(cause);
        return exception;
    }
}
