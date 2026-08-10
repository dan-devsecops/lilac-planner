package com.lilac.planner.unit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lilac.planner.service.MailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("MailService - reset links must not leak into logs by default")
class MailServiceUnitTest {

    private static final String LINK = "http://localhost:5173/reset-password?token=raw-secret-token";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger mailLogger = (Logger) LoggerFactory.getLogger(MailService.class);

    @BeforeEach
    void captureLogs() {
        appender.start();
        mailLogger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        mailLogger.detachAppender(appender);
        appender.stop();
    }

    @SuppressWarnings("unchecked")
    private MailService service(JavaMailSender sender, String host, boolean logResetLinks) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        MailService service = new MailService(provider);
        ReflectionTestUtils.setField(service, "mailHost", host);
        ReflectionTestUtils.setField(service, "from", "no-reply@test.local");
        ReflectionTestUtils.setField(service, "logResetLinks", logResetLinks);
        return service;
    }

    private String allLogOutput() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    @DisplayName("mail disabled + flag off (default) → link/token is NOT logged, hint is")
    void mailDisabled_defaultDoesNotLogLink() {
        service(null, "", false).sendPasswordReset("alice@x.com", LINK);

        String logged = allLogOutput();
        assertThat(logged)
                .doesNotContain(LINK)
                .doesNotContain("raw-secret-token")
                .contains("alice@x.com")
                .contains("PLANNER_MAIL_LOG_RESET_LINKS");
    }

    @Test
    @DisplayName("mail disabled + flag on (dev) → full link is logged")
    void mailDisabled_optInLogsLink() {
        service(null, "", true).sendPasswordReset("alice@x.com", LINK);

        assertThat(allLogOutput()).contains(LINK);
    }

    @Test
    @DisplayName("send failure + flag off → link/token is NOT logged")
    void sendFailure_defaultDoesNotLogLink() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("boom")).when(sender).send(any(SimpleMailMessage.class));

        service(sender, "smtp.example.com", false).sendPasswordReset("alice@x.com", LINK);

        assertThat(allLogOutput()).doesNotContain("raw-secret-token");
    }

    @Test
    @DisplayName("send failure + flag on (dev) → full link is logged")
    void sendFailure_optInLogsLink() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("boom")).when(sender).send(any(SimpleMailMessage.class));

        service(sender, "smtp.example.com", true).sendPasswordReset("alice@x.com", LINK);

        assertThat(allLogOutput()).contains(LINK);
    }

    @Test
    @DisplayName("SMTP configured → email is sent, raw token never logged regardless of flag")
    void smtpConfigured_sendsEmail() {
        JavaMailSender sender = mock(JavaMailSender.class);

        service(sender, "smtp.example.com", false).sendPasswordReset("alice@x.com", LINK);

        verify(sender).send(any(SimpleMailMessage.class));
        assertThat(allLogOutput()).doesNotContain("raw-secret-token");
    }
}
