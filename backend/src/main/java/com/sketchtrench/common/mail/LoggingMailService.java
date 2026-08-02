package com.sketchtrench.common.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Dev/default mail sender: prints the action link to the log. Swap for a real SMTP
 * implementation in production — because callers depend on the {@link MailService}
 * interface, swapping never touches business logic.
 */
@Slf4j
@Component
public class LoggingMailService implements MailService {

    private static final String VERIFY_URL = "http://localhost:5173/verify-email?token=%s";
    private static final String RESET_URL = "http://localhost:5173/reset-password?token=%s";

    @Override
    public void sendPasswordReset(String email, String resetToken) {
        log.info("[mail] password reset for {} -> {}", email, RESET_URL.formatted(resetToken));
    }

    @Override
    public void sendEmailVerification(String email, String verificationToken) {
        log.info("[mail] email verification for {} -> {}", email, VERIFY_URL.formatted(verificationToken));
    }
}
