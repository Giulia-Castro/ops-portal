package io.github.giuliacastro.opsportal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The real browser flow: CSRF cookie, session login, logout. Kept in its own Spring context
 * because spring-security-test's csrf() post-processor (used by other tests) replaces the
 * CSRF token repository of the shared context.
 */
@SpringBootTest(properties = "portal.recent-days=29")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginFlowTest {

    static final String CREDENTIALS = "{\"username\":\"operator\",\"password\":\"operator-test-password\"}";

    @Autowired
    MockMvc mvc;

    @Test
    void csrfCookieSessionLoginAndLogout() throws Exception {
        var csrfCall = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = csrfCall.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isFalse();
        String headerName = JsonPath.read(csrfCall.getResponse().getContentAsString(), "$.headerName");
        assertThat(headerName).isEqualTo("X-XSRF-TOKEN");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(CREDENTIALS))
                .andExpect(status().isForbidden());

        var login = mvc.perform(post("/api/auth/login")
                        .cookie(cookie)
                        .header(headerName, cookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREDENTIALS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("operator"))
                .andExpect(jsonPath("$.roles[0]").value("OPERATOR"))
                .andExpect(jsonPath("$.roles[1]").value("VIEWER"))
                .andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("operator"));

        mvc.perform(post("/api/auth/logout").session(session).cookie(cookie).header(headerName, cookie.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }
}
