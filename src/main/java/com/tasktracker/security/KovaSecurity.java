package com.tasktracker.security;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.*;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@Profile("kova")
public class KovaSecurity {
    @Bean
    SecurityFilterChain kovaChain(HttpSecurity http,OAuth2AuthorizedClientService clients,
            @Value("${tasktracker.kova.check-uri}") String checkUri,
            @Value("${tasktracker.kova.ui-uri}") String ui) throws Exception {
        if(!ui.startsWith("https://") && !ui.startsWith("http://127.0.0.1:"))
            throw new IllegalArgumentException("Invalid public UI URI");
        http.authorizeHttpRequests(a->a
            .requestMatchers("/","/index.html","/*.js","/*.css","/*.ico","/*.svg","/assets/**",
                "/dashboard","/projects","/tasks","/incidents","/overtime","/team","/reminders","/acceso",
                "/auth/csrf","/auth/info","/oauth2/authorization/kova","/login/oauth2/code/kova").permitAll()
            .anyRequest().authenticated());
        http.csrf(c->c.csrfTokenRepository(new HttpSessionCsrfTokenRepository())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        http.formLogin(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.requestCache(c->c.requestCache(new NullRequestCache()));
        http.oauth2Login(o->o
            .successHandler((r,s,a)->s.sendRedirect(ui))
            .failureHandler((r,s,e)->s.sendRedirect(ui+"acceso?estado=denegado")));
        http.logout(l->l.logoutUrl("/auth/logout").logoutSuccessHandler((r,s,a)->s.setStatus(204))
            .addLogoutHandler((r,s,a)->{if(a instanceof OAuth2AuthenticationToken t)
                clients.removeAuthorizedClient(t.getAuthorizedClientRegistrationId(),t.getName());}));
        http.exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->s.setStatus(401)));
        http.addFilterBefore(new GrantFilter(clients,checkUri),AuthorizationFilter.class);
        return http.build();
    }

    static final class GrantFilter extends OncePerRequestFilter {
        private final OAuth2AuthorizedClientService clients;
        private final RestClient check;
        GrantFilter(OAuth2AuthorizedClientService clients,String uri) {
            this.clients=clients;
            var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(2)); factory.setReadTimeout(Duration.ofSeconds(3));
            this.check=RestClient.builder().requestFactory(factory).baseUrl(uri).build();
        }
        @Override protected boolean shouldNotFilter(HttpServletRequest request) {
            String path=request.getRequestURI().substring(request.getContextPath().length());
            return !(path.startsWith("/api/") || path.equals("/auth/session"));
        }
        @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)
                throws ServletException,IOException {
            var auth=SecurityContextHolder.getContext().getAuthentication();
            if(!(auth instanceof OAuth2AuthenticationToken token)) {s.setStatus(401);return;}
            OAuth2AuthorizedClient client=clients.loadAuthorizedClient(token.getAuthorizedClientRegistrationId(),token.getName());
            if(client==null || client.getAccessToken().getExpiresAt()==null
                    || !client.getAccessToken().getExpiresAt().isAfter(java.time.Instant.now())) {
                clear(r,token);s.setStatus(401);return;
            }
            try {
                var response=check.get().headers(h->h.setBearerAuth(client.getAccessToken().getTokenValue()))
                    .retrieve().toEntity(Map.class);
                Map<?,?> grant=response.getBody();
                if(grant==null || !"tasktracker-pilot".equals(grant.get("instanceId"))
                    || !"ACTIVE".equals(grant.get("status"))
                    || !Objects.equals(token.getPrincipal().getAttribute("organization_id"),grant.get("organizationId"))) {
                    clear(r,token);s.setStatus(403);return;
                }
                String role=String.valueOf(grant.get("role"));
                if(!List.of("OWNER","ADMIN","MEMBER","VIEWER").contains(role)) {s.setStatus(403);return;}
                if(!Set.of("GET","HEAD","OPTIONS").contains(r.getMethod()) && role.equals("VIEWER")) {
                    s.setStatus(403);return;
                }
                r.setAttribute("kova.grant",grant);
            } catch(org.springframework.web.client.HttpClientErrorException e) {
                clear(r,token);s.setStatus(403);return;
            } catch(org.springframework.web.client.RestClientException e) {
                // No cached allow decision: KOVA unavailable means private requests remain closed.
                s.setStatus(503);return;
            }
            chain.doFilter(r,s);
        }
        private void clear(HttpServletRequest r,OAuth2AuthenticationToken token) {
            clients.removeAuthorizedClient(token.getAuthorizedClientRegistrationId(),token.getName());
            var session=r.getSession(false);if(session!=null)session.invalidate();
            SecurityContextHolder.clearContext();
        }
    }
}
