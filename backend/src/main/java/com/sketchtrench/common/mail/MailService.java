package com.sketchtrench.common.mail;

/**
 * Outbound transactional email. Defined as an interface so the password-reset and
 * email-verification flows don't care HOW the mail is delivered (console in dev, SMTP
 * in prod). The default bean logs the link — sufficient for local testing and CI.
 */
public interface MailService {

    void sendPasswordReset(String email, String resetToken);

    void sendEmailVerification(String email, String verificationToken);
}
