package com.compliancechecker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The browser extension (chrome-extension://...) may call the API; other websites may not. Mock mode, no AWS. */
@SpringBootTest
@AutoConfigureMockMvc
class CorsTest {

	private static final String EXTENSION = "chrome-extension://abcdefghijklmnopabcdefghijklmnop";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void extensionPreflightIsAllowed() throws Exception {
		mockMvc.perform(options("/api/check")
						.header(HttpHeaders.ORIGIN, EXTENSION)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, EXTENSION));
	}

	@Test
	void extensionCanRunACheck() throws Exception {
		mockMvc.perform(post("/api/check")
						.header(HttpHeaders.ORIGIN, EXTENSION)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"prompt\":\"Reply to erika@example.com\",\"compliance\":\"# Rules\"}"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, EXTENSION))
				.andExpect(jsonPath("$.isSafe").value(false))
				.andExpect(jsonPath("$.problems[0].ruleId").value("R-01"));
	}

	@Test
	void otherWebsitesAreRejected() throws Exception {
		mockMvc.perform(options("/api/check")
						.header(HttpHeaders.ORIGIN, "https://evil.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}
}
