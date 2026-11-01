package com.ltz.messaging;

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
 * MSG-27 servis sınırları (bu taskta dokunulan kısım): Messaging başka servisin kaynak koduna ve veritabanına doğrudan
 * erişmez; kullanıcılar yalnız kimlikle (REF) tutulur. Engel verisi Social'dadır ve yalnız API ile sorulur.
 */
class ServiceBoundaryTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");

    private static final Pattern FOREIGN_IMPORT = Pattern.compile(
            "^import\\s+(?:static\\s+)?com\\.ltz\\.(?!messaging\\.)", Pattern.MULTILINE);
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
    void yapilandirmaYalnizMessagingVeritabaninaBaglanir() throws IOException {
        String properties = Files.readString(MAIN_RESOURCES.resolve("application.properties"));

        Matcher matcher = DATABASE_NAME.matcher(properties);
        int found = 0;
        while (matcher.find()) {
            found++;
            assertThat(matcher.group(1)).isEqualTo("messaging_db");
        }
        assertThat(found).isPositive();
    }

    @Test
    void migrationlarBaskaServisinTablosunaDokunmazYabanciAnahtarYalnizServisIci() throws IOException {
        Pattern foreignTable = Pattern.compile("(?i)\\b(accounts|user_profiles|user_blocks|friendships|"
                + "friend_requests|lfp_listings|notifications)\\b");
        Pattern reference = Pattern.compile("(?i)\\breferences\\s+(\\w+)");
        try (Stream<Path> migrations = Files.list(MAIN_RESOURCES.resolve("db/migration"))) {
            for (Path migration : migrations.filter(path -> path.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(migration).replaceAll("(?m)--.*$", "");
                assertThat(foreignTable.matcher(sql).find())
                        .as("%s başka servisin tablosuna dokunmamalı", migration.getFileName())
                        .isFalse();
                Matcher references = reference.matcher(sql);
                while (references.find()) {
                    assertThat(references.group(1)).as("FK yalnız messaging tablolarına").isEqualTo("conversations");
                }
            }
        }
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
