package com.ltz.recommendation.infrastructure.client;

import java.nio.file.Files;
import java.nio.file.Path;

/** Testler runtime yokluğunu atlamaz; Windows'ta onaylı portable interpreter kullanılır. */
public final class PythonTestRuntime {
    private PythonTestRuntime() { }
    public static String executable() {
        String configured = System.getProperty("ltz.recommendation.assistant.python-executable");
        if (configured != null) return configured;
        if (!System.getProperty("os.name").startsWith("Windows")) return "python3";
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("backend/pom.xml"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Repository root unavailable");
        Path executable = root.resolve(".cache/tools/python/3.14.10/python.exe");
        if (!Files.isRegularFile(executable)) throw new IllegalStateException("Approved Python test runtime unavailable");
        return executable.toString();
    }
}
