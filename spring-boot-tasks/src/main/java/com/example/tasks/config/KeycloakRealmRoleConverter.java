package com.example.tasks.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Převede Keycloak claim realm_access.roles na ROLE_* authority.
 * Sem se "přestěhují" Liferay Regular/Site role po migraci uživatelů do Keycloaku.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

	private static final String REALM_ACCESS = "realm_access";

	private static final String ROLES = "roles";

	/** Vrátí authority z realm rolí; chybějící claim = žádné role */
	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
		if (realmAccess == null || !(realmAccess.get(ROLES) instanceof Collection<?> roles)) {
			return List.of();
		}
		return roles.stream()
				.map(String::valueOf)
				.map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
				.toList();
	}

}
