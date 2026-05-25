package manyfaces.mailer.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.testing.StreamRecorder;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import manyfaces.mailer.config.MailerConfig;
import manyfaces.mailer.smtp.SmtpMailSender;
import manyfaces.mailer.smtp.SmtpTransportSettings;
import manyfaces.mailer.template.TemplateCatalog;
import manyfaces.mailer.template.TemplateRenderService;
import manyfaces.mailer.v1.SendTemplatedEmailRequest;
import manyfaces.mailer.v1.SendTemplatedEmailResponse;
import manyfaces.mailer.v1.SmtpTransportConfig;
import manyfaces.mailer.v1.TestSmtpConnectionRequest;
import manyfaces.mailer.v1.TestSmtpConnectionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** AMC-J*: per-request SMTP wire override and TestSmtpConnection RPC. */
@ExtendWith(MockitoExtension.class)
class MailerSmtpOverrideTest {

	private static final TemplateRenderService.RenderedEmail SAMPLE =
			new TemplateRenderService.RenderedEmail("Subject", "<p>h</p>", "h");

	@Mock
	private TemplateRenderService templateRenderService;

	@Mock
	private SmtpMailSender smtpMailSender;

	private MailerConfig envConfig;
	private MailerServiceImpl impl;

	@BeforeEach
	void setUp() throws Exception {
		envConfig = MailerConfig.testingMinimal(Locale.forLanguageTag("en"));
		impl = new MailerServiceImpl(envConfig, templateRenderService, smtpMailSender);
		lenient()
				.when(templateRenderService.render(anyString(), anyString(), any()))
				.thenReturn(SAMPLE);
		lenient()
				.when(smtpMailSender.send(anyList(), anyList(), anyList(), anyString(), anyString(), anyString(), isNull(), any()))
				.thenReturn("smtp-msg-id");
	}

	@Test
	void AMC_J1_wireSmtpOverridesEnvHost() throws Exception {
		var wire = SmtpTransportConfig.newBuilder()
				.setHost("wire-host")
				.setPort(2525)
				.setFromEmail("wire@example.com")
				.build();
		var req = baseRequest().setSmtp(wire).build();
		StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

		impl.sendTemplatedEmail(req, rec);

		assertThat(rec.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
		assertThat(rec.getError()).isNull();
		ArgumentCaptor<SmtpTransportSettings> cap = ArgumentCaptor.forClass(SmtpTransportSettings.class);
		verify(smtpMailSender)
				.send(anyList(), anyList(), anyList(), anyString(), anyString(), anyString(), isNull(), cap.capture());
		assertThat(cap.getValue().host()).isEqualTo("wire-host");
		assertThat(cap.getValue().port()).isEqualTo(2525);
	}

	@Test
	void AMC_J2_missingWireBlockUsesEnvConfig() {
		var req = baseRequest().build();
		var resolved = MailerServiceImpl.resolveTransportForSend(req, envConfig);
		assertThat(resolved.host()).isEqualTo(envConfig.smtpHost());
		assertThat(resolved.port()).isEqualTo(envConfig.smtpPort());
	}

	@Test
	void AMC_J3_incompleteWireSmtpFailsInvalidArgument() {
		var wire = SmtpTransportConfig.newBuilder().setHost("only-host").setPort(1025).build();
		var req = baseRequest().setSmtp(wire).build();
		StreamRecorder<SendTemplatedEmailResponse> rec = StreamRecorder.create();

		impl.sendTemplatedEmail(req, rec);

		assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
	}

	@Test
	void AMC_J4_testSmtpConnection_missingBlockFailsInvalidArgument() {
		StreamRecorder<TestSmtpConnectionResponse> rec = StreamRecorder.create();
		impl.testSmtpConnection(TestSmtpConnectionRequest.newBuilder().build(), rec);
		assertThat(Status.fromThrowable(rec.getError()).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
	}

	private static SendTemplatedEmailRequest.Builder baseRequest() {
		return SendTemplatedEmailRequest.newBuilder()
				.addTo("user@example.com")
				.setTemplateId(TemplateCatalog.IDENTITY_EMAIL_CONFIRM)
				.setLocale("en")
				.putParams("action_link", "https://example.com/confirm")
				.putParams("user_name", "Pat");
	}
}
