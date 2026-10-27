package com.tasktracker.security;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.*;
import org.springframework.security.oauth2.core.oidc.user.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
public abstract class KovaHttpTestSupport {
 protected static final String ORG="11111111-1111-1111-1111-111111111111";
 private static final HttpServer SERVER;
 static {
  try {
   SERVER=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   SERVER.createContext("/check",e->{
    byte[] body=("{\"instanceId\":\"tasktracker-personal\",\"organizationId\":\""+ORG+"\",\"role\":\"ADMIN\",\"status\":\"ACTIVE\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);e.close();
   });SERVER.start();
  } catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}
 }
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("tasktracker.kova.check-uri",()->"http://127.0.0.1:"+SERVER.getAddress().getPort()+"/check");}
 @Autowired OAuth2AuthorizedClientService clients;
 @Autowired ClientRegistrationRepository registrations;
 protected org.springframework.test.web.servlet.request.RequestPostProcessor kovaAdmin(){
  var id=new OidcIdToken("test-id",Instant.now(),Instant.now().plusSeconds(120),Map.of("sub","test-admin","organization_id",ORG,"instance_id","tasktracker-personal"));
  var principal=new DefaultOidcUser(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),id);
  var auth=new OAuth2AuthenticationToken(principal,principal.getAuthorities(),"kova");
  clients.saveAuthorizedClient(new OAuth2AuthorizedClient(registrations.findByRegistrationId("kova"),"test-admin",new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"test-only",Instant.now(),Instant.now().plusSeconds(120))),auth);
  return oidcLogin().idToken(t->t.subject("test-admin").claim("organization_id",ORG).claim("instance_id","tasktracker-personal")).clientRegistration(registrations.findByRegistrationId("kova"));
 }
}
