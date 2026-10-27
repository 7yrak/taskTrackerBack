package com.tasktracker.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Base de las pruebas de API: perfil de prueba con un propietario y su hash BCrypt generado al vuelo. */
@ActiveProfiles("test")
public abstract class AuthTestSupport {

    public static final String PASSWORD = "clave-de-prueba";

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        registry.add("tasktracker.auth.password-hash", () -> new BCryptPasswordEncoder().encode(PASSWORD));
    }

    protected RequestPostProcessor owner() {
        return SecurityMockMvcRequestPostProcessors.user("owner").roles("OWNER");
    }
}
