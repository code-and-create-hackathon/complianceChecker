package com.compliancechecker.account;

import com.compliancechecker.TestAccounts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login, roles, user management and the server-side policy, end to end with in-memory H2. All data is made up. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AccountIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private AuthService authService;

	@Test
	void workerLogsInWithUsernameOnly() throws Exception {
		userService.createIfAbsent("lena", Role.WORKER);

		String token = login("{\"username\":\" Lena \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("lena"))
				.andExpect(jsonPath("$.role").value("worker"))
				.andReturn().getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("lena"));
	}

	@Test
	void unknownUserCannotLogIn() throws Exception {
		login("{\"username\":\"nobody\"}")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("LOGIN_FAILED"));
	}

	@Test
	void adminNeedsTheAdminCode(CapturedOutput output) throws Exception {
		login("{\"username\":\"admin\"}").andExpect(status().isUnauthorized());
		login("{\"username\":\"admin\",\"adminCode\":\"wrong\"}")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Unknown user or wrong admin code."));
		login("{\"username\":\"admin\",\"adminCode\":\"" + TestAccounts.ADMIN_CODE + "\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("admin"));

		assertThat(output.getAll()).doesNotContain(TestAccounts.ADMIN_CODE);
	}

	@Test
	void logoutInvalidatesTheToken() throws Exception {
		String worker = TestAccounts.workerAuthorization(userService, authService, "jonas");

		mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, worker))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, worker))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void adminCreatesWorkersAndWorkersCannot() throws Exception {
		// Extra fields such as a role are ignored: this endpoint only ever creates workers.
		String admin = TestAccounts.adminAuthorization(authService);
		String worker = TestAccounts.workerAuthorization(userService, authService, "erika");

		perform(post("/api/users"), admin, "{\"username\":\"paul\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("paul"))
				.andExpect(jsonPath("$.role").value("worker"));
		perform(post("/api/users"), admin, "{\"username\":\"nina\",\"role\":\"admin\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("worker"));
		perform(post("/api/users"), admin, "{\"username\":\"paul\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("USERNAME_TAKEN"));
		perform(post("/api/users"), admin, "{\"username\":\"<b>x</b>\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.username").exists());

		perform(post("/api/users"), worker, "{\"username\":\"mallory\"}")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("FORBIDDEN"));
		mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, worker))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.username == 'paul')]").exists());
	}

	@Test
	void onlyAdminPublishesPolicyAndEveryoneCanReadIt() throws Exception {
		String admin = TestAccounts.adminAuthorization(authService);
		String worker = TestAccounts.workerAuthorization(userService, authService, "erika");

		perform(put("/api/policy"), worker, "{\"text\":\"# Anything goes\"}")
				.andExpect(status().isForbidden());

		int version = Integer.parseInt(perform(put("/api/policy"), admin, "{\"text\":\"# Rules\\n- R-01 No IBANs.\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.publishedBy").value("admin"))
				.andReturn().getResponse().getContentAsString().replaceAll(".*\"version\":(\\d+).*", "$1"));

		mockMvc.perform(get("/api/policy").header(HttpHeaders.AUTHORIZATION, worker))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(version))
				.andExpect(jsonPath("$.text").value("# Rules\n- R-01 No IBANs."));
		mockMvc.perform(get("/api/policy")).andExpect(status().isUnauthorized());
	}

	@Test
	void everyApiEndpointRequiresLoginExceptLogin() throws Exception {
		for (String path : new String[] {"/api/auth/me", "/api/policy", "/api/history", "/api/users"}) {
			mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/api/check").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"hi\"}"))
				.andExpect(status().isUnauthorized());
	}

	private ResultActions login(String body) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request, String authorization, String body)
			throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, authorization)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}
}
