package com.tasktracker.security;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/** Existing private installation is unchanged. The isolated KOVA instance always uses profile kova. */
@Configuration
@Profile("!kova")
public class LegacySecurity {
    @Bean SecurityFilterChain existingPrivateInstallation(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a->a.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable).build();
    }
}
