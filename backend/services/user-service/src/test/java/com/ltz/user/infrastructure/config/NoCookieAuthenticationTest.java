package com.ltz.user.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Değişmez (C004): {@code SecurityConfig} CSRF'yi kapatır; bu yalnızca servis çerezden kimlik okumadığı sürece
 * güvenlidir (CSRF/Origin denetimi ve çerez-Bearer çevirisi Gateway'dedir). Biri bu serviste çerez okuyan bir kod
 * eklerse bu test kırılır ve CSRF kararı yeniden değerlendirilmelidir.
 */
class NoCookieAuthenticationTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    private static final Pattern COOKIE_READ = Pattern.compile(
            "getCookies\\s*\\(|@CookieValue|WebUtils\\s*\\.\\s*getCookie|BearerTokenResolver|CookieBearer");

    @Test
    void servisCerezdenKimlikOkumaz() throws IOException {
        assertThat(MAIN_SOURCES).isDirectory();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            List<String> offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(NoCookieAuthenticationTest::readsCookies)
                    .map(path -> MAIN_SOURCES.relativize(path).toString())
                    .toList();
            assertThat(offenders)
                    .as("çerezden kimlik/çerez okuyan sınıf varsa SecurityConfig'teki csrf().disable() gözden geçirilmeli")
                    .isEmpty();
        }
    }

    @Test
    void csrfKararinaDairGerekceSecurityConfigteYazilidir() throws IOException {
        Path config = MAIN_SOURCES.resolve(Path.of("com", "ltz", "user", "infrastructure", "config", "SecurityConfig.java"));
        assertThat(Files.readString(config)).contains("Gateway").contains("csrf");
    }

    private static boolean readsCookies(Path path) {
        try {
            return COOKIE_READ.matcher(Files.readString(path)).find();
        } catch (IOException exception) {
            throw new IllegalStateException("Kaynak okunamadı: " + path, exception);
        }
    }
}
