package com.ltz.community.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Persistence testlerini paylaşılan geliştirme verisinden yalıtır: testler hiçbir zaman {@code community_db}'ye
 * (Compose'taki ortak veritabanı) yazmaz.
 *
 * <p>Ortamda {@code POSTGRES_PASSWORD} tanımlıysa (pre-push ve Sonar taraması Compose profilinden sunucu adresini,
 * kullanıcıyı ve parolayı verir) aynı PostgreSQL sunucusunda bu test çalışmasına özel, rastgele adlı GEÇİCİ bir
 * veritabanı oluşturulur ve JVM kapanırken silinir. Böylece testler boş bir veritabanı varsayabilir, tabloları
 * temizleyebilir ve ortak verilere (gerçek gönderiler, bekleyen outbox olayları) hiçbir şekilde dokunmaz. Kullanıcı
 * veritabanı oluşturamıyorsa ya da ortam boşsa (IDE, doğrudan Maven) Testcontainers ile geçici bir PostgreSQL 17 başlatılır.
 */
public abstract class PostgreSqlIntegrationTest {

	private static final String DATABASE_PREFIX = "ltz_community_test_";

	/** Geçici veritabanının JDBC adresi; yoksa (null) Testcontainers kullanılır. */
	private static final String SCRATCH_URL = createScratchDatabase();

	private static final PostgreSQLContainer<?> POSTGRES = SCRATCH_URL != null ? null : startContainer();

	private static String createScratchDatabase() {
		String password = System.getenv("POSTGRES_PASSWORD");
		if (password == null) {
			return null;
		}
		String host = valueOrDefault(System.getenv("POSTGRES_HOST"), "localhost");
		String port = valueOrDefault(System.getenv("POSTGRES_PORT"), "5542");
		String user = valueOrDefault(System.getenv("POSTGRES_USER"), "ltz_dev");
		String database = DATABASE_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		String serverUrl = "jdbc:postgresql://" + host + ":" + port + "/";
		try (Connection admin = DriverManager.getConnection(serverUrl + "postgres?connectTimeout=10", user, password);
				Statement statement = admin.createStatement()) {
			// Ad yalnız bu sınıfın ürettiği güvenli karakterlerden oluşur (enjeksiyon yüzeyi yoktur).
			statement.execute("create database \"" + database + "\"");
		} catch (SQLException exception) {
			System.err.println("Scratch database could not be created (" + exception.getSQLState()
					+ "); falling back to Testcontainers.");
			return null;
		}
		Runtime.getRuntime().addShutdownHook(new Thread(() -> dropScratchDatabase(serverUrl, user, password, database),
				"community-test-db-drop"));
		return serverUrl + database + "?connectTimeout=60";
	}

	private static void dropScratchDatabase(String serverUrl, String user, String password, String database) {
		try (Connection admin = DriverManager.getConnection(serverUrl + "postgres?connectTimeout=10", user, password);
				Statement statement = admin.createStatement()) {
			statement.execute("drop database if exists \"" + database + "\" with (force)");
		} catch (SQLException exception) {
			System.err.println("Scratch database " + database + " could not be dropped: " + exception.getSQLState());
		}
	}

	private static String valueOrDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static PostgreSQLContainer<?> startContainer() {
		PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:17-alpine")
				.withDatabaseName("community_test")
				.withUsername("community_test")
				.withPassword("community_test");
		container.start();
		Runtime.getRuntime().addShutdownHook(new Thread(container::stop, "community-test-postgres-stop"));
		return container;
	}

	@DynamicPropertySource
	static void configurePostgres(DynamicPropertyRegistry registry) {
		if (SCRATCH_URL != null) {
			registry.add("spring.datasource.url", () -> SCRATCH_URL);
			return;
		}
		// "localhost" Windows'ta önce IPv6'ya (::1) çözülüp Docker port yönlendirmesinde takılabildiği için IPv4 kullanılır.
		registry.add("spring.datasource.url",
				() -> POSTGRES.getJdbcUrl().replace("//" + POSTGRES.getHost() + ":", "//127.0.0.1:") + "&connectTimeout=60");
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.hikari.connection-timeout", () -> "120000");
	}
}
