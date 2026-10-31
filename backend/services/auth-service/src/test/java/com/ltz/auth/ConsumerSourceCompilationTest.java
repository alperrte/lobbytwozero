package com.ltz.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Hardware'in testi ({@code ProductionAuthSupport}) Auth'un üretim kaynaklarını yalnızca aşağıdaki sabit kümeyle
 * derleyip çalıştırır (docs-auth/README.md, "Başkalarının bağlı olduğu Auth imzaları"). Bu kümedeki bir dosya küme dışı
 * bir Auth tipine bağımlı olursa Hardware'in testi {@code cannot find symbol} ile kırılır, ama Auth'un kendi testleri
 * geçer. Bu test aynı derlemeyi Auth tarafında yapar ve hatayı Auth'un PR'ında yakalar.
 *
 * <p>Küme değişirse (Hardware kendi listesini güncellerse) bu liste de güncellenir.
 */
class ConsumerSourceCompilationTest {

    private static final List<String> HARDWARE_COMPILE_SET = List.of(
            "domain/entity/Account", "domain/entity/AuthSession", "domain/entity/Role",
            "domain/enums/AccountStatus", "domain/enums/RoleCode", "domain/enums/AuthMethod",
            "domain/enums/SessionRevokeReason", "infrastructure/config/JwtProperties",
            "infrastructure/security/JwtSigningKey", "infrastructure/security/TokenClaims",
            "infrastructure/security/AccessTokenIssuer", "infrastructure/security/ServiceTokenIssuer",
            "infrastructure/security/DeviceTokenIssuer", "api/controller/JwksController", "api/controller/AuthApiPaths");

    @Test
    void hardwareninDerledigiAuthKaynaklariKumeDisiTipeBagimliOlmadanDerlenir(@TempDir Path output) throws Exception {
        Path sourceRoot = Path.of("src/main/java/com/ltz/auth").toAbsolutePath();
        assertThat(sourceRoot).isDirectory();
        List<String> sources = HARDWARE_COMPILE_SET.stream()
                .map(name -> sourceRoot.resolve(name + ".java").toString()).toList();
        assertThat(sources).allSatisfy(source -> assertThat(Path.of(source)).exists());

        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK gerekir").isNotNull();
        // Hardware'in sınıf yolunda Auth'un kendi derlenmiş sınıfları yoktur; burada da çıkarılır, yoksa küme dışı bir Auth
        // tipi 'target/classes' içinden bulunur ve bozukluk gizlenir.
        Path ownOutput = Path.of("target").toAbsolutePath().normalize();
        String classpath = java.util.Arrays.stream(System.getProperty("surefire.test.class.path",
                        System.getProperty("java.class.path")).split(java.io.File.pathSeparator))
                .filter(entry -> !Path.of(entry).toAbsolutePath().normalize().startsWith(ownOutput))
                .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        try (var files = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            StringWriter diagnostics = new StringWriter();
            boolean success = compiler.getTask(diagnostics, files, null,
                    List.of("--release", "25", "-encoding", "UTF-8", "-classpath", classpath, "-processorpath", classpath,
                            "-processor", "lombok.launch.AnnotationProcessorHider$AnnotationProcessor", "-d",
                            output.toString()),
                    null, files.getJavaFileObjectsFromStrings(sources)).call();
            assertThat(success).as("Hardware'in derleme kümesi tek başına derlenmeli: %s", diagnostics).isTrue();
        }
        assertThat(Files.exists(output.resolve("com/ltz/auth/infrastructure/security/DeviceTokenIssuer.class"))).isTrue();
    }
}
