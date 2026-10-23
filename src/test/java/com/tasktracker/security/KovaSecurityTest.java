package com.tasktracker.security;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:kova-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
 "spring.jpa.show-sql=false","tasktracker.kova.ui-uri=http://127.0.0.1:8082/",
 "spring.security.oauth2.client.registration.kova.redirect-uri=http://127.0.0.1:8082/login/oauth2/code/kova",
 "spring.security.oauth2.client.provider.kova.authorization-uri=http://127.0.0.1:18090/oauth2/authorize",
 "spring.security.oauth2.client.provider.kova.token-uri=http://127.0.0.1:18090/oauth2/token",
 "spring.security.oauth2.client.provider.kova.jwk-set-uri=http://127.0.0.1:18090/oauth2/jwks",
 "spring.security.oauth2.client.provider.kova.issuer-uri=http://127.0.0.1:18090"})
@AutoConfigureMockMvc @ActiveProfiles("kova")
class KovaSecurityTest {
 static HttpServer server;
 static volatile int code=200;
 static volatile String role="OWNER";
 static final String ORG="11111111-1111-1111-1111-111111111111";
 @DynamicPropertySource static void properties(DynamicPropertyRegistry props) throws Exception {
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/check",exchange->{
   String body="{\"instanceId\":\"tasktracker-pilot\",\"organizationId\":\""+ORG+
     "\",\"organizationName\":\"Pilot\",\"role\":\""+role+"\",\"status\":\"ACTIVE\"}";
   exchange.getResponseHeaders().set("Content-Type","application/json");
   exchange.sendResponseHeaders(code,body.getBytes().length);
   exchange.getResponseBody().write(body.getBytes()); exchange.close();
  });server.start();
  props.add("tasktracker.kova.check-uri",()->"http://127.0.0.1:"+server.getAddress().getPort()+"/check");
 }
 @Autowired MockMvc mvc;
 @Autowired OAuth2AuthorizedClientService clients;
 @Autowired ClientRegistrationRepository registrations;
 @BeforeEach void reset() {code=200;role="OWNER";}
 @AfterAll static void stop() {if(server!=null)server.stop(0);}
 private org.springframework.test.web.servlet.request.RequestPostProcessor signedIn(String org) {
  var login=oidcLogin().idToken(t->t.subject("pilot-user").claim("organization_id",org));
  var access=new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"test-only-access",
      Instant.now(),Instant.now().plusSeconds(120));
  var principal=new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(
      List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
      new org.springframework.security.oauth2.core.oidc.OidcIdToken("test-id-token",Instant.now(),
      Instant.now().plusSeconds(120),Map.of("sub","pilot-user","organization_id",org)));
  var auth=new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(principal,
      principal.getAuthorities(),"kova");
  clients.saveAuthorizedClient(new OAuth2AuthorizedClient(registrations.findByRegistrationId("kova"),
      "pilot-user",access),auth);
  return login.clientRegistration(registrations.findByRegistrationId("kova"));
 }
 @Test void anonymousCannotReadAnyBusinessApi() throws Exception {
  for(String path:List.of("/api/projects","/api/tasks","/api/incidents","/api/overtime","/api/team","/api/reminders","/api/stats"))
   mvc.perform(get(path)).andExpect(status().isUnauthorized());
 }
 @Test void requiresCsrfBeforeAnyWrite() throws Exception {
  mvc.perform(post("/api/projects").with(signedIn(ORG)).contentType("application/json").content("{\"name\":\"No CSRF\"}"))
   .andExpect(status().isForbidden());
 }
 @Test void validGrantCanReadButForeignOrganizationCannot() throws Exception {
  mvc.perform(get("/auth/session").with(signedIn(ORG))).andExpect(status().isOk())
    .andExpect(jsonPath("$.organizationId").value(ORG));
  mvc.perform(get("/auth/session").with(signedIn(UUID.randomUUID().toString()))).andExpect(status().isForbidden());
 }
 @Test void revocationAndOutageNeverAllowCachedAccess() throws Exception {
  mvc.perform(get("/api/projects").with(signedIn(ORG))).andExpect(status().isOk());
  code=403; mvc.perform(get("/api/projects").with(signedIn(ORG))).andExpect(status().isForbidden());
  code=503; mvc.perform(get("/api/projects").with(signedIn(ORG))).andExpect(status().isServiceUnavailable());
 }
 @Test void viewerCannotWriteEvenWithCsrf() throws Exception {
  role="VIEWER";
  mvc.perform(get("/api/projects").with(signedIn(ORG))).andExpect(status().isOk());
  mvc.perform(post("/api/projects").with(signedIn(ORG)).with(csrf())
    .contentType("application/json").content("{\"name\":\"Blocked\"}")).andExpect(status().isForbidden());
 }
 @Test void loginUsesPkce() throws Exception {
  mvc.perform(get("/oauth2/authorization/kova")).andExpect(status().is3xxRedirection())
    .andExpect(header().string("Location",org.hamcrest.Matchers.containsString("code_challenge=")))
    .andExpect(header().string("Location",org.hamcrest.Matchers.containsString("code_challenge_method=S256")));
 }
}
