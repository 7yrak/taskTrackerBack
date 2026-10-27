package com.tasktracker.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Acceso de un único propietario: usuario y hash BCrypt vienen de la configuración
 * (variables de entorno), nunca del repositorio. La API privada exige sesión y CSRF.
 */
@Configuration
public class LocalSecurity {

    private static final String BCRYPT = "\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService owner(@Value("${tasktracker.auth.username:}") String username,
                             @Value("${tasktracker.auth.password-hash:}") String passwordHash) {
        if (username.isBlank() || !passwordHash.matches(BCRYPT)) {
            throw new IllegalStateException(
                "Configura TASKTRACKER_USER y TASKTRACKER_PASSWORD_HASH (hash BCrypt) para iniciar TaskTracker.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username).password(passwordHash).roles("OWNER").build());
    }

    @Bean
    LoginThrottle loginThrottle() {
        return new LoginThrottle();
    }

    @Bean
    SecurityFilterChain securityChain(HttpSecurity http, LoginThrottle throttle) throws Exception {
        http.authorizeHttpRequests(a -> a
            .requestMatchers("/", "/index.html", "/*.js", "/*.css", "/*.ico", "/*.svg", "/assets/**",
                "/dashboard", "/projects", "/tasks", "/incidents", "/overtime", "/team", "/reminders", "/acceso",
                "/auth/csrf", "/auth/login").permitAll()
            .anyRequest().authenticated());
        http.csrf(c -> c.csrfTokenRepository(new HttpSessionCsrfTokenRepository())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.requestCache(c -> c.requestCache(new NullRequestCache()));
        http.formLogin(f -> f
            .loginPage("/acceso")
            .loginProcessingUrl("/auth/login")
            .successHandler((request, response, auth) -> {
                throttle.reset();
                response.setStatus(HttpStatus.NO_CONTENT.value());
            })
            .failureHandler((request, response, error) -> {
                throttle.failed();
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
            }));
        http.logout(l -> l.logoutUrl("/auth/logout")
            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        http.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        http.addFilterBefore(throttle, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
