package com.example.tasks;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL v Dockeru pro integrační testy. Init skript založí "Liferay" tabulku
 * public.demo_task, aby Flyway V2 import běžel proti realistickým datům.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/** Kontejner sdílený v rámci jednoho Spring kontextu; @ServiceConnection nastaví datasource */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
				.withInitScript("liferay-legacy-schema.sql");
	}

}
