package com.tasktracker.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Freno a la fuerza bruta: como hay un único propietario, el contador es global.
 * Tras {@value #MAX_FAILURES} fallos seguidos el login responde 429 durante {@link #LOCK}.
 */
public class LoginThrottle extends OncePerRequestFilter {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK = Duration.ofMinutes(5);

    private final Clock clock;
    private int failures;
    private Instant lockedUntil = Instant.EPOCH;

    public LoginThrottle() {
        this(Clock.systemUTC());
    }

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    synchronized void failed() {
        if (++failures >= MAX_FAILURES) {
            lockedUntil = clock.instant().plus(LOCK);
            failures = 0;
        }
    }

    synchronized void reset() {
        failures = 0;
        lockedUntil = Instant.EPOCH;
    }

    synchronized boolean locked() {
        return secondsLocked() > 0;
    }

    private synchronized long secondsLocked() {
        Instant now = clock.instant();
        return now.isBefore(lockedUntil) ? Duration.between(now, lockedUntil).toSeconds() + 1 : 0;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !("POST".equals(request.getMethod()) && path.equals("/auth/login"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long wait = secondsLocked();
        if (wait > 0) {
            response.setHeader("Retry-After", String.valueOf(wait));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        chain.doFilter(request, response);
    }
}
