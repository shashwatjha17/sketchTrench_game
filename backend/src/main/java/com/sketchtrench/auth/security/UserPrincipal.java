package com.sketchtrench.auth.security;

import java.security.Principal;

/**
 * The authenticated identity carried in Spring's SecurityContext AND in the WebSocket
 * {@code Principal}. A record avoids a full Spring UserDetails object where a plain
 * identity is enough. Using username as {@code getName()} keeps STOMP destinations
 * ({@code /user/{name}/...}) natural.
 */
public record UserPrincipal(Long id, String username) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
