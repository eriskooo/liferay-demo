package com.example.tasks.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata OpenAPI dokumentu; UI na /swagger-ui.html, JSON na /v3/api-docs */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	/** Základní popis API */
	@Bean
	public OpenAPI tasksOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Tasks API")
				.version("v1")
				.description("Spring Boot náhrada Liferay Task portletu / Service Builder služby"));
	}

}
