package com.tasktracker.security;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
/** Unknown deployment profiles must never restore anonymous access. */
@Configuration @Profile("!kova & !kova-personal")
public class LegacySecurity {
    @Bean SecurityFilterChain closedInstallation(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.anyRequest().denyAll()).build();
    }
}
