package com.example.tasks.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Kde se nahrazují Liferay role a oprávnění.
 *
 * Liferay: uživatelé/role/oprávnění v DB portálu (User_, Role_, ResourcePermission),
 * kontrola v kódu přes ModelResourcePermission.check(permissionChecker, task, ActionKeys.UPDATE),
 * identita v HTTP session (JSESSIONID) nebo Basic/OAuth2 u /o/* API.
 *
 * Spring Boot: identitu spravuje IdP (Keycloak), API je stateless OAuth2 resource server,
 * role přichází v JWT (realm_access.roles) a mapují se na GrantedAuthority.
 * Mapování v demu:  Liferay "VIEW" na Task  -> role tasks-viewer
 *                   Liferay "ADD_ENTRY/UPDATE/DELETE" -> role tasks-editor
 * Jemnější (per-záznam) oprávnění -> @PreAuthorize s vlastní logikou nebo ACL/ReBAC.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	public static final String ROLE_VIEWER = "tasks-viewer";

	public static final String ROLE_EDITOR = "tasks-editor";

	private static final String[] PUBLIC_PATHS = {
		"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health"
	};

	/** Produkční řetězec: JWT z Keycloaku + role per HTTP metoda */
	@Bean
	@ConditionalOnProperty(name = "tasks.security.enabled", havingValue = "true")
	public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
		JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
		jwtConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());

		return http
				.csrf(csrf -> csrf.disable()) // stateless API bez cookies -> CSRF nehrozí
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_PATHS).permitAll()
						.requestMatchers(HttpMethod.GET, "/api/tasks/**").hasAnyRole(ROLE_VIEWER, ROLE_EDITOR)
						.requestMatchers("/api/tasks/**").hasRole(ROLE_EDITOR)
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
				.build();
	}

	/** Lokální demo bez IdP: vše povoleno (NIKDY v produkci) */
	@Bean
	@ConditionalOnProperty(name = "tasks.security.enabled", havingValue = "false", matchIfMissing = true)
	public SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.build();
	}

}
