package manyfaces.mailer.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.testing.StreamRecorder;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import manyfaces.mailer.config.MailerConfig;
import manyfaces.mailer.smtp.SmtpMailSender;
import manyfaces.mailer.template.TemplateCatalog;
import manyfaces.mailer.template.TemplateRenderService;
import manyfaces.mailer.v1.SendTemplatedEmailRequest;
import manyfaces.mailer.v1.SendTemplatedEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailerServiceImplTest {

    private static final TemplateRenderService.RenderedEmail SAMPLE =
            new TemplateRenderService.RenderedEmail("Subject", "<p>h</p>", "h");

    @Mock
    private TemplateRenderService templateRenderService;

    @Mock
    private SmtpMailSender smtpMailSender;

    private MailerServiceImpl impl;

    @BeforeEach
    void setUp() throws Exception {
        impl = new MailerServiceImpl(MailerConfig.testingMinimal(Locale.forLanguageTag("en")), templateRenderService, smtpMailSender);
        lenient()
                .when(templateRenderService.render(anyString(), anyString(), any()))
                .thenReturn(SAMPLE);
        lenient()
                .doAnswer(invocation -> "smtp-msg-id")
                .when(smtpMailSender)
                .send(anyList(), anyList(), anyList(), anyString(), anyString(), anyString(), isNull());
    }

    @Test
    void sendTemplatedEmail_success_returnsCorrelationAndSmtpId() throws Exception {
        var req = validConfirmRequest("en").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rec.getError()).isNull();
        assertThat(rec.getValues()).hasSize(1);
        assertThat(rec.getValues().get(0).getCorrelationId()).isNotBlank();
        assertThat(rec.getValues().get(0).getSmtpMessageId()).isEqualTo("smtp-msg-id");
        verify(smtpMailSender).send(anyList(), anyList(), anyList(), eq("Subject"), eq("h"), eq("<p>h</p>"), isNull());
    }

    @Test
    void sendTemplatedEmail_trimsTemplateId() throws Exception {
        var req = validConfirmRequest("en").setTemplateId("  " + TemplateCatalog.IDENTITY_EMAIL_CONFIRM + "  ").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rec.getError()).isNull();
        verify(templateRenderService).render(eq(TemplateCatalog.IDENTITY_EMAIL_CONFIRM), eq("en"), any());
    }

    @Test
    void sendTemplatedEmail_blankLocale_fallsBackToDefault() throws Exception {
        var req = validConfirmRequest("").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        verify(templateRenderService).render(eq(TemplateCatalog.IDENTITY_EMAIL_CONFIRM), eq("en"), any());
    }

    @Test
    void sendTemplatedEmail_noTo_failsInvalidArgument() throws Exception {
        var req = SendTemplatedEmailRequest.newBuilder()
                .setTemplateId(TemplateCatalog.IDENTITY_EMAIL_CONFIRM)
                .setLocale("en")
                .putParams("action_link", "https://a")
                .putParams("user_name", "u")
                .build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(smtpMailSender, never()).send(anyList(), anyList(), anyList(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void sendTemplatedEmail_blankToRecipient_failsInvalidArgumentBeforeSmtp() throws Exception {
        var req = validConfirmRequest("en").clearTo().addTo("   ").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(Status.fromThrowable(rec.getError()).getDescription()).contains("Blank recipient");
        verify(smtpMailSender, never()).send(anyList(), anyList(), anyList(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void sendTemplatedEmail_blankCcRecipient_failsInvalidArgumentBeforeSmtp() throws Exception {
        var req = validConfirmRequest("en").addCc("\t").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(Status.fromThrowable(rec.getError()).getDescription()).contains("'cc'");
        verify(smtpMailSender, never()).send(anyList(), anyList(), anyList(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void sendTemplatedEmail_blankBccRecipient_failsInvalidArgumentBeforeSmtp() throws Exception {
        var req = validConfirmRequest("en").addBcc("").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(Status.fromThrowable(rec.getError()).getDescription()).contains("'bcc'");
        verify(smtpMailSender, never()).send(anyList(), anyList(), anyList(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void sendTemplatedEmail_unknownTemplate_failsInvalidArgument() throws Exception {
        var req = validConfirmRequest("en").setTemplateId("unknown_tpl").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(smtpMailSender, never()).send(anyList(), anyList(), anyList(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void sendTemplatedEmail_missingRequiredParam_failsInvalidArgument() throws Exception {
        var req = SendTemplatedEmailRequest.newBuilder()
                .addTo("a@b.com")
                .setTemplateId(TemplateCatalog.IDENTITY_EMAIL_CONFIRM)
                .setLocale("en")
                .putParams("action_link", "https://x")
                .build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(templateRenderService, never()).render(anyString(), anyString(), any());
    }

    @Test
    void sendTemplatedEmail_paramKeyTooLong_failsInvalidArgument() throws Exception {
        String longKey = "k".repeat(129);
        var req = validConfirmRequest("en").putParams(longKey, "v").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void sendTemplatedEmail_paramValueTooLong_failsInvalidArgument() throws Exception {
        String longVal = "v".repeat(8193);
        var req = validConfirmRequest("en").putParams("action_link", longVal).build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void sendTemplatedEmail_paramContainsNewline_failsInvalidArgument() throws Exception {
        var req = validConfirmRequest("en").putParams("user_name", "bad\nname").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void sendTemplatedEmail_paramsTotalSizeExceedsCap_failsInvalidArgument() throws Exception {
        String chunk = "x".repeat(8192);
        var b = validConfirmRequest("en");
        for (int i = 0; i < 40; i++) {
            b.putParams("p" + i, chunk);
        }
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(b.build(), rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void sendTemplatedEmail_tooManyUniqueRecipients_failsInvalidArgument() throws Exception {
        var b = SendTemplatedEmailRequest.newBuilder()
                .setTemplateId(TemplateCatalog.IDENTITY_EMAIL_CONFIRM)
                .setLocale("en")
                .putParams("action_link", "https://x")
                .putParams("user_name", "u");
        IntStream.range(0, 51).forEach(i -> b.addTo("u" + i + "@x.com"));
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(b.build(), rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void sendTemplatedEmail_renderFailure_failsInvalidArgument() throws Exception {
        when(templateRenderService.render(anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("bad template"));
        var req = validConfirmRequest("en").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void sendTemplatedEmail_smtpAuth_failsFailedPrecondition() throws Exception {
        doThrow(new AuthenticationFailedException("auth"))
                .when(smtpMailSender)
                .send(anyList(), anyList(), anyList(), anyString(), anyString(), anyString(), isNull());
        var req = validConfirmRequest("en").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void sendTemplatedEmail_smtpMessaging_failsUnavailable() throws Exception {
        doThrow(new MessagingException("conn reset") {})
                .when(smtpMailSender)
                .send(anyList(), anyList(), anyList(), anyString(), anyString(), anyString(), isNull());
        var req = validConfirmRequest("en").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    void sendTemplatedEmail_smtpUnexpectedRuntime_failsInternal() throws Exception {
        doThrow(new RuntimeException("boom"))
                .when(smtpMailSender)
                .send(anyList(), anyList(), anyList(), anyString(), anyString(), anyString(), isNull());
        var req = validConfirmRequest("en").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INTERNAL);
    }

    @Test
    void sendTemplatedEmail_replyToForwardedToSmtp() throws Exception {
        var req = validConfirmRequest("en").setReplyTo("reply@example.com").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rec.getError()).isNull();
        verify(smtpMailSender)
                .send(anyList(), anyList(), anyList(), anyString(), anyString(), anyString(), eq("reply@example.com"));
    }

    @Test
    void sendTemplatedEmail_blankRequiredParam_failsInvalidArgument() throws Exception {
        var req = validConfirmRequest("en").putParams("user_name", "   ").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void sendTemplatedEmail_blankTemplateId_failsInvalidArgument() throws Exception {
        var req = validConfirmRequest("en").setTemplateId("   ").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void sendTemplatedEmail_paramContainsCarriageReturn_failsInvalidArgument() throws Exception {
        var req = validConfirmRequest("en").putParams("user_name", "bad\rname").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void sendTemplatedEmail_paramContainsNullByte_failsInvalidArgument() throws Exception {
        var req = validConfirmRequest("en").putParams("user_name", "bad\u0000name").build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(templateRenderService, never()).render(anyString(), anyString(), any());
    }

    @Test
    void sendTemplatedEmail_dedupRecipientsAcrossToCcBcc() throws Exception {
        var req = SendTemplatedEmailRequest.newBuilder()
                .addTo("User@Example.COM")
                .addCc("user@example.com")
                .setTemplateId(TemplateCatalog.IDENTITY_EMAIL_CONFIRM)
                .setLocale("en")
                .putParams("action_link", "https://x")
                .putParams("user_name", "u")
                .build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rec.getError()).isNull();
    }

    @Test
    void sendTemplatedEmail_ccBccForwardedToSmtp() throws Exception {
        var req = validConfirmRequest("en")
                .addCc("cc@example.com")
                .addBcc("bcc@example.com")
                .build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rec.getError()).isNull();
        verify(smtpMailSender)
                .send(
                        eq(java.util.List.of("user@example.com")),
                        eq(java.util.List.of("cc@example.com")),
                        eq(java.util.List.of("bcc@example.com")),
                        anyString(),
                        anyString(),
                        anyString(),
                        isNull());
    }

    @Test
    void sendTemplatedEmail_passwordResetTemplate_succeeds() throws Exception {
        var req = SendTemplatedEmailRequest.newBuilder()
                .addTo("a@b.com")
                .setTemplateId(TemplateCatalog.IDENTITY_PASSWORD_RESET)
                .setLocale("sk")
                .putParams("action_link", "https://example.com/reset")
                .putParams("user_name", "Sam")
                .build();
        StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

        impl.sendTemplatedEmail(req, rec);

        assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rec.getError()).isNull();
        verify(templateRenderService).render(eq(TemplateCatalog.IDENTITY_PASSWORD_RESET), eq("sk"), any());
    }

    private static SendTemplatedEmailRequest.Builder validConfirmRequest(String locale) {
        return SendTemplatedEmailRequest.newBuilder()
                .addTo("user@example.com")
                .setTemplateId(TemplateCatalog.IDENTITY_EMAIL_CONFIRM)
                .setLocale(locale)
                .putParams("action_link", "https://example.com/confirm")
                .putParams("user_name", "Pat");
    }
}
