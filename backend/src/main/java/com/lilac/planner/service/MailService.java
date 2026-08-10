package com.lilac.planner.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends the forgot-password reset link. When no SMTP host is configured (the default for local dev
 * and tests) the link can be logged instead of sent, so the flow is exercisable without a mail
 * server - but only when {@code planner.mail.log-reset-links} is explicitly enabled (dev only):
 * the link contains the raw reset token, and logging it would let anyone with log access take
 * over the account.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.from:no-reply@lilac-planner.local}")
    private String from;

    @Value("${planner.mail.log-reset-links:false}")
    private boolean logResetLinks;

    public MailService(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordReset(String toEmail, String resetLink) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (mailHost == null || mailHost.isBlank() || sender == null) {
            logResetLink("[mail disabled]", toEmail, resetLink);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Reset your Lilac Planner password");
        message.setText("Reset your password using this link (valid for a limited time):\n\n"
                + resetLink + "\n\nIf you didn't request this, you can ignore this email.");
        try {
            sender.send(message);
            log.info("Sent password-reset email to {}", toEmail);
        } catch (Exception e) {
            // A misconfigured / unreachable SMTP host must not surface to the caller:
            // it would 500 the forgot-password request and could leak that the email
            // exists. Log and move on.
            log.warn("Could not send password-reset email to {} ({}): {}", toEmail,
                    e.getClass().getSimpleName(), e.getMessage());
            logResetLink("[mail send failed]", toEmail, resetLink);
        }
    }

    /**
     * The reset link carries the raw, usable reset token (only its hash is stored in the DB), so
     * logging it would let anyone with log access take over the account. The full link is logged
     * only when explicitly opted in via {@code planner.mail.log-reset-links} (dev only).
     */
    private void logResetLink(String prefix, String toEmail, String resetLink) {
        if (logResetLinks) {
            log.info("{} password-reset link for {}: {}", prefix, toEmail, resetLink);
        } else {
            log.info("{} password-reset link generated for {} - not logged. Set MAIL_HOST to a"
                    + " working SMTP host to send it, or PLANNER_MAIL_LOG_RESET_LINKS=true"
                    + " (dev only) to log the link.", prefix, toEmail);
        }
    }
}
