package com.sketchtrench.auth.security;

import com.sketchtrench.util.HashUtils;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Generates high-entropy one-time tokens (refresh, reset) and hashes them for storage. */
@Component
public class TokenService {

    public String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public String hash(String rawToken) {
        return HashUtils.sha256Hex(rawToken);
    }
}
