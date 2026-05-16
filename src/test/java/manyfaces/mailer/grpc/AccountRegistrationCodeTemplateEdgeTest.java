package manyfaces.mailer.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.testing.StreamRecorder;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import manyfaces.mailer.config.MailerConfig;
import manyfaces.mailer.smtp.SmtpMailSender;
import manyfaces.mailer.template.TemplateCatalog;
import manyfaces.mailer.template.TemplateRenderService;
import manyfaces.mailer.v1.SendTemplatedEmailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountRegistrationCodeTemplateEdgeTest {

    private static final TemplateRenderService.RenderedEmail SAMPLE =
            new TemplateRenderService.RenderedEmail("Subject", "<p>code</p>", "code");

    @Mock
    private TemplateRenderService templateRenderService;

    @Mock
    private SmtpMailSender smtpMailSender;

    private MailerServiceImpl impl;

    @BeforeEach
    void setUp() throws Exception {
        impl = new MailerServiceImpl(
                MailerConfig.testingMinimal(Locale.forLanguageTag("en")), templateRenderService, smtpMailSender);
        lenient().when(templateRenderService.render(any(), any(), any())).thenReturn(SAMPLE);
        lenient().when(smtpMailSender.send(any(), any(), any(), any(), any(), any(), any())).thenReturn("msg-1");
    }

    @Test
    void sendTemplatedEmail_acceptsAccountRegistrationCode() throws Exception {
        var observer = StreamRecorder.<manyfaces.mailer.v1.SendTemplatedEmailResponse>create();
        impl.sendTemplatedEmail(validRequest().build(), observer);
        observer.awaitCompletion(5, TimeUnit.SECONDS);
        assertThat(observer.getError()).isNull();
        verify(templateRenderService).render(eq(TemplateCatalog.ACCOUNT_REGISTRATION_CODE), eq("en"), any());
    }

    @Test
    void sendTemplatedEmail_rejectsMissingRegistrationCode() throws Exception {
        var observer = StreamRecorder.<manyfaces.mailer.v1.SendTemplatedEmailResponse>create();
        impl.sendTemplatedEmail(
                validRequest().clearParams().putParams("action_link", "https://x/register?hash=abc").putParams("user_name", "A").putParams("expiry_minutes", "30").build(),
                observer);
        observer.awaitCompletion(5, TimeUnit.SECONDS);
        assertThat(observer.getError()).isNotNull();
        assertThat(((io.grpc.StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(templateRenderService, never()).render(any(), any(), any());
    }

    @Test
    void sendTemplatedEmail_actionLinkMayContainHashQuery() throws Exception {
        var observer = StreamRecorder.<manyfaces.mailer.v1.SendTemplatedEmailResponse>create();
        impl.sendTemplatedEmail(
                validRequest().putParams("action_link", "https://portal.example/en/register/complete?hash=opaque123").build(),
                observer);
        observer.awaitCompletion(5, TimeUnit.SECONDS);
        assertThat(observer.getError()).isNull();
    }

    private static SendTemplatedEmailRequest.Builder validRequest() {
        return SendTemplatedEmailRequest.newBuilder()
                .setTemplateId(TemplateCatalog.ACCOUNT_REGISTRATION_CODE)
                .setLocale("en")
                .addTo("user@example.com")
                .putParams("action_link", "https://portal.example/en/register/complete?hash=opaque")
                .putParams("registration_code", "ABC234")
                .putParams("user_name", "Demo")
                .putParams("expiry_minutes", "30");
    }
}
