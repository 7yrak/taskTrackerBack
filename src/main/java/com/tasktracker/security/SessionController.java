package com.tasktracker.security;

import java.security.Principal;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {

    @GetMapping("/auth/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    /** Requiere sesión: sin ella el filtro responde 401 y el front muestra el login. */
    @GetMapping("/auth/session")
    Map<String, String> session(Principal principal) {
        return Map.of("username", principal.getName());
    }
}
