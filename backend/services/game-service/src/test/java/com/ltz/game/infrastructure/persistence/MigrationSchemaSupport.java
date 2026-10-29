package com.ltz.game.infrastructure.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Eski ve yeni izole şemalar için ortak, salt okunur karşılaştırma. */
final class MigrationSchemaSupport {
    private MigrationSchemaSupport() { }
    static List<Integer> schemaCounts(DriverManagerDataSource source) {
        var jdbc = new JdbcTemplate(source);
        return List.of(
                jdbc.queryForObject("select count(*) from information_schema.columns where table_schema='public' and table_name<>'flyway_schema_history'", Integer.class),
                jdbc.queryForObject("select count(*) from pg_constraint p join pg_class c on c.oid=p.conrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname<>'flyway_schema_history'", Integer.class),
                jdbc.queryForObject("select count(*) from pg_indexes where schemaname='public' and tablename<>'flyway_schema_history'", Integer.class),
                jdbc.queryForObject("select count(*) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public'", Integer.class),
                jdbc.queryForObject("select count(*) from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and not t.tgisinternal", Integer.class),
                jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name<>'flyway_schema_history'", Integer.class));
    }
    static List<String> schema(DriverManagerDataSource source) {
        List<String> queries = List.of(
                "select table_name,ordinal_position,column_name,data_type,udt_name,character_maximum_length,numeric_precision,numeric_scale,is_nullable,column_default,collation_name,identity_generation,is_generated,generation_expression from information_schema.columns where table_schema='public' and table_name<>'flyway_schema_history' order by table_name,ordinal_position",
                "select c.relname,conname,contype,condeferrable,condeferred,convalidated,pg_get_constraintdef(p.oid) definition from pg_constraint p join pg_class c on c.oid=p.conrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relname<>'flyway_schema_history' order by c.relname,conname",
                "select tablename,indexname,indexdef from pg_indexes where schemaname='public' and tablename<>'flyway_schema_history' order by tablename,indexname",
                "select proname,pg_get_functiondef(p.oid) definition from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' order by proname",
                "select c.relname,t.tgname,pg_get_triggerdef(t.oid) definition from pg_trigger t join pg_class c on c.oid=t.tgrelid join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and not t.tgisinternal order by c.relname,t.tgname",
                "select c.relname,c.relkind,c.relpersistence,c.relrowsecurity,c.relforcerowsecurity from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p') and c.relname<>'flyway_schema_history' order by c.relname");
        var jdbc = new JdbcTemplate(source);
        List<String> signature = new ArrayList<>();
        for (String query : queries) {
            signature.addAll(jdbc.queryForList("select row_to_json(t)::text from (" + query + ") t", String.class));
        }
        return signature;
    }

    static void validateHibernate(DriverManagerDataSource source) throws Exception {
        try (var registry = new StandardServiceRegistryBuilder().applySettings(Map.of(
                "hibernate.connection.url", Objects.requireNonNull(source.getUrl()),
                "hibernate.connection.username", Objects.requireNonNull(source.getUsername()),
                "hibernate.connection.password", Objects.requireNonNull(source.getPassword()),
                "hibernate.hbm2ddl.auto", "validate", "hibernate.show_sql", "false")).build()) {
            var sources = new MetadataSources(registry);
            try (var files = Files.list(Path.of("src/main/java/com/ltz/game/domain/entity"))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    var entity = Class.forName("com.ltz.game.domain.entity."
                            + file.getFileName().toString().replace(".java", ""));
                    if (entity.isAnnotationPresent(jakarta.persistence.Entity.class)) sources.addAnnotatedClass(entity);
                }
            }
            try (var factory = sources.getMetadataBuilder().applyPhysicalNamingStrategy(
                    new PhysicalNamingStrategySnakeCaseImpl()).build().buildSessionFactory()) {
                assertTrue(factory.isOpen());
            }
        }
    }
}
