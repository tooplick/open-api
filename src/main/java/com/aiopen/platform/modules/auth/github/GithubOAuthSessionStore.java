package com.aiopen.platform.modules.auth.github;

import com.aiopen.platform.modules.user.dto.LoginResponse;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GithubOAuthSessionStore {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration TICKET_TTL = Duration.ofMinutes(2);
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, StateEntry> states = new ConcurrentHashMap<>();
    private final Map<String, TicketEntry> tickets = new ConcurrentHashMap<>();

    public String createState(String redirect) {
        cleanup();
        String state = randomToken();
        states.put(state, new StateEntry(redirect, Instant.now().plus(STATE_TTL)));
        return state;
    }

    public String consumeState(String state) {
        cleanup();
        StateEntry entry = states.remove(state);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return entry.redirect();
    }

    public String createTicket(LoginResponse response) {
        cleanup();
        String ticket = randomToken();
        tickets.put(ticket, new TicketEntry(response, Instant.now().plus(TICKET_TTL)));
        return ticket;
    }

    public LoginResponse consumeTicket(String ticket) {
        cleanup();
        TicketEntry entry = tickets.remove(ticket);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return entry.response();
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void cleanup() {
        Instant now = Instant.now();
        states.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private record StateEntry(String redirect, Instant expiresAt) {
    }

    private record TicketEntry(LoginResponse response, Instant expiresAt) {
    }
}
