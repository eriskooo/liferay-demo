package com.example.tasks.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tasks.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Režim tasks.security.enabled=true. JwtDecoder je mock (žádný Keycloak),
 * token simuluje SecurityMockMvc jwt() s danými rolemi.
 */
@SpringBootTest(properties = "tasks.security.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityIntegrationTest {

	private static final String BODY = "{\"title\":\"Secured\"}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	@DisplayName("Bez tokenu vrátí 401")
	void should_return401_whenNoToken() throws Exception {
		mockMvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Viewer smí číst")
	void should_allowRead_whenViewer() throws Exception {
		mockMvc.perform(get("/api/tasks").with(jwt().authorities(role(SecurityConfig.ROLE_VIEWER))))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Viewer nesmí zapisovat - 403")
	void should_return403_whenViewerWrites() throws Exception {
		mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(BODY)
						.with(jwt().authorities(role(SecurityConfig.ROLE_VIEWER))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Editor smí vytvořit úkol")
	void should_allowCreate_whenEditor() throws Exception {
		mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(BODY)
						.with(jwt().authorities(role(SecurityConfig.ROLE_EDITOR))))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("Token bez rolí nesmí číst - 403")
	void should_return403_whenNoRoles() throws Exception {
		mockMvc.perform(get("/api/tasks").with(jwt())).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Swagger dokumentace je veřejná")
	void should_allowApiDocs_whenAnonymous() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	private static SimpleGrantedAuthority role(String role) {
		return new SimpleGrantedAuthority("ROLE_" + role);
	}

}
