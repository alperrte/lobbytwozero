package com.ltz.social;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * SOC-15 servis sınırları: Social yalnız kendi verisine dokunur. Başka servisin kaynak koduna (paket bağımlılığı) ve
 * başka servisin veritabanına doğrudan erişim bulunmaz; servisler arası ilişki yalnız kimlik (REF) ve sözleşmedir.
 */
class ServiceBoundaryTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");

    private static final Pattern FOREIGN_IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?com\\.ltz\\.(?!social\\.)",
            Pattern.MULTILINE);
    private static final Pattern DATABASE_NAME = Pattern.compile("\\b([a-z]+_db)\\b");

    @Test
    void kaynakKodBaskaServisinPaketlerineBagimliDegil() throws IOException {
        List<Path> sources = javaSources();

        assertThat(sources).isNotEmpty();
        for (Path source : sources) {
            assertThat(FOREIGN_IMPORT.matcher(Files.readString(source)).find())
                    .as("%s başka bir LTZ servisinin paketini import etmemeli", source)
                    .isFalse();
        }
    }

    @Test
    void yapilandirmaYalnizSocialVeritabaninaBaglanir() throws IOException {
        String properties = Files.readString(MAIN_RESOURCES.resolve("application.properties"));

        Matcher matcher = DATABASE_NAME.matcher(properties);
        int found = 0;
        while (matcher.find()) {
            found++;
            assertThat(matcher.group(1)).isEqualTo("social_db");
        }
        assertThat(found).isPositive();
    }

    @Test
    void migrationlarYalnizKendiTablolariniYonetir_digerServisinTablosunaDokunmaz() throws IOException {
        Pattern foreignTable = Pattern.compile("(?i)\\b(accounts|user_profiles|privacy_settings|conversations|messages|"
                + "lfp_listings|notifications|games|library_entries|reviews)\\b");
        try (Stream<Path> migrations = Files.list(MAIN_RESOURCES.resolve("db/migration"))) {
            for (Path migration : migrations.filter(path -> path.toString().endsWith(".sql")).toList()) {
                String sql = stripComments(Files.readString(migration));
                assertThat(foreignTable.matcher(sql).find())
                        .as("%s başka servisin tablosuna dokunmamalı", migration.getFileName())
                        .isFalse();
            }
        }
    }

    @Test
    void kullanicilarYalnizKimlikleRefOlarakTutulur_yabanciAnahtarYok() throws IOException {
        try (Stream<Path> migrations = Files.list(MAIN_RESOURCES.resolve("db/migration"))) {
            for (Path migration : migrations.filter(path -> path.toString().endsWith(".sql")).toList()) {
                String sql = stripComments(Files.readString(migration));
                assertThat(Pattern.compile("(?i)\\b(references|foreign\\s+key)\\b").matcher(sql).find())
                        .as("%s kullanıcı kimlikleri için FK içermemeli (REF)", migration.getFileName())
                        .isFalse();
            }
        }
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static String stripComments(String sql) {
        return sql.replaceAll("(?m)--.*$", "");
    }
}
