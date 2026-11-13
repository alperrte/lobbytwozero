package com.ltz.lfp;

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
 * LFP-18 servis sınırları: ilan verisi yalnız LFP'dedir. Başka servisin kaynak koduna (paket bağımlılığı) ve
 * veritabanına doğrudan erişim yoktur; oyun ve kullanıcılar yalnız kimlikle (REF) tutulur, FK yalnız LFP'nin kendi
 * tabloları arasındadır.
 */
class ServiceBoundaryTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");

    private static final Pattern FOREIGN_IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?com\\.ltz\\.(?!lfp\\.)",
            Pattern.MULTILINE);
    private static final Pattern DATABASE_NAME = Pattern.compile("\\b([a-z]+_db)\\b");
    private static final Pattern FOREIGN_KEY_TARGET = Pattern.compile("(?i)\\breferences\\s+([a-z_]+)");

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
    void yapilandirmaYalnizLfpVeritabaninaBaglanir() throws IOException {
        String properties = Files.readString(MAIN_RESOURCES.resolve("application.properties"));

        Matcher matcher = DATABASE_NAME.matcher(properties);
        int found = 0;
        while (matcher.find()) {
            found++;
            assertThat(matcher.group(1)).isEqualTo("lfp_db");
        }
        assertThat(found).isPositive();
    }

    @Test
    void migrationlarBaskaServisinTablosunaDokunmazFkYalnizKendiTablolarina() throws IOException {
        Pattern foreignTable = Pattern.compile("(?i)\\b(accounts|user_profiles|privacy_settings|user_blocks|"
                + "friendships|conversations|messages|notifications|games|library_entries|reviews)\\b");
        try (Stream<Path> migrations = Files.list(MAIN_RESOURCES.resolve("db/migration"))) {
            for (Path migration : migrations.filter(path -> path.toString().endsWith(".sql")).toList()) {
                String sql = stripComments(Files.readString(migration));
                assertThat(foreignTable.matcher(sql).find())
                        .as("%s başka servisin tablosuna dokunmamalı", migration.getFileName())
                        .isFalse();
                Matcher target = FOREIGN_KEY_TARGET.matcher(sql);
                while (target.find()) {
                    assertThat(target.group(1)).as("%s yalnız LFP tablolarına FK verebilir", migration.getFileName())
                            .startsWith("lfp_");
                }
            }
        }
    }

    @Test
    void oyunVeKullaniciKimlikleriRefOlarakTutulur() throws IOException {
        try (Stream<Path> migrations = Files.list(MAIN_RESOURCES.resolve("db/migration"))) {
            for (Path migration : migrations.filter(path -> path.toString().endsWith(".sql")).toList()) {
                String sql = stripComments(Files.readString(migration));
                assertThat(Pattern.compile("(?i)foreign\\s+key\\s*\\(\\s*(owner_id|game_id|user_id|reporter_id)")
                        .matcher(sql).find())
                        .as("%s kullanıcı/oyun kimlikleri için FK içermemeli (REF)", migration.getFileName())
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
