package com.tasktracker.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:local-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
class LocalSecurityTest extends AuthTestSupport {

    @Autowired MockMvc mvc;
    @Autowired LoginThrottle throttle;

    @BeforeEach
    void clearThrottle() {
        throttle.reset();
    }

    private MockHttpServletRequestBuilder login(String password) {
        return post("/auth/login").with(csrf()).param("username", "owner").param("password", password);
    }

    @Test
    void anonymousCannotReadAnyBusinessApi() throws Exception {
        for (String path : List.of("/api/projects", "/api/tasks", "/api/incidents", "/api/overtime", "/api/team",
                "/api/reminders", "/api/stats", "/auth/session")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void writesRequireCsrfEvenWhenSignedIn() throws Exception {
        mvc.perform(post("/api/projects").with(owner()).contentType("application/json").content("{\"name\":\"Sin CSRF\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void loginRequiresCsrf() throws Exception {
        mvc.perform(post("/auth/login").param("username", "owner").param("password", PASSWORD))
            .andExpect(status().isForbidden());
    }

    @Test
    void validCredentialsOpenASessionAndWrongOnesDoNot() throws Exception {
        mvc.perform(login("otra-clave")).andExpect(status().isUnauthorized());
        var session = mvc.perform(login(PASSWORD)).andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
        mvc.perform(get("/auth/session").session((org.springframework.mock.web.MockHttpSession) session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("owner"));
    }

    @Test
    void signedInOwnerCanReadAndLogOut() throws Exception {
        mvc.perform(get("/api/projects").with(owner())).andExpect(status().isOk());
        mvc.perform(post("/auth/logout").with(owner()).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void csrfEndpointIsPublic() throws Exception {
        mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.headerName").exists());
    }

    @Test
    void repeatedFailuresLockLoginEvenForTheRightPassword() throws Exception {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            mvc.perform(login("mala-" + i)).andExpect(status().isUnauthorized());
        }
        mvc.perform(login(PASSWORD)).andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    void lockExpiresAfterTheWaitingPeriod() {
        var now = new Instant[] {Instant.parse("2026-01-01T00:00:00Z")};
        var clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now[0]; }
        };
        var throttle = new LoginThrottle(clock);
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) throttle.failed();
        org.junit.jupiter.api.Assertions.assertTrue(throttle.locked());
        now[0] = now[0].plus(LoginThrottle.LOCK).plusSeconds(1);
        org.junit.jupiter.api.Assertions.assertFalse(throttle.locked());
    }
}
