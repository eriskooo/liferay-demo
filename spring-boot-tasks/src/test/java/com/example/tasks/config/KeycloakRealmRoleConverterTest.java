package com.example.tasks.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

	private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

	@Test
	@DisplayName("Převede realm role na ROLE_ authority")
	void should_mapRealmRoles_whenClaimPresent() {
		Jwt jwt = jwtWith(Map.of("realm_access", Map.of("roles", List.of("tasks-viewer", "tasks-editor"))));

		assertThat(converter.convert(jwt))
				.extracting(GrantedAuthority::getAuthority)
				.containsExactly("ROLE_tasks-viewer", "ROLE_tasks-editor");
	}

	@Test
	@DisplayName("Vrátí prázdný seznam, když claim realm_access chybí")
	void should_returnEmpty_whenClaimMissing() {
		assertThat(converter.convert(jwtWith(Map.of("sub", "user")))).isEmpty();
	}

	@Test
	@DisplayName("Vrátí prázdný seznam, když roles není kolekce")
	void should_returnEmpty_whenRolesIsNotCollection() {
		assertThat(converter.convert(jwtWith(Map.of("realm_access", Map.of("roles", "admin"))))).isEmpty();
	}

	@Test
	@DisplayName("Vrátí prázdný seznam pro prázdné role")
	void should_returnEmpty_whenRolesEmpty() {
		assertThat(converter.convert(jwtWith(Map.of("realm_access", Map.of("roles", List.of()))))).isEmpty();
	}

	private static Jwt jwtWith(Map<String, Object> claims) {
		return Jwt.withTokenValue("token").header("alg", "none").claims(c -> c.putAll(claims)).build();
	}

}
