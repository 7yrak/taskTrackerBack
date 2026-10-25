package com.tasktracker.security;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("kova")
public class KovaSessionController {
    private final String instance;
    public KovaSessionController(@org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.client.registration.kova.client-id:tasktracker-pilot}") String instance){this.instance=instance;}
    @GetMapping("/auth/csrf")
    Map<String,String> csrf(CsrfToken token) {return Map.of("headerName",token.getHeaderName(),"token",token.getToken());}
    @GetMapping("/auth/info")
    Map<String,String> info() {return Map.of("provider","KOVA","instance",instance);}
    @GetMapping("/auth/session")
    Object session(HttpServletRequest request) {return request.getAttribute("kova.grant");}
}
