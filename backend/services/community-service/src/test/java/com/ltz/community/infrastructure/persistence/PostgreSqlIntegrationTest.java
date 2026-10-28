package com.ltz.community.infrastructure.persistence;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Persistence testlerini geliştirici ortamındaki parola ve veritabanı durumundan yalıtır.
 *
 * <p>Ortamda {@code POSTGRES_PASSWORD} tanımlıysa (pre-push ve Sonar taraması geçici bir PostgreSQL açıp
 * bağlantı bilgisini verir) o veritabanı kullanılır ve Testcontainers başlatılmaz; ikinci bir container
 * açmak yoğun makinelerde bağlantı zaman aşımlarına yol açıyordu. Ortam boşsa (IDE, doğrudan Maven)
 * Testcontainers ile geçici bir PostgreSQL 17 başlatılır.
 */
public abstract class PostgreSqlIntegrationTest {

	private static final boolean ENVIRONMENT_PROVIDES_DATABASE = System.getenv("POSTGRES_PASSWORD") != null;

	private static final PostgreSQLContainer<?> POSTGRES = ENVIRONMENT_PROVIDES_DATABASE ? null : startContainer();

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
		if (POSTGRES == null) {
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
